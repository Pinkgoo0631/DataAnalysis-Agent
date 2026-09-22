/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<div ref="listRef" class="message-list custom-scrollbar">
		<!-- Welcome state when no session -->
		<ChatWelcome v-if="!store.currentSession" />

		<!-- Messages -->
		<template v-else>
			<div class="messages-inner">
				<template v-for="message in filteredMessages" :key="message.id">
					<div class="message-wrapper">
						<!-- ── User message ─────────────────────────────────── -->
						<div v-if="message.role === 'user'" class="row user-row">
							<v-card class="user-card" elevation="1">
								<span
									v-html="escapeHtml(message.content).replace(/\n/g, '<br>')"
								/>
							</v-card>
							<v-avatar
								color="grey-darken-2"
								size="34"
								rounded="lg"
								class="avatar"
							>
								<v-icon size="18" color="white">mdi-account</v-icon>
							</v-avatar>
						</div>

						<!-- ── AI messages ──────────────────────────────────── -->
						<div v-else class="row ai-row">
							<v-avatar
								color="blue-darken-3"
								size="34"
								rounded="lg"
								class="avatar"
							>
								<v-icon size="18" color="white">mdi-robot</v-icon>
							</v-avatar>

							<!-- HTML node message -->
							<v-card
								v-if="message.messageType === 'html'"
								class="ai-card"
								elevation="1"
							>
								<div class="md-body" v-html="sanitizeHtml(message.content)" />
							</v-card>

							<!-- Result Set -->
							<v-card
								v-else-if="message.messageType === 'result-set'"
								class="ai-card"
								elevation="1"
							>
								<ChatResultSet
									:data="safeParseJson(message.content)"
									:page-size="store.requestOptions.pageSize"
								/>
							</v-card>

							<!-- Markdown Report -->
							<v-card
								v-else-if="message.messageType === 'markdown-report'"
								class="ai-card report-card"
								elevation="1"
							>
								<ChatMarkdownReport :content="message.content" />
							</v-card>

							<!-- Timeline -->
							<v-card
								v-else-if="message.messageType === 'timeline'"
								class="ai-card timeline-card"
								elevation="1"
							>
								<ChatWorkflowTimeline
									:node-blocks="safeParseBlocks(message.content)"
									:completed="true"
								/>
							</v-card>

							<!-- Warning (user stopped) -->
							<div
								v-else-if="message.messageType === 'warning'"
								class="status-banner status-banner--warning"
							>
								<v-icon size="16" class="mr-2">mdi-alert</v-icon>
								{{ message.content }}
							</div>

							<!-- Error -->
							<div
								v-else-if="message.messageType === 'error'"
								class="status-banner status-banner--error"
							>
								<v-icon size="16" class="mr-2">mdi-alert-circle</v-icon>
								{{ message.content }}
							</div>

							<!-- Plain AI text (render as markdown) -->
							<v-card v-else class="ai-card" elevation="1">
								<div class="md-body" v-html="renderMarkdown(message.content)" />
							</v-card>
						</div>
					</div>

					<!-- ── Report card below completed timeline ────────── -->
					<div
						v-if="
							message.messageType === 'timeline' &&
							extractReportContent(message.content)
						"
						class="message-wrapper"
					>
						<div class="row ai-row">
							<v-avatar
								color="blue-darken-3"
								size="34"
								rounded="lg"
								class="avatar"
								style="visibility: hidden"
							/>
							<v-card class="ai-card report-card" elevation="1">
								<ChatMarkdownReport
									:content="extractReportContent(message.content)!"
								/>
							</v-card>
						</div>
					</div>
				</template>

				<!-- ── Streaming: Workflow Timeline ──────────────────── -->
				<div
					v-if="store.isStreaming && store.nodeBlocks.length > 0"
					class="row ai-row"
				>
					<v-avatar color="blue-darken-3" size="34" rounded="lg" class="avatar">
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card timeline-card" elevation="1">
						<ChatWorkflowTimeline :node-blocks="store.nodeBlocks" />
					</v-card>
				</div>

				<!-- ── Streaming: Report card below timeline ─────────── -->
				<div
					v-if="store.isReportStreaming && store.streamingReportContent"
					class="row ai-row"
				>
					<v-avatar
						color="blue-darken-3"
						size="34"
						rounded="lg"
						class="avatar"
						style="visibility: hidden"
					/>
					<v-card class="ai-card report-card" elevation="1">
						<ChatStreamingReport :content="store.streamingReportContent" />
					</v-card>
				</div>

				<!-- ── Streaming spinner (before first node arrives) ── -->
				<div
					v-else-if="store.isStreaming && store.nodeBlocks.length === 0"
					class="row ai-row"
				>
					<v-avatar color="blue-darken-3" size="34" rounded="lg" class="avatar">
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card" elevation="1">
						<div class="thinking-dots">
							<span class="dot" />
							<span class="dot dot--2" />
							<span class="dot dot--3" />
						</div>
					</v-card>
				</div>
			</div>
		</template>
	</div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownContent } from '~/utils/markdown';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import { useChatStore } from '~/stores/chat';
