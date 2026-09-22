/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<div class="chat-page">
		<ChatSidebar />
		<main class="analysis-workspace">
			<section class="analysis-document">
				<header class="document-header">
					<div class="document-number instrument-code">DA / NOTEBOOK</div>
					<div class="document-heading">
						<div>
							<p class="document-kicker instrument-code">ACTIVE ANALYSIS</p>
							<h1>{{ store.currentSession?.title || '新建分析文档' }}</h1>
						</div>
						<div class="document-agent">
							<span>执行智能体</span>
							<strong>{{ store.currentAgentName || 'DataAgent' }}</strong>
						</div>
					</div>
				</header>
				<ChatMessageList />
				<div class="command-dock">
					<div class="command-dock__label instrument-code">
						<span>COMMAND INPUT</span>
						<span>⌘ ENTER TO RUN</span>
					</div>
					<ChatInputArea />
				</div>
			</section>
			<ChatContextPanel />
		</main>
	</div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, watch, computed } from 'vue';
import { useChatStore } from '~/stores/chat';
import agentService from '~/services/agent/index';
import ChatSidebar from '~/components/chat/ChatSidebar.vue';
import ChatMessageList from '~/components/chat/ChatMessageList.vue';
import ChatInputArea from '~/components/chat/ChatInputArea.vue';
import ChatContextPanel from '~/components/chat/ChatContextPanel.vue';

const route = useRoute();
const store = useChatStore();

const currentAgentId = computed(() => {
	const q = route.query.agentId;
	return q ? Number(q) : undefined;
});

async function init(agentId: number) {
	store.currentAgentId = agentId;

	// Load agent info
	try {
		const agent = await agentService.get(agentId);
		if (agent) {
			store.currentAgentName = agent.name || '';
			store.currentAgentAvatar = agent.avatar || '';
			store.currentAgentDescription = agent.description || '';
		}
	} catch {
		/* ignore */
	}

	// Load active model — handled by store.loadSessions

	store.connectSessionStream(agentId);
	await store.loadSessions(agentId);
}

onMounted(async () => {
	if (currentAgentId.value) await init(currentAgentId.value);
});

watch(currentAgentId, async (newId, oldId) => {
	if (newId && newId !== oldId) {
		store.sessions = [];
		store.currentSession = null;
		store.currentMessages = [];
		store.isStreaming = false;
		store.nodeBlocks = [];
		await init(newId);
	}
});

onUnmounted(() => {
	store.disconnectSessionStream();
});
</script>

<style scoped>
.chat-page {
	display: flex;
	height: calc(100vh - 64px);
	overflow: hidden;
	background: #dfe4e5;
}

.analysis-workspace {
	flex: 1;
	display: flex;
	overflow: hidden;
	min-width: 0;
	border-left: 1px solid var(--da-line);
}

.analysis-document {
	position: relative;
	display: flex;
	flex: 1;
	flex-direction: column;
	min-width: 0;
	overflow: hidden;
	background: #fbfcfc;
}

.analysis-document::after {
	position: absolute;
	z-index: 4;
	top: 0;
	left: 0;
	width: 100%;
	height: 1px;
	pointer-events: none;
	background: linear-gradient(
		90deg,
		transparent,
		var(--da-signal-500),
		transparent
	);
	content: '';
	opacity: 0;
	animation: workspace-scan 0.85s ease-out 0.12s both;
}

@keyframes workspace-scan {
	0% {
		opacity: 0;
		transform: translateY(0);
	}
	18% {
		opacity: 0.75;
	}
	100% {
		opacity: 0;
		transform: translateY(220px);
	}
}

.document-header {
	display: grid;
	grid-template-columns: 92px 1fr;
	min-height: 102px;
	border-bottom: 1px solid var(--da-line);
	background: #f7f9f9;
}

.document-number {
	display: flex;
	align-items: flex-end;
	padding: 0 14px 17px;
	border-right: 1px solid var(--da-line);
	font-size: 8px;
	letter-spacing: 0.08em;
	color: #849096;
	writing-mode: vertical-rl;
	transform: rotate(180deg);
}

.document-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 24px;
	padding: 18px 28px;
}

.document-kicker {
	margin: 0 0 5px;
	font-size: 8px;
	letter-spacing: 0.13em;
	color: var(--da-signal-600);
}

.document-heading h1 {
	max-width: 680px;
	overflow: hidden;
	font-family: Georgia, 'Noto Serif SC', serif;
	font-size: clamp(21px, 2vw, 28px);
	font-weight: 500;
	line-height: 1.2;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.document-agent {
	min-width: 150px;
	padding-left: 16px;
	border-left: 1px solid var(--da-line);
}

.document-agent span,
.document-agent strong {
	display: block;
}

.document-agent span {
	font-size: 9px;
	color: #849096;
}

.document-agent strong {
	margin-top: 3px;
	font-size: 11px;
	font-weight: 700;
}

.command-dock {
	z-index: 2;
	border-top: 1px solid #bcc6ca;
	background: #eef2f2;
	box-shadow: 0 -8px 24px rgba(38, 46, 50, 0.06);
}

.command-dock__label {
	display: flex;
	justify-content: space-between;
	padding: 7px 24px 0;
	font-size: 8px;
	letter-spacing: 0.1em;
	color: #7e8a90;
}

@media (max-width: 768px) {
	.document-header {
		grid-template-columns: 52px 1fr;
		min-height: 84px;
	}

	.document-heading {
		padding: 14px 16px;
	}

	.document-heading h1 {
		font-size: 18px;
	}

	.document-agent {
		display: none;
	}

	.command-dock__label {
		padding-inline: 12px;
	}
}
</style>
