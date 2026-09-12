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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private AppUserMapper appUserMapper;

	private PasswordEncoder passwordEncoder;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		authService = new AuthService(appUserMapper, passwordEncoder);
	}

	@Test
	void register_createsIsolatedActiveUserWithHashedPassword() {
		RegisterRequest request = new RegisterRequest();
		request.setUsername(" New.User ");
		request.setPassword("password-123");
		doAnswer(invocation -> {
			AppUser user = invocation.getArgument(0);
			user.setId(7L);
			return 1;
		}).when(appUserMapper).insert(any(AppUser.class));

		var result = authService.register(request);

		assertEquals(7L, result.id());
		assertEquals("new.user", result.username());
		assertEquals("USER", result.role());
		var captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
		verify(appUserMapper).insert(captor.capture());
		assertEquals("ACTIVE", captor.getValue().getStatus());
		assertNotEquals(request.getPassword(), captor.getValue().getPasswordHash());
		assertTrue(passwordEncoder.matches(request.getPassword(), captor.getValue().getPasswordHash()));
	}

	@Test
	void authenticate_validCredentials_returnsPrincipal() {
		when(appUserMapper.findByUsername("alice"))
			.thenReturn(AppUser.builder()
				.id(9L)
				.username("alice")
				.passwordHash(passwordEncoder.encode("password-123"))
				.role("USER")
				.status("ACTIVE")
				.build());

		var result = authService.authenticate("Alice", "password-123");

		assertEquals(9L, result.id());
		assertEquals("alice", result.username());
	}

	@Test
	void authenticate_wrongPassword_isUnauthorized() {
		when(appUserMapper.findByUsername("alice"))
			.thenReturn(AppUser.builder()
				.id(9L)
				.username("alice")
				.passwordHash(passwordEncoder.encode("password-123"))
				.role("USER")
				.status("ACTIVE")
				.build());

		assertThrows(ResponseStatusException.class, () -> authService.authenticate("alice", "wrong-password"));
	}

}
