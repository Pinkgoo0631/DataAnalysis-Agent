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

import com.alibaba.cloud.ai.dataagent.controller.AuthController;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.security.AgentApiKeyReactiveAuthenticationManager;
import com.alibaba.cloud.ai.dataagent.security.AgentApiKeyServerAuthenticationConverter;
import com.alibaba.cloud.ai.dataagent.security.ApiKeyCredentialService;
import com.alibaba.cloud.ai.dataagent.security.AuthenticatedUser;
import com.alibaba.cloud.ai.dataagent.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthSessionIntegrationTest {

	@Test
	void csrfLoginAndMe_preservesAuthenticatedSessionWithoutBasicChallenge() {
		WebFluxSecurityConfiguration configuration = new WebFluxSecurityConfiguration();
		AgentMapper agentMapper = mock(AgentMapper.class);
		ApiKeyCredentialService credentialService = new ApiKeyCredentialService(configuration.apiKeyPasswordEncoder());
		var authenticationManager = new AgentApiKeyReactiveAuthenticationManager(agentMapper, credentialService);
		var securityContextRepository = configuration.securityContextRepository();
		var securityChain = configuration.agentApiSecurityWebFilterChain(
				org.springframework.security.config.web.server.ServerHttpSecurity.http(), authenticationManager,
				new AgentApiKeyServerAuthenticationConverter(), securityContextRepository);

		AuthService authService = mock(AuthService.class);
		when(authService.authenticate("admin", "1234567"))
			.thenReturn(new AuthenticatedUser(1L, "admin", "ADMIN"));
		WebTestClient client = WebTestClient.bindToController(new AuthController(authService, securityContextRepository))
			.webFilter(new WebFilterChainProxy(securityChain))
			.build();

		var csrfResult = client.get()
			.uri("/api/auth/csrf")
			.exchange()
			.expectStatus()
			.isOk()
			.expectCookie()
			.exists("XSRF-TOKEN")
			.expectBody()
			.returnResult();
		ResponseCookie csrfCookie = csrfResult.getResponseCookies().getFirst("XSRF-TOKEN");

		var loginResult = client.post()
			.uri("/api/auth/login")
			.cookie("XSRF-TOKEN", csrfCookie.getValue())
			.header("X-XSRF-TOKEN", csrfCookie.getValue())
			.bodyValue(Map.of("username", "admin", "password", "1234567"))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.username")
			.isEqualTo("admin")
			.returnResult();
		ResponseCookie sessionCookie = loginResult.getResponseCookies().getFirst("SESSION");

		client.get()
			.uri("/api/auth/me")
			.cookie("SESSION", sessionCookie.getValue())
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
			.expectBody()
			.jsonPath("$.username")
			.isEqualTo("admin")
			.jsonPath("$.role")
			.isEqualTo("ADMIN");
	}

}
