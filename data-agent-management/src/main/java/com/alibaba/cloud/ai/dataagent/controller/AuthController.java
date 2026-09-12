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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.auth.LoginRequest;
import com.alibaba.cloud.ai.dataagent.dto.auth.RegisterRequest;
import com.alibaba.cloud.ai.dataagent.security.AuthenticatedUser;
import com.alibaba.cloud.ai.dataagent.service.auth.AuthService;
import com.alibaba.cloud.ai.dataagent.vo.AuthUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final ServerSecurityContextRepository securityContextRepository;

	@GetMapping("/csrf")
	public Mono<CsrfToken> csrf(ServerWebExchange exchange) {
		Object attribute = exchange.getAttribute(CsrfToken.class.getName());
		if (attribute instanceof Mono<?> token) {
			return token.cast(CsrfToken.class);
		}
		if (attribute instanceof CsrfToken token) {
			return Mono.just(token);
		}
		return Mono.error(new IllegalStateException("CSRF token is unavailable"));
	}

	@PostMapping("/register")
	public Mono<ResponseEntity<AuthUserResponse>> register(@Valid @RequestBody RegisterRequest request) {
		return Mono.fromCallable(() -> ResponseEntity.ok(AuthUserResponse.from(authService.register(request))))
			.subscribeOn(Schedulers.boundedElastic());
	}

	@PostMapping("/login")
	public Mono<ResponseEntity<AuthUserResponse>> login(@Valid @RequestBody LoginRequest request,
			ServerWebExchange exchange) {
		return Mono.fromCallable(() -> authService.authenticate(request.getUsername(), request.getPassword()))
			.subscribeOn(Schedulers.boundedElastic())
			.flatMap(user -> {
				var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null,
						AuthorityUtils.createAuthorityList("ROLE_" + user.role()));
				return exchange.getSession()
					.flatMap(session -> session.changeSessionId())
					.then(securityContextRepository.save(exchange, new SecurityContextImpl(authentication)))
					.thenReturn(ResponseEntity.ok(AuthUserResponse.from(user)));
			});
	}

	@GetMapping("/me")
	public ResponseEntity<AuthUserResponse> me(org.springframework.security.core.Authentication authentication) {
		AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
		return ResponseEntity.ok(AuthUserResponse.from(user));
	}

	@PostMapping("/logout")
	public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
		return exchange.getSession().flatMap(session -> session.invalidate().thenReturn(ResponseEntity.noContent().build()));
	}

}
