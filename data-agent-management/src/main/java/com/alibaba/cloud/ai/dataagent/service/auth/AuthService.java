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
package com.alibaba.cloud.ai.dataagent.service.auth;

import com.alibaba.cloud.ai.dataagent.dto.auth.RegisterRequest;
import com.alibaba.cloud.ai.dataagent.entity.AppUser;
import com.alibaba.cloud.ai.dataagent.mapper.AppUserMapper;
import com.alibaba.cloud.ai.dataagent.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final AppUserMapper appUserMapper;

	private final PasswordEncoder passwordEncoder;

	@Transactional(rollbackFor = Exception.class)
	public AuthenticatedUser register(RegisterRequest request) {
		String username = normalize(request.getUsername());
		if (appUserMapper.findByUsername(username) != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
		}
		AppUser user = AppUser.builder()
			.username(username)
			.passwordHash(passwordEncoder.encode(request.getPassword()))
			.role("USER")
			.status("ACTIVE")
			.build();
		try {
			appUserMapper.insert(user);
		}
		catch (DuplicateKeyException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在", ex);
		}
		return principal(user);
	}

	public AuthenticatedUser authenticate(String rawUsername, String password) {
		AppUser user = appUserMapper.findByUsername(normalize(rawUsername));
		if (user == null || !"ACTIVE".equals(user.getStatus())
				|| !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
		}
		return principal(user);
	}

	private AuthenticatedUser principal(AppUser user) {
		return new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
	}

	private String normalize(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

}
