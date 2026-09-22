/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

type Environment = Record<string, string | undefined>;

const DEFAULT_BACKEND_PORT = '9065';

export function resolveBackendBaseUrl(
	environment: Environment = process.env,
): string {
	const configuredUrl = environment.NUXT_BACKEND_URL?.trim();
	if (configuredUrl) {
		return configuredUrl.replace(/\/+$/, '');
	}

	const backendPort = environment.SERVER_PORT?.trim() || DEFAULT_BACKEND_PORT;
	return `http://localhost:${backendPort}`;
}

export function createBackendProxyRouteRules(
	environment: Environment = process.env,
) {
	const backendBaseUrl = resolveBackendBaseUrl(environment);
	return {
		'/api/**': { proxy: `${backendBaseUrl}/api/**` },
		'/nl2sql/**': { proxy: `${backendBaseUrl}/nl2sql/**` },
	};
}