import { extractReportContent } from '~/utils/reportTimeline';
import type { ResultData } from '~/services/resultSet/index';
import type { ChatMessage } from '~/services/chat/index';
import ChatWelcome from './ChatWelcome.vue';
import ChatResultSet from './ChatResultSet.vue';
import ChatMarkdownReport from './ChatMarkdownReport.vue';
import ChatWorkflowTimeline from './ChatWorkflowTimeline.vue';
import ChatStreamingReport from './ChatStreamingReport.vue';

const TIMELINE_ABSORBED_TYPES = new Set([
	'result-set',
	'markdown-report',
	'html',
]);

const store = useChatStore();
const listRef = ref<HTMLElement | null>(null);
const { renderECharts } = useEchartsRenderer();

const filteredMessages = computed<ChatMessage[]>(() => {
	const msgs = store.currentMessages;
	if (!msgs.length) return msgs;

	const result: ChatMessage[] = [];
	for (let i = 0; i < msgs.length; i++) {
		const msg = msgs[i];
		if (!msg) continue;
		if (
			msg.role === 'assistant' &&
			TIMELINE_ABSORBED_TYPES.has(msg.messageType)
		) {
			const surroundHasTimeline = msgs.some(
				(m, j) =>
					j !== i &&
					m.role === 'assistant' &&
					m.messageType === 'timeline' &&
					m.sessionId === msg.sessionId,
			);
			if (surroundHasTimeline) continue;
		}
		result.push(msg);
	}
	return result;
});

const SANITIZE_OPTIONS = {
	ADD_TAGS: ['div'],
	ADD_ATTR: ['style', 'class'],
	RETURN_TRUSTED_TYPE: false as const,
};

function renderMarkdown(content: string): string {
	if (!content) return '';
	return DOMPurify.sanitize(
		renderMarkdownContent(content),
		SANITIZE_OPTIONS,
	) as string;
}

function sanitizeHtml(content: string): string {
	if (!content) return '';
	return DOMPurify.sanitize(content, SANITIZE_OPTIONS) as string;
}

function safeParseJson(content: string): ResultData | null {
	try {
		return JSON.parse(content);
	} catch {
		return null;
	}
}

function safeParseBlocks(content: string) {
	try {
		return JSON.parse(
			content,
		) as import('~/services/graph/index').GraphNodeResponse[][];
	} catch {
		return [];
	}
}

function escapeHtml(text: string): string {
	const div = document.createElement('div');
	div.textContent = text;
	return div.innerHTML;
}

let scrollRafId: number | null = null;
function scrollToBottom() {
	if (scrollRafId) cancelAnimationFrame(scrollRafId);
	scrollRafId = requestAnimationFrame(() => {
		if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight;
		scrollRafId = null;
	});
}

watch(
	() => store.currentMessages.length,
	() => {
		scrollToBottom();
		nextTick(() => renderECharts(listRef.value));
	},
);
watch(
	() => store.nodeBlocks,
	() => scrollToBottom(),
	{ deep: true },
);
watch(
	() => store.streamingReportContent,
	() => scrollToBottom(),
);
watch(
	() => store.isStreaming,
	(v) => {
		if (v) scrollToBottom();
	},
);
</script>

<style scoped>
/* ── Layout ──────────────────────────────────────────────────────────────────── */
.message-list {
	flex: 1;
	overflow-y: auto;
	display: flex;
	flex-direction: column;
	background-color: #fbfcfc;
	background-image: linear-gradient(
		90deg,
		transparent 0,
		transparent 91px,
		rgba(91, 108, 116, 0.1) 91px,
		rgba(91, 108, 116, 0.1) 92px,
		transparent 92px
	);
}

