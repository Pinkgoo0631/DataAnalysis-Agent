/*
 * Copyright 2026 the original author or authors.
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

import axios from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import agentDatasourceService from './agentDatasource';

vi.mock('axios', () => ({
	default: {
		get: vi.fn(),
		post: vi.fn(),
	},
}));

vi.mock('ofetch', () => ({
	$fetch: vi.fn().mockResolvedValue({ success: true }),
}));

describe('agentDatasourceService', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('uses the CSRF-configured axios client when updating selected tables', async () => {
		vi.mocked(axios.post).mockResolvedValue({
			data: { success: true, message: '更新成功' },
		});

		const result = await agentDatasourceService.updateDatasourceTables('7', {
			datasourceId: 1,
			tables: ['orders'],
		});

		expect(axios.post).toHaveBeenCalledWith('/api/agent/7/datasources/tables', {
			datasourceId: 1,
			tables: ['orders'],
		});
		expect(result.success).toBe(true);
	});
});
