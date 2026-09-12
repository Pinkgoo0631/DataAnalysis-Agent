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
				<v-icon icon="mdi-account-plus" color="white" size="26" />
			</v-avatar>
			<div>
				<div class="text-h6 font-weight-bold">创建账户</div>
				<div class="text-body-2 text-medium-emphasis">新账户从空白隔离环境开始</div>
			</div>
		</div>

		<v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4">
			{{ errorMessage }}
		</v-alert>

		<v-form @submit.prevent="submit">
			<v-text-field
				v-model.trim="form.username"
				label="用户名"
				hint="3-64 位，可使用字母、数字、下划线、点和短横线"
				persistent-hint
				prepend-inner-icon="mdi-account-outline"
				variant="outlined"
				autocomplete="username"
				:disabled="loading"
				class="mb-2"
			/>
			<v-text-field
				v-model="form.password"
				label="密码"
				hint="至少 8 位"
				persistent-hint
				prepend-inner-icon="mdi-lock-outline"
				:type="showPassword ? 'text' : 'password'"
				:append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
				variant="outlined"
				autocomplete="new-password"
				:disabled="loading"
				class="mb-2"
				@click:append-inner="showPassword = !showPassword"
			/>
			<v-text-field
				v-model="confirmPassword"
				label="确认密码"
				prepend-inner-icon="mdi-lock-check-outline"
				:type="showPassword ? 'text' : 'password'"
				variant="outlined"
				autocomplete="new-password"
				:disabled="loading"
			/>
			<v-btn
				type="submit"
				color="primary"
				variant="flat"
				block
				size="large"
				:loading="loading"
				:disabled="!canSubmit"
			>
				注册
			</v-btn>
		</v-form>

		<div class="text-center text-body-2 mt-6">
			已有账号？
			<NuxtLink to="/login" class="text-primary font-weight-bold">返回登录</NuxtLink>
		</div>
	</v-card>
</template>

<script setup lang="ts">
import axios from 'axios';
import { useAuthStore } from '~/stores/auth';

definePageMeta({ layout: 'auth' });

const authStore = useAuthStore();
const loading = ref(false);
const showPassword = ref(false);
const confirmPassword = ref('');
const errorMessage = ref('');
const form = reactive({ username: '', password: '' });
const canSubmit = computed(
	() =>
		form.username.length >= 3 &&
		form.password.length >= 8 &&
		form.password === confirmPassword.value,
);

function resolveError(error: unknown) {
	if (axios.isAxiosError(error)) {
		return error.response?.data?.message || error.response?.data?.detail || '注册失败，请检查输入';
	}
	return '注册失败，请稍后重试';
}

async function submit() {
	if (form.password !== confirmPassword.value) {
		errorMessage.value = '两次输入的密码不一致';
		return;
	}
	loading.value = true;
	errorMessage.value = '';
	try {
		await authStore.register(form);
		await navigateTo({ path: '/login', query: { registered: '1' } });
	} catch (error) {
		errorMessage.value = resolveError(error);
	} finally {
		loading.value = false;
	}
}
</script>