.messages-inner {
	max-width: 1040px;
	margin: 0 auto;
	padding: 34px 48px 52px;
	display: flex;
	flex-direction: column;
	gap: 0;
	width: 100%;
}

/* ── Row (shared by user + AI) ───────────────────────────────────────────────── */
.row {
	display: flex;
	align-items: flex-start;
	gap: 14px;
}

.user-row {
	position: relative;
	flex-direction: column;
	justify-content: flex-start;
	padding: 0 0 26px 44px;
}

.user-row::before {
	content: 'RESEARCH QUESTION';
	font-family: var(--da-font-mono);
	font-size: 8px;
	font-weight: 700;
	letter-spacing: 0.12em;
	color: var(--da-signal-600);
}

.ai-row {
	position: relative;
	justify-content: flex-start;
	padding: 28px 0 34px;
	border-top: 1px solid var(--da-line);
}

.ai-row::before {
	content: 'ANALYSIS OUTPUT';
	position: absolute;
	top: 10px;
	left: 48px;
	font-family: var(--da-font-mono);
	font-size: 8px;
	font-weight: 700;
	letter-spacing: 0.12em;
	color: #60889a;
}

/* ── Avatar ──────────────────────────────────────────────────────────────────── */
.avatar {
	flex-shrink: 0;
	margin-top: 2px;
	border-radius: 0 !important;
	box-shadow: 0 0 0 1px var(--da-line-strong);
}

.ai-row .avatar {
	background: var(--da-steel-600) !important;
}

.user-row .avatar {
	background: var(--da-graphite-800) !important;
}

/* ── User card ───────────────────────────────────────────────────────────────── */
.user-card {
	width: 100%;
	max-width: 100%;
	padding: 14px 18px;
	border: 0 !important;
	border-left: 3px solid var(--da-signal-500) !important;
	border-radius: 0 !important;
	background: #eef2f2 !important;
	box-shadow: none !important;
	color: #253037 !important;
	font-family: Georgia, 'Noto Serif SC', serif;
	font-size: 17px;
	line-height: 1.7;
	word-break: break-word;
}

/* ── AI card ─────────────────────────────────────────────────────────────────── */
.ai-card {
	flex: 1;
	min-width: 0;
	padding: 6px 0 0;
	border: 0 !important;
	border-radius: 0 !important;
	font-size: 14px;
	line-height: 1.8;
	max-width: 100%;
	word-break: break-word;
	color: var(--da-text);
	background: transparent !important;
	box-shadow: none !important;
}

/* Report card: full width like markdown content */
.report-card {
	max-width: 100% !important;
	padding: 0 !important;
	flex: 1;
	min-width: 0;
}

/* Timeline card: full width, let timeline handle its own padding */
.timeline-card {
	padding: 10px 0;
	max-width: 100% !important;
	flex: 1;
	min-width: 0;
}

/* ── Thinking dots ───────────────────────────────────────────────────────────── */
.thinking-dots {
	display: flex;
	align-items: center;
	gap: 5px;
	padding: 4px 2px;
}
.dot {
	width: 7px;
	height: 7px;
	background: var(--da-steel-600);
	border-radius: 50%;
	animation: dotBounce 1.2s infinite;
}
.dot--2 {
	animation-delay: 0.2s;
}
.dot--3 {
	animation-delay: 0.4s;
}
@keyframes dotBounce {
	0%,
	60%,
	100% {
		transform: translateY(0);
	}
	30% {
		transform: translateY(-5px);
	}
}

