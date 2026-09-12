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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserIsolationMigrationInitializerTest {

	private JdbcTemplate jdbcTemplate;

	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
		jdbcTemplate = new JdbcTemplate(dataSource);
		passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		for (String table : new String[] { "agent", "datasource", "model_config", "user_prompt_config" }) {
			jdbcTemplate.execute("CREATE TABLE " + table + " (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(30))");
			jdbcTemplate.update("INSERT INTO " + table + " (name) VALUES (?)", "legacy");
		}
		jdbcTemplate.execute(
				"CREATE TABLE chat_session (id VARCHAR(64) PRIMARY KEY, user_id BIGINT, title VARCHAR(30))");
		jdbcTemplate.update("INSERT INTO chat_session (id, title) VALUES ('legacy-session', 'legacy')");
	}

	@Test
	void migrate_preservesLegacyDataAndAssignsItToBootstrapAdmin() throws Exception {
		var initializer = new UserIsolationMigrationInitializer(jdbcTemplate, passwordEncoder);

		initializer.migrate();

		Long adminId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username = 'admin'", Long.class);
		String passwordHash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM app_user WHERE username = 'admin'", String.class);
		assertNotNull(adminId);
		assertTrue(passwordEncoder.matches("1234567", passwordHash));
		assertEquals("ADMIN",
				jdbcTemplate.queryForObject("SELECT role FROM app_user WHERE id = ?", String.class, adminId));
		for (String table : new String[] { "agent", "datasource", "model_config", "user_prompt_config" }) {
			assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
			assertEquals(adminId,
					jdbcTemplate.queryForObject("SELECT user_id FROM " + table + " WHERE name = 'legacy'", Long.class));
		}
		assertEquals(adminId,
				jdbcTemplate.queryForObject("SELECT user_id FROM chat_session WHERE id = 'legacy-session'", Long.class));

		initializer.migrate();

		assertEquals(1,
				jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE username = 'admin'", Integer.class));
		assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent", Integer.class));
	}

}
