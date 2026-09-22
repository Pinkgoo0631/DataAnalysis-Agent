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

import { describe, expect, it } from 'vitest';
import {
	createBackendProxyRouteRules,
	resolveBackendBaseUrl,
} from './backendProxy';

describe('backend proxy configuration', () => {
	it('defaults local development requests to the available backend port', () => {
		expect(resolveBackendBaseUrl({})).toBe('http://localhost:9065');
		expect(createBackendProxyRouteRules({})).toEqual({
			'/api/**': { proxy: 'http://localhost:9065/api/**' },
			'/nl2sql/**': { proxy: 'http://localhost:9065/nl2sql/**' },
		});
	});

	it('uses the configured backend URL and removes trailing slashes', () => {
		const environment = {
			NUXT_BACKEND_URL: 'http://127.0.0.1:19065/',
		};

		expect(resolveBackendBaseUrl(environment)).toBe(
			'http://127.0.0.1:19065',
		);
	});

	it('falls back to the shared backend server port', () => {
		expect(resolveBackendBaseUrl({ SERVER_PORT: '18065' })).toBe(
			'http://localhost:18065',
		);
	});
});
