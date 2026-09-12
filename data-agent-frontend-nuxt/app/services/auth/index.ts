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

import axios from 'axios';

export interface AuthUser {
	id: number;
	username: string;
	role: 'ADMIN' | 'USER';
}

export interface Credentials {
	username: string;
	password: string;
}

const API_BASE_URL = '/api/auth';

async function prepareCsrf() {
	await axios.get(`${API_BASE_URL}/csrf`);
}

class AuthService {
	async currentUser(): Promise<AuthUser> {
		const response = await axios.get<AuthUser>(`${API_BASE_URL}/me`);
		return response.data;
	}

	async login(credentials: Credentials): Promise<AuthUser> {
		await prepareCsrf();
		const response = await axios.post<AuthUser>(
			`${API_BASE_URL}/login`,
			credentials,
		);
		return response.data;
	}

	async register(credentials: Credentials): Promise<AuthUser> {
		await prepareCsrf();
		const response = await axios.post<AuthUser>(
			`${API_BASE_URL}/register`,
			credentials,
		);
		return response.data;
	}

	async logout(): Promise<void> {
		await prepareCsrf();
		await axios.post(`${API_BASE_URL}/logout`);
	}
}

export default new AuthService();
