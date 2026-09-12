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
	<v-app>
		<v-main class="auth-background">
			<div class="auth-shell">
				<slot />
			</div>
		</v-main>
	</v-app>
</template>

<style scoped>
.auth-background {
	background:
		radial-gradient(circle at 15% 15%, rgba(59, 130, 246, 0.22), transparent 34%),
		radial-gradient(circle at 85% 80%, rgba(14, 165, 233, 0.18), transparent 32%),
		linear-gradient(145deg, #0f172a 0%, #172554 48%, #0f172a 100%);
}

.auth-shell {
	display: flex;
	min-height: 100vh;
	align-items: center;
	justify-content: center;
	padding: 24px;
}
</style>
