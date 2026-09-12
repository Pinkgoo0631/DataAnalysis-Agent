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
package com.alibaba.cloud.ai.dataagent.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class UploadAuthorizationWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();
		if (!path.startsWith("/uploads/")) {
			return chain.filter(exchange);
		}
		return ReactiveSecurityContextHolder.getContext().flatMap(context -> {
			if (!(context.getAuthentication().getPrincipal() instanceof AuthenticatedUser user)) {
				return notFound(exchange);
			}
			String tenantSegment = "/users/" + user.id() + "/";
			if (path.contains(tenantSegment) || ("ADMIN".equals(user.role()) && !path.contains("/users/"))) {
				return chain.filter(exchange);
			}
			return notFound(exchange);
		}).switchIfEmpty(notFound(exchange));
	}

	private Mono<Void> notFound(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
		return exchange.getResponse().setComplete();
	}

}
