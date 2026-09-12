/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserIsolationMigrationInitializer {

	private static final String VERSION = "user-isolation-v1";

	private final JdbcTemplate jdbcTemplate;

	private final PasswordEncoder passwordEncoder;

	@EventListener(ApplicationReadyEvent.class)
	public synchronized void migrate() throws SQLException {
		createInfrastructure();
		if (migrationApplied()) {
			ensureAdminExists();
			return;
		}

		Long adminId = ensureAdminExists();
		for (String table : new String[] { "agent", "datasource", "model_config", "user_prompt_config" }) {
			ensureColumn(table, "user_id", "BIGINT");
		}
		ensureIndex("agent", "idx_agent_user_id", "user_id");
		ensureIndex("datasource", "idx_datasource_user_id", "user_id");
		ensureIndex("model_config", "idx_model_config_user_id", "user_id");
		ensureIndex("user_prompt_config", "idx_user_prompt_config_user_id", "user_id");
		jdbcTemplate.update("UPDATE agent SET user_id = ?", adminId);
		jdbcTemplate.update("UPDATE datasource SET user_id = ?", adminId);
		jdbcTemplate.update("UPDATE model_config SET user_id = ?", adminId);
		jdbcTemplate.update("UPDATE user_prompt_config SET user_id = ?", adminId);
		jdbcTemplate.update("UPDATE chat_session SET user_id = ?", adminId);
		jdbcTemplate.update("INSERT INTO data_agent_schema_migration (version, applied_time) VALUES (?, CURRENT_TIMESTAMP)",
				VERSION);
		log.info("User isolation migration completed; legacy DataAgent records now belong to admin user {}", adminId);
	}

	private void createInfrastructure() {
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS app_user (
				  id BIGINT AUTO_INCREMENT PRIMARY KEY,
				  username VARCHAR(64) NOT NULL UNIQUE,
				  password_hash VARCHAR(255) NOT NULL,
				  role VARCHAR(20) NOT NULL DEFAULT 'USER',
				  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
				  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
				  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
				)
				""");
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS data_agent_schema_migration (
				  version VARCHAR(100) PRIMARY KEY,
				  applied_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
				)
				""");
	}

	private boolean migrationApplied() {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM data_agent_schema_migration WHERE version = ?", Integer.class, VERSION);
		return count != null && count > 0;
	}

	private Long ensureAdminExists() {
		try {
			return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE LOWER(username) = 'admin'", Long.class);
		}
		catch (DataAccessException notFound) {
			try {
				jdbcTemplate.update("""
						INSERT INTO app_user (username, password_hash, role, status, create_time, update_time)
						VALUES ('admin', ?, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
						""", passwordEncoder.encode("1234567"));
			}
			catch (DataAccessException concurrentInsert) {
				log.debug("Admin user was concurrently created", concurrentInsert);
			}
			return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE LOWER(username) = 'admin'", Long.class);
		}
	}

	private void ensureColumn(String table, String column, String type) throws SQLException {
		if (columnExists(table, column)) {
			return;
		}
		jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
	}

	private boolean columnExists(String table, String column) throws SQLException {
		return jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
			DatabaseMetaData metadata = connection.getMetaData();
			try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, column)) {
				if (columns.next()) {
					return true;
				}
			}
			try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table.toUpperCase(),
					column.toUpperCase())) {
				return columns.next();
			}
		});
	}

	private void ensureIndex(String table, String index, String column) throws SQLException {
		Boolean exists = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
			DatabaseMetaData metadata = connection.getMetaData();
			try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
				while (indexes.next()) {
					if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
				}
			}
			try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, table.toUpperCase(), false,
					false)) {
				while (indexes.next()) {
					if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
				}
			}
			return false;
		});
		if (!Boolean.TRUE.equals(exists)) {
			jdbcTemplate.execute("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
		}
	}

}
