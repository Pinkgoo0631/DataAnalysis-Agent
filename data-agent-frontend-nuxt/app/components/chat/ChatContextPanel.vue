<!--
  Read-only run context for the analysis workbench. All mutations still happen
  through the existing chat controls so this panel stays predictable.
-->
<template>
	<aside class="context-panel" aria-label="分析上下文">
		<header class="context-header">
			<div>
				<p class="context-kicker instrument-code">LIVE CONTEXT</p>
				<h2>运行上下文</h2>
			</div>
			<span
				class="context-state"
				:class="{ 'context-state--busy': store.isStreaming }"
			>
				{{ store.isStreaming ? 'RUNNING' : 'READY' }}
			</span>
		</header>

		<section class="context-section">
			<p class="section-label instrument-code">01 / SESSION</p>
			<div class="context-value context-value--strong">
				{{ store.currentSession?.title || '尚未选择会话' }}
			</div>
			<div class="context-meta instrument-code">
				{{ sessionCode }} · {{ store.currentMessages.length }} MESSAGES
			</div>
		</section>

		<section class="context-section">
			<p class="section-label instrument-code">02 / RESOURCES</p>
			<div class="resource-row">
				<span class="resource-icon"
					><v-icon icon="mdi-database-outline" size="15"
				/></span>
				<div>
					<span>数据源</span>
					<strong>{{ store.activeDatasource?.name || '未连接' }}</strong>
				</div>
			</div>
			<div class="resource-row">
				<span class="resource-icon"><v-icon icon="mdi-chip" size="15" /></span>
				<div>
					<span>推理模型</span>
					<strong>{{
						store.activeModelConfig?.modelName ||
						store.activeChatModel ||
						'未配置'
					}}</strong>
				</div>
			</div>
		</section>

		<section class="context-section">
			<p class="section-label instrument-code">03 / PARAMETERS</p>
			<div class="parameter-grid">
				<div>
					<span>人工确认</span
					><strong>{{ yesNo(store.requestOptions.humanFeedback) }}</strong>
				</div>
				<div>
					<span>仅生成 SQL</span
					><strong>{{ yesNo(store.requestOptions.nl2sqlOnly) }}</strong>
				</div>
				<div>
					<span>显示结果</span
					><strong>{{ yesNo(store.requestOptions.showSqlResults) }}</strong>
				</div>
				<div>
					<span>分页大小</span
					><strong>{{ store.requestOptions.pageSize }}</strong>
				</div>
			</div>
		</section>

		<section class="context-section context-section--progress">
			<p class="section-label instrument-code">04 / EXECUTION</p>
			<div class="execution-scale" aria-hidden="true">
				<span
					v-for="tick in 12"
					:key="tick"
					:class="{ active: tick <= activeTicks }"
				/>
			</div>
			<div class="execution-readout">
				<strong>{{ executionSteps }}</strong>
				<span>分析步骤</span>
			</div>
		</section>

		<footer class="context-footer instrument-code">
			<span class="pulse-dot" />
			LOCAL WORKSPACE · SYNCED
		</footer>
	</aside>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useChatStore } from '~/stores/chat';

const store = useChatStore();

const sessionCode = computed(() => {
	const id = store.currentSession?.id;
	return id ? `S-${String(id).slice(-6).toUpperCase()}` : 'S-EMPTY';
});

const executionSteps = computed(() => store.nodeBlocks.length);
const activeTicks = computed(() => {
	if (!store.isStreaming && executionSteps.value === 0) return 1;
	return Math.min(
		12,
		Math.max(2, executionSteps.value + (store.isStreaming ? 2 : 0)),
	);
});

function yesNo(value: boolean) {
	return value ? 'ON' : 'OFF';
}
</script>

<style scoped>
.context-panel {
	display: flex;
	flex-direction: column;
	width: 286px;
	min-width: 286px;
	height: 100%;
	overflow-y: auto;
	border-left: 1px solid var(--da-line);
	background: #f1f4f4;
	color: var(--da-text);
}

.context-header {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	padding: 20px 18px 18px;
	border-bottom: 1px solid var(--da-line);
}

.context-kicker,
.section-label {
	margin: 0 0 5px;
	font-size: 8px;
	letter-spacing: 0.14em;
	color: #78858b;
}

.context-header h2 {
	font-size: 15px;
	font-weight: 720;
	line-height: 1.2;
}

.context-state {
	padding: 4px 6px;
	border: 1px solid #9fbfb4;
	background: #e5f1ed;
	color: #28745f;
	font-family: var(--da-font-mono);
	font-size: 8px;
	font-weight: 700;
	letter-spacing: 0.08em;
}

.context-state--busy {
	border-color: #efb08f;
	background: #fff0e8;
	color: #a54d29;
}

.context-section {
	padding: 18px;
	border-bottom: 1px solid var(--da-line);
}

.context-value {
	font-size: 12px;
	line-height: 1.5;
	color: #59656b;
}

.context-value--strong {
	font-size: 13px;
	font-weight: 700;
	color: #273036;
}

.context-meta {
	margin-top: 7px;
	font-size: 8px;
	color: #8a959a;
}

.resource-row {
	display: flex;
	align-items: center;
	gap: 10px;
	padding: 9px 0;
	border-bottom: 1px dashed #d4dadd;
}

.resource-row:last-child {
	border-bottom: 0;
}

.resource-icon {
	display: grid;
	place-items: center;
	width: 30px;
	height: 30px;
	border: 1px solid #c9d1d4;
	background: #fff;
	color: #477b91;
}

.resource-row div {
	min-width: 0;
}

.resource-row span,
.parameter-grid span,
.execution-readout span {
	display: block;
	font-size: 9px;
	color: #7d898f;
}

.resource-row strong {
	display: block;
	max-width: 190px;
	margin-top: 2px;
	overflow: hidden;
	font-size: 11px;
	font-weight: 700;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.parameter-grid {
	display: grid;
	grid-template-columns: 1fr 1fr;
	border-top: 1px solid #d4dadd;
	border-left: 1px solid #d4dadd;
}

.parameter-grid div {
	padding: 9px;
	border-right: 1px solid #d4dadd;
	border-bottom: 1px solid #d4dadd;
	background: rgba(255, 255, 255, 0.55);
}

.parameter-grid strong {
	display: block;
	margin-top: 3px;
	font-family: var(--da-font-mono);
	font-size: 10px;
	color: #334047;
}

.context-section--progress {
	flex: 1;
}

.execution-scale {
	display: grid;
	grid-template-columns: repeat(12, 1fr);
	gap: 3px;
	margin: 14px 0 10px;
}

.execution-scale span {
	height: 20px;
	background: #d8dfe1;
}

.execution-scale span.active {
	background: #60889a;
}

.execution-scale span:nth-child(4n).active {
	background: var(--da-signal-500);
}

.execution-readout {
	display: flex;
	align-items: baseline;
	gap: 7px;
}

.execution-readout strong {
	font-family: var(--da-font-mono);
	font-size: 24px;
	font-weight: 500;
}

.context-footer {
	display: flex;
	align-items: center;
	gap: 7px;
	padding: 12px 18px;
	font-size: 8px;
	letter-spacing: 0.08em;
	color: #7d898f;
}

.pulse-dot {
	width: 6px;
	height: 6px;
	background: #268768;
	box-shadow: 0 0 0 3px rgba(38, 135, 104, 0.12);
}

@media (max-width: 1180px) {
	.context-panel {
		display: none;
	}
}
</style>
