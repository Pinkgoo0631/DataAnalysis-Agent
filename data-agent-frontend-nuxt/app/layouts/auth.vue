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
				<section class="auth-intro" aria-label="DataAgent 产品介绍">
					<div class="auth-intro__rail" aria-hidden="true">
						<span v-for="tick in 24" :key="tick" />
					</div>
					<div class="auth-intro__brand">
						<BrandMark :size="38" />
						<span>DataAgent</span>
					</div>
					<div class="auth-intro__copy">
						<p class="auth-intro__status instrument-code">
							<span /> 分析系统就绪
						</p>
						<h1>把数据变成<br />可执行的判断。</h1>
						<p>连接数据源、描述问题，由智能体完成查询、验证和报告。</p>
					</div>
					<div class="auth-intro__modules instrument-code">
						<span>SQL / QUERY</span>
						<span>CHART / REPORT</span>
						<span>AGENT / TRACE</span>
					</div>
				</section>
				<section class="auth-form-panel">
					<slot />
				</section>
			</div>
		</v-main>
	</v-app>
</template>

<style scoped>
.auth-background {
	background-color: var(--da-canvas);
	background-image:
		linear-gradient(rgba(71, 123, 145, 0.06) 1px, transparent 1px),
		linear-gradient(90deg, rgba(71, 123, 145, 0.06) 1px, transparent 1px);
	background-size: 32px 32px;
}

.auth-shell {
	display: grid;
	grid-template-columns: minmax(360px, 0.9fr) minmax(440px, 1.1fr);
	min-height: 100vh;
	max-width: 1320px;
	margin: 0 auto;
	padding: 48px;
}

.auth-intro {
	position: relative;
	display: flex;
	flex-direction: column;
	justify-content: space-between;
	min-height: calc(100vh - 96px);
	padding: 40px;
	overflow: hidden;
	color: #edf1f2;
	background: var(--da-graphite-900);
	border: 1px solid var(--da-graphite-950);
}

.auth-intro::after {
	position: absolute;
	right: -90px;
	bottom: 100px;
	width: 280px;
	height: 280px;
	border: 1px solid rgba(71, 123, 145, 0.35);
	border-radius: 50%;
	box-shadow:
		0 0 0 48px rgba(71, 123, 145, 0.06),
		0 0 0 96px rgba(71, 123, 145, 0.035);
	content: '';
}

.auth-intro__rail {
	position: absolute;
	top: 0;
	left: 0;
	right: 0;
	height: 8px;
	display: grid;
	grid-template-columns: repeat(24, 1fr);
	gap: 7px;
	padding: 0 24px;
	background: var(--da-graphite-950);
}

.auth-intro__rail span {
	width: 1px;
	height: 5px;
	background: #5c676d;
}

.auth-intro__rail span:nth-child(6n + 1) {
	height: 8px;
	background: var(--da-signal-500);
}

.auth-intro__brand {
	display: flex;
	align-items: center;
	gap: 10px;
	font-size: 16px;
	font-weight: 750;
}

.auth-intro__copy {
	position: relative;
	z-index: 1;
	max-width: 480px;
}

.auth-intro__status {
	display: flex;
	align-items: center;
	gap: 8px;
	margin-bottom: 22px;
	font-size: 10px;
	color: #aeb8bd;
}

.auth-intro__status span {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: #4bb28f;
}

.auth-intro h1 {
	margin: 0;
	font-size: clamp(34px, 4.6vw, 60px);
	font-weight: 720;
	line-height: 1.12;
	letter-spacing: -0.045em;
}

.auth-intro__copy > p:last-child {
	max-width: 410px;
	margin: 24px 0 0;
	font-size: 14px;
	line-height: 1.8;
	color: #aeb8bd;
}

.auth-intro__modules {
	display: flex;
	flex-wrap: wrap;
	gap: 8px 22px;
	font-size: 9px;
	color: #89969c;
}

.auth-form-panel {
	display: flex;
	align-items: center;
	justify-content: center;
	min-width: 0;
	padding: 48px;
	background: rgba(248, 250, 250, 0.9);
	border: 1px solid var(--da-line);
	border-left: 0;
}

@media (max-width: 900px) {
	.auth-shell {
		grid-template-columns: 1fr;
		padding: 20px;
	}

	.auth-intro {
		min-height: auto;
		padding: 28px;
	}

	.auth-intro__copy {
		margin: 70px 0;
	}

	.auth-form-panel {
		padding: 36px 24px;
		border-top: 0;
		border-left: 1px solid var(--da-line);
	}
}

@media (max-width: 600px) {
	.auth-shell {
		padding: 0;
	}

	.auth-intro {
		display: none;
	}

	.auth-form-panel {
		width: 100%;
		min-height: 100vh;
		box-sizing: border-box;
		padding: 24px 16px;
		border: 0;
	}
}
</style>
