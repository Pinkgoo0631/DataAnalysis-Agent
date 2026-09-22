/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<div class="welcome-wrap">
		<div class="welcome-status instrument-code">
			<span class="welcome-status__dot" />
			分析终端已就绪
		</div>

		<div class="agent-avatar-wrap" aria-hidden="true">
			<v-avatar
				:image="store.currentAgentAvatar || undefined"
				:color="store.currentAgentAvatar ? undefined : 'grey-lighten-3'"
				size="80"
				rounded="sm"
				class="agent-avatar"
			>
				<span v-if="!store.currentAgentAvatar" class="agent-avatar-emoji"
					>🤖</span
				>
			</v-avatar>
		</div>

		<h2 class="welcome-title">
			<span class="agent-name">{{ store.currentAgentName || '数据助手' }}</span>
		</h2>

		<!-- Agent Description -->
		<p class="welcome-desc">
			{{
				store.currentAgentDescription ||
				'我可以为您分析数据库中的表结构、生成 SQL 或可视化图表。'
			}}
		</p>

		<div class="capability-grid" aria-label="可用分析能力">
			<div class="capability-item">
				<v-icon icon="mdi-database-search-outline" size="18" />
				<span>查询数据</span>
				<small class="instrument-code">SQL</small>
			</div>
			<div class="capability-item">
				<v-icon icon="mdi-chart-box-outline" size="18" />
				<span>识别趋势</span>
				<small class="instrument-code">CHART</small>
			</div>
			<div class="capability-item">
				<v-icon icon="mdi-file-chart-outline" size="18" />
				<span>生成报告</span>
				<small class="instrument-code">REPORT</small>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { useChatStore } from '~/stores/chat';
const store = useChatStore();
</script>

<style scoped>
.welcome-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	flex: 1;
	padding: 48px 24px;
	text-align: center;
}

.welcome-status {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	margin-bottom: 18px;
	font-size: 10px;
	color: var(--da-text-muted);
}

.welcome-status__dot {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--da-success);
	box-shadow: 0 0 0 3px rgba(38, 135, 104, 0.12);
}

.agent-avatar-wrap {
	margin-bottom: 18px;
}

.agent-avatar {
	border: 1px solid var(--da-line-strong);
	box-shadow: 7px 7px 0 var(--da-steel-100);
}

.agent-avatar-emoji {
	font-size: 36px;
	line-height: 1;
}

.welcome-title {
	font-size: 24px;
	font-weight: 700;
	color: var(--da-graphite-900);
	margin-bottom: 10px;
	letter-spacing: -0.02em;
}

.agent-name {
	color: var(--da-graphite-900);
}

.welcome-desc {
	font-size: 14.5px;
	color: var(--da-text-muted);
	max-width: 480px;
	line-height: 1.7;
}

.capability-grid {
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	width: min(560px, 100%);
	margin-top: 30px;
	border: 1px solid var(--da-line);
	background: rgba(255, 255, 255, 0.78);
}

.capability-item {
	display: grid;
	grid-template-columns: auto 1fr;
	align-items: center;
	gap: 4px 10px;
	min-width: 0;
	padding: 14px 16px;
	text-align: left;
	color: var(--da-steel-600);
}

.capability-item + .capability-item {
	border-left: 1px solid var(--da-line);
}

.capability-item span {
	font-size: 13px;
	font-weight: 650;
	color: var(--da-text);
}

.capability-item small {
	grid-column: 2;
	font-size: 9px;
	color: #899399;
}

@media (max-width: 600px) {
	.capability-grid {
		grid-template-columns: 1fr;
		max-width: 280px;
	}

	.capability-item + .capability-item {
		border-left: 0;
		border-top: 1px solid var(--da-line);
	}
}
</style>
