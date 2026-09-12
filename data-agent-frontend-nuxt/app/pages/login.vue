<!--
 Copyright 2024-2026 the original author or authors.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<template>
	<v-card width="440" max-width="100%" class="pa-4 pa-sm-7 rounded-xl" elevation="18">
		<div class="d-flex align-center mb-6">
			<v-avatar color="primary" size="48" class="mr-4 rounded-lg">
				<v-icon icon="mdi-robot" color="white" size="28" />
			</v-avatar>
			<div>
				<div class="text-h6 font-weight-bold">登录 Data Agent</div>
				<div class="text-body-2 text-medium-emphasis">进入你的独立工作空间</div>
			</div>
		</div>

		<v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4">
			{{ errorMessage }}
		</v-alert>

		<v-form @submit.prevent="submit">
			<v-text-field
				v-model.trim="form.username"
				label="用户名"
				prepend-inner-icon="mdi-account-outline"
				variant="outlined"
				autocomplete="username"
				:disabled="loading"
				class="mb-2"
			/>
			<v-text-field
				v-model="form.password"
				label="密码"
				prepend-inner-icon="mdi-lock-outline"
				:type="showPassword ? 'text' : 'password'"
				:append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
				variant="outlined"
				autocomplete="current-password"
				:disabled="loading"
				@click:append-inner="showPassword = !showPassword"
			/>
			<v-btn
				type="submit"
				color="primary"
				variant="flat"
				block
				size="large"
				:loading="loading"
				:disabled="!form.username || !form.password"
			>
				登录
			</v-btn>
		</v-form>

		<div class="text-center text-body-2 mt-6">
			还没有账号？
			<NuxtLink to="/register" class="text-primary font-weight-bold">立即注册</NuxtLink>
		</div>
	</v-card>
</template>

<script setup lang="ts">
import axios from 'axios';
import { useAuthStore } from '~/stores/auth';

definePageMeta({ layout: 'auth' });

const authStore = useAuthStore();
const route = useRoute();
const loading = ref(false);
const showPassword = ref(false);
const errorMessage = ref(
	route.query.registered === '1' ? '注册成功，请使用新账户登录。' : '',
);
const form = reactive({ username: '', password: '' });

function resolveError(error: unknown) {
	if (axios.isAxiosError(error)) {
		return error.response?.data?.message || error.response?.data?.detail || '用户名或密码错误';
	}
	return '登录失败，请稍后重试';
}

async function submit() {
	loading.value = true;
	errorMessage.value = '';
	try {
		await authStore.login(form);
		const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/agent/new';
		await navigateTo(redirect);
	} catch (error) {
		errorMessage.value = resolveError(error);
	} finally {
		loading.value = false;
	}
}
</script>