/* ── Markdown body inside AI card ────────────────────────────────────────────── */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
	font-family: Georgia, 'Noto Serif SC', serif;
	font-weight: 600;
	margin: 20px 0 8px;
	line-height: 1.4;
}
.md-body :deep(p) {
	margin-bottom: 7px;
}
.md-body :deep(ul),
.md-body :deep(ol) {
	padding-left: 20px;
	margin-bottom: 7px;
}
.md-body :deep(li) {
	margin-bottom: 3px;
}
.md-body :deep(code:not(pre code)) {
	background: #eef2f3;
	border: 1px solid var(--da-line);
	padding: 2px 6px;
	border-radius: 3px;
	font-size: 12.5px;
	font-family: var(--da-font-mono);
	color: #9b4727;
}
.md-body :deep(blockquote) {
	border-left: 3px solid var(--da-steel-600);
	padding-left: 12px;
	color: var(--da-text-muted);
	margin: 6px 0;
}
.md-body :deep(table) {
	width: 100%;
	border-collapse: collapse;
	margin: 8px 0;
	display: block;
	overflow-x: auto;
}
.md-body :deep(thead) {
	display: table-header-group;
}
.md-body :deep(tbody) {
	display: table-row-group;
}
.md-body :deep(tr) {
	display: table-row;
	border-top: 1px solid #c6cbd1;
}
.md-body :deep(th) {
	display: table-cell;
	background: #e9edef;
	padding: 7px 12px;
	border: 1px solid var(--da-line);
	font-weight: 600;
	font-size: 13px;
	text-align: left;
}
.md-body :deep(td) {
	display: table-cell;
	padding: 7px 12px;
	border: 1px solid var(--da-line);
	font-size: 13px;
}
.md-body :deep(tr:nth-child(even)) {
	background: #f4f6f6;
}
.md-body :deep(a) {
	color: var(--da-steel-600);
	text-decoration: underline;
}
.md-body :deep(hr) {
	border: none;
	border-top: 1px solid var(--da-line);
	margin: 12px 0;
}
.md-body :deep(strong) {
	font-weight: 700;
}

/* ── Code block with header ─────────────────────────────────────────────────── */
.md-body :deep(.code-block-wrapper) {
	margin: 10px 0;
	border: 1px solid var(--da-line-strong);
	border-radius: 3px;
	overflow: auto;
	background: #eef2f3;
}
.md-body :deep(.code-block-header) {
	display: flex;
	justify-content: space-between;
	align-items: center;
	background: #f6f8fa;
	padding: 6px 10px;
	border-bottom: 1px solid #e1e4e8;
	font-size: 11px;
}
.md-body :deep(.code-language) {
	color: #6a737d;
	font-weight: 600;
	font-family: 'Monaco', 'Menlo', monospace;
	font-size: 10px;
	text-transform: uppercase;
}
.md-body :deep(.code-copy-button) {
	background: transparent;
	border: 1px solid #d1d5da;
	padding: 3px 10px;
	border-radius: 4px;
	font-size: 10px;
	cursor: pointer;
	transition: all 0.2s;
	color: #24292e;
}
.md-body :deep(.code-copy-button:hover) {
	background: #f3f4f6;
	border-color: #c6cbd1;
}
.md-body :deep(.code-copy-button.copied) {
	background: #28a745;
	border-color: #28a745;
	color: white;
}
.md-body :deep(pre.hljs) {
	margin: 0;
	padding: 10px;
	overflow-x: auto;
	overflow-y: hidden;
	background: #f6f8fa;
	font-size: 12px;
	line-height: 1.4;
	white-space: pre;
}
.md-body :deep(pre.hljs code) {
	display: block;
	padding: 0;
	margin: 0;
	background: transparent;
	border: none;
	font-family: 'Monaco', 'Menlo', monospace;
	color: inherit;
	white-space: pre;
	min-width: max-content;
}

/* ── Scrollbar ───────────────────────────────────────────────────────────────── */
.custom-scrollbar::-webkit-scrollbar {
	width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
	background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
	background: var(--da-line-strong);
	border-radius: 2px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
	background: #899399;
}

/* ── Status banners (warning / error) ────────────────────────────────────────── */
.status-banner {
	display: flex;
	align-items: center;
	padding: 10px 14px;
	border-radius: 3px;
	font-size: 13.5px;
	font-weight: 500;
	line-height: 1.5;
	max-width: 75%;
}
.status-banner--warning {
	background: #fffbeb;
	border: 1px solid #fcd34d;
	color: #92400e;
}
.status-banner--error {
	background: #fef2f2;
	border: 1px solid #fca5a5;
	color: #991b1b;
}

@media (max-width: 768px) {
	.message-list {
		background-image: none;
	}

	.messages-inner {
		padding: 24px 16px 36px;
	}

	.user-row {
		padding-left: 0;
	}

	.user-card {
		font-size: 15px;
	}

	.ai-row::before {
		left: 44px;
	}
}
</style>
