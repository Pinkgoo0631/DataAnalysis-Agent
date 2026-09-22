/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<v-app id="app">
		<v-main>
			<BaseDrawer v-model="drawer" :drawer-width="196">
				<template #drawer>
					<div class="app-rail">
						<div class="rail-brand">
							<BrandMark :size="40" />
							<div class="rail-brand__copy">
								<strong>DataAgent</strong>
								<span>智能分析工作台</span>
							</div>
						</div>

						<nav class="rail-navigation" aria-label="主导航">
							<template
								v-for="(item, index) in navigationItems"
								:key="item.path"
							>
								<div
									v-if="
										index === 0 ||
										item.section !== navigationItems[index - 1]?.section
									"
									class="rail-section-label"
								>
									{{ item.sectionLabel }}
								</div>
								<button
									type="button"
									class="rail-action"
									:class="{ 'rail-action--active': isActive(item.path) }"
									:title="item.label"
									:aria-label="item.label"
									@click="navigateToPath(item.path)"
								>
									<v-icon :icon="item.icon" size="20" />
									<span class="rail-action__label">{{ item.label }}</span>
									<v-icon
										v-if="item.path === '/agent/new'"
										icon="mdi-plus"
										size="15"
										class="rail-action__suffix"
									/>
								</button>
							</template>
						</nav>

						<div class="rail-footer">
							<button
								type="button"
								class="rail-action rail-action--account"
								:title="`退出 ${authStore.user?.username || ''}`"
								aria-label="退出登录"
								@click="handleLogout"
							>
								<span class="rail-user">{{
									(authStore.user?.username || 'U').slice(0, 1).toUpperCase()
								}}</span>
								<span class="rail-action__label rail-account__name">{{
									authStore.user?.username || '退出登录'
								}}</span>
								<v-icon
									icon="mdi-logout"
									size="17"
									class="rail-action__suffix"
								/>
							</button>
							<button
								type="button"
								class="rail-action rail-close"
								aria-label="关闭主导航"
								@click="drawer = false"
							>
								<v-icon icon="mdi-close" size="20" />
								<span class="rail-action__label">关闭导航</span>
							</button>
						</div>
					</div>
				</template>

				<template #header="{ toggle, isOpen }">
					<v-btn
						icon
						variant="text"
						size="small"
						class="header-menu mr-3"
						aria-label="切换主导航"
						@click="toggle"
					>
						<v-icon :icon="isOpen ? 'mdi-menu-open' : 'mdi-menu'" />
					</v-btn>
					<div class="header-title-block">
						<div class="header-context instrument-code">
							ANALYSIS WORKSPACE /
							{{
								selectedAgentId
									? `A-${String(selectedAgentId).padStart(3, '0')}`
									: '未选择'
							}}
						</div>
						<div class="header-title">{{ currentRouteTitle }}</div>
					</div>
					<v-spacer />
					<div class="header-agent-switcher">
						<span class="header-agent-switcher__label instrument-code"
							>AGENT</span
						>
						<v-select
							v-model="selectedAgentId"
							:items="agentOptions"
							item-title="title"
							item-value="value"
							variant="plain"
							density="compact"
							hide-details
							placeholder="选择智能体"
							class="header-agent-select"
							@update:model-value="handleAgentSwitch"
						/>
					</div>
					<div class="system-readout">
						<span class="system-readout__dot" />
						<div>
							<div class="system-readout__label">系统就绪</div>
							<div class="system-readout__value instrument-code">
								{{ globalChatModelName || '模型待连接' }}
							</div>
						</div>
					</div>
				</template>

				<slot />
			</BaseDrawer>
		</v-main>

		<ConfirmDialog
			v-model="dialogState.isVisible"
			:title="dialogState.title"
			:message="dialogState.message"
			:prepend-icon="dialogState.icon"
			:confirm-text="dialogState.confirmText"
			@confirm="handleGlobalConfirm"
		/>
		<Tip />
	</v-app>
</template>

<script setup lang="ts">
import BaseDrawer from '../components/BaseDrawer/index.vue';
import agentService from '~/services/agent/index';
import modelConfigService from '~/services/modelConfig/index';
import { useAuthStore } from '~/stores/auth';
import { useDisplay } from 'vuetify';

const { dialogState, handleGlobalConfirm } = useConfirm();
const authStore = useAuthStore();
const drawer = ref(true);
const { mobile } = useDisplay();
const router = useRouter();
const route = useRoute();

const navigationItems = [
	{
		path: '/chat',
		icon: 'mdi-chart-timeline-variant-shimmer',
		label: '数据问答',
		section: 'analyse',
		sectionLabel: '分析工作',
	},
	{
		path: '/prompt-config',
		icon: 'mdi-tune-variant',
		label: '提示词配置',
		section: 'analyse',
		sectionLabel: '分析工作',
	},
	{
		path: '/knowledge/business',
		icon: 'mdi-book-open-page-variant-outline',
		label: '业务知识配置',
		section: 'knowledge',
		sectionLabel: '知识与语义',
	},
	{
		path: '/knowledge/agents',
		icon: 'mdi-brain',
		label: '智能体知识库',
		section: 'knowledge',
		sectionLabel: '知识与语义',
	},
	{
		path: '/knowledge/semantic-models',
		icon: 'mdi-vector-polyline',
		label: '语义模型配置',
		section: 'knowledge',
		sectionLabel: '知识与语义',
	},
	{
		path: '/system/agents',
		icon: 'mdi-robot-outline',
		label: '智能体管理',
		section: 'system',
		sectionLabel: '系统配置',
	},
	{
		path: '/system/data-sources',
		icon: 'mdi-database-outline',
		label: '数据连接',
		section: 'system',
		sectionLabel: '系统配置',
	},
	{
		path: '/system/model-config',
		icon: 'mdi-chip',
		label: '模型配置',
		section: 'system',
		sectionLabel: '系统配置',
	},
	{
		path: '/agent/new',
		icon: 'mdi-plus-box-outline',
		label: '创建智能体',
		section: 'create',
		sectionLabel: '创建',
	},
];

watch(
	mobile,
	(isMobile) => {
		if (isMobile) drawer.value = false;
	},
	{ immediate: true },
);

type DrawerAgentOption = {
	id: number;
	name: string;
	title: string;
	value: number;
	subtitle: string;
	avatar?: string;
	tags?: string;
};

const agents = ref<DrawerAgentOption[]>([]);
const selectedAgentId = ref<number | undefined>(undefined);
const globalChatModelName = ref('');

const routeTitleMap: Record<string, string> = {
	'/chat': '数据问答',
	'/dashboard': '数据看板',
	'/prompt-config': '提示词配置',
	'/knowledge/business': '业务知识配置',
	'/knowledge/agents': '智能体知识库',
	'/knowledge/semantic-models': '语义模型配置',
	'/system/agents': '智能体管理',
	'/system/data-sources': '数据连接',
	'/system/model-config': '模型配置',
	'/system/settings': '通用设置',
	'/agent/new': '创建智能体',
};

const agentOptions = computed(() => agents.value);

const currentRouteTitle = computed(() => {
	if (route.path.startsWith('/agent/') && route.path !== '/agent/new') {
		return '智能体详情';
	}
	return routeTitleMap[route.path] || 'Data Agent';
});

function parseRouteAgentId() {
	const pathId = Number(route.params.id);
	if (
		route.path.startsWith('/agent/') &&
		route.path !== '/agent/new' &&
		Number.isFinite(pathId) &&
		pathId > 0
	) {
		return pathId;
	}
	const queryId = Number(route.query.agentId);
	if (Number.isFinite(queryId) && queryId > 0) {
		return queryId;
	}
	return undefined;
}

function getQueryWithAgentId(agentId?: number) {
	const query: Record<string, string> = {};
	Object.keys(route.query).forEach((key) => {
		const value = route.query[key];
		if (key === 'agentId') return;
		if (Array.isArray(value)) {
			if (value[0]) query[key] = String(value[0]);
		} else if (value !== undefined) {
			query[key] = String(value);
		}
	});
	if (agentId) query.agentId = String(agentId);
	return query;
}

function applyAgentToCurrentRoute(agentId: number, replace = false) {
	if (route.path === '/agent/new') return;
	const target =
		route.path.startsWith('/agent/') && route.path !== '/agent/new'
			? { path: `/agent/${agentId}` }
			: { path: route.path, query: getQueryWithAgentId(agentId) };
	if (replace) {
		router.replace(target);
	} else {
		router.push(target);
	}
}

function navigateToPath(path: string) {
	if (path === '/agent/new') {
		if (route.path !== path) router.push({ path });
		return;
	}
	if (
		route.path === path &&
		path.startsWith('/agent/') &&
		selectedAgentId.value
	) {
		return;
	}
	if (selectedAgentId.value) {
		if (path.startsWith('/agent/') && path !== '/agent/new') {
			router.push({ path: `/agent/${selectedAgentId.value}` });
			return;
		}
		router.push({ path, query: { agentId: String(selectedAgentId.value) } });
		return;
	}
	router.push({ path });
}

function handleAgentSwitch(value: number | string | undefined) {
	const id = Number(value);
	if (!Number.isFinite(id) || id <= 0) return;
	selectedAgentId.value = id;
	applyAgentToCurrentRoute(id);
}

const isActive = (path: string) => route.path === path;

async function handleLogout() {
	await authStore.logout();
	await router.replace('/login');
}

async function loadGlobalModelName() {
	try {
		const configs = await modelConfigService.list();
		const activeChat = configs.find(
			(item) => item.modelType === 'CHAT' && item.isActive,
		);
		globalChatModelName.value = activeChat?.modelName || '';
	} catch (e) {
		console.error('Failed to load global model name', e);
	}
}

async function loadAgents() {
	const list = await agentService.list();
	agents.value = list
		.filter((item) => item.id !== undefined && item.id > 0)
		.map((item) => {
			const raw = item as unknown as Record<string, unknown>;
			return {
				id: item.id as number,
				name: item.name || `Agent ${item.id}`,
				title: item.name || `Agent ${item.id}`,
				value: item.id as number,
				subtitle: typeof raw.tags === 'string' ? raw.tags : '',
				avatar: typeof raw.avatar === 'string' ? raw.avatar : undefined,
				tags: typeof raw.tags === 'string' ? raw.tags : '',
			};
		});
}

function syncSelectedFromRoute() {
	const routeAgentId = parseRouteAgentId();
	if (routeAgentId && agents.value.some((item) => item.id === routeAgentId)) {
		selectedAgentId.value = routeAgentId;
	}
}

onMounted(async () => {
	await loadGlobalModelName();
	await loadAgents();
	syncSelectedFromRoute();
	const firstAgent = agents.value[0];
	if (!selectedAgentId.value && firstAgent?.id) {
		selectedAgentId.value = firstAgent.id;
		applyAgentToCurrentRoute(selectedAgentId.value, true);
	}
});

watch(
	() => route.fullPath,
	() => {
		syncSelectedFromRoute();
	},
);
</script>

<style scoped>
.app-rail {
	display: flex;
	flex-direction: column;
	align-items: stretch;
	height: 100%;
	padding: 14px 12px 12px;
	background: #20262a;
}

.rail-brand {
	display: flex;
	align-items: center;
	gap: 10px;
	height: 52px;
	margin-bottom: 12px;
	padding: 0 6px 12px;
	border-bottom: 1px solid #394247;
}

.rail-brand__copy {
	min-width: 0;
}

.rail-brand__copy strong,
.rail-brand__copy span {
	display: block;
}

.rail-brand__copy strong {
	color: #f4f7f7;
	font-size: 14px;
	font-weight: 750;
	letter-spacing: 0.01em;
}

.rail-brand__copy span {
	margin-top: 2px;
	color: #8f9ba1;
	font-size: 9px;
}

.rail-navigation {
	display: flex;
	flex: 1;
	flex-direction: column;
	width: 100%;
	overflow: auto;
}

.rail-action {
	position: relative;
	display: flex;
	align-items: center;
	gap: 10px;
	width: 100%;
	min-height: 38px;
	margin: 1px 0;
	padding: 0 10px;
	border: 1px solid transparent;
	background: transparent;
	color: #9eaaaf;
	cursor: pointer;
	text-align: left;
	transition:
		background-color 130ms ease,
		border-color 130ms ease,
		color 130ms ease,
		transform 130ms ease;
}

.rail-action:hover {
	border-color: #465158;
	background: #2a3237;
	color: #fff;
	transform: translateX(2px);
}

.rail-action--active {
	border-color: #516068;
	background: #303a3f;
	color: #fff;
}

.rail-action--active::before {
	position: absolute;
	top: 7px;
	bottom: 7px;
	left: -1px;
	width: 3px;
	background: var(--da-signal-500);
	content: '';
	transform-origin: center;
	animation: active-marker-in 180ms ease-out both;
}

.rail-action__label {
	min-width: 0;
	flex: 1;
	overflow: hidden;
	font-size: 12px;
	font-weight: 600;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.rail-action__suffix {
	flex: 0 0 auto;
	color: #718087;
}

.rail-section-label {
	padding: 11px 10px 5px;
	color: #68767d;
	font-size: 9px;
	font-weight: 650;
	letter-spacing: 0.04em;
}

.rail-section-label:not(:first-child) {
	margin-top: 3px;
	border-top: 1px solid #343e43;
}

@keyframes active-marker-in {
	from {
		opacity: 0;
		transform: scaleY(0.2);
	}
	to {
		opacity: 1;
		transform: scaleY(1);
	}
}

.rail-action:focus-visible {
	z-index: 1;
}

.rail-action :deep(.v-icon) {
	flex: 0 0 auto;
}

.rail-navigation::-webkit-scrollbar {
	width: 3px;
}

.rail-navigation::-webkit-scrollbar-thumb {
	background: #4a565c;
}

.rail-action--account {
	padding-left: 7px;
}

.rail-account__name {
	font-weight: 500;
}

.rail-user {
	display: grid;
	place-items: center;
	width: 26px;
	height: 26px;
	flex: 0 0 26px;
	background: #dce4e6;
	color: #283036;
	font-size: 11px;
	font-weight: 750;
}

.rail-footer {
	display: flex;
	flex-direction: column;
	align-items: stretch;
	width: 100%;
	padding-top: 8px;
	border-top: 1px solid #394247;
}

.rail-close {
	display: none;
}

.system-readout__dot {
	animation: system-pulse 2.4s ease-in-out infinite;
}

@keyframes system-pulse {
	0%,
	100% {
		box-shadow: 0 0 0 3px rgba(38, 135, 104, 0.12);
	}
	50% {
		box-shadow: 0 0 0 6px rgba(38, 135, 104, 0);
	}
}

.rail-action > :first-child {
	flex: 0 0 auto;
}

.header-menu {
	color: #4f5a60;
}

.header-title-block {
	min-width: 0;
}

.header-context {
	font-size: 9px;
	line-height: 1;
	color: #7b878d;
}

.header-title {
	margin-top: 5px;
	font-size: 15px;
	font-weight: 700;
	line-height: 1;
	color: #252b30;
}

.header-agent-switcher {
	display: flex;
	align-items: center;
	width: 250px;
	height: 38px;
	margin-right: 20px;
	padding-left: 12px;
	border: 1px solid var(--da-line);
	background: #fff;
}

.header-agent-switcher__label {
	padding-right: 10px;
	font-size: 8px;
	color: #7c888e;
	letter-spacing: 0.12em;
}

.header-agent-select {
	min-width: 0;
}

.header-agent-select :deep(.v-field__input) {
	min-height: 36px;
	padding-top: 0;
	padding-bottom: 0;
	font-size: 12px;
	font-weight: 650;
}

.system-readout {
	display: flex;
	align-items: center;
	gap: 9px;
	padding-left: 16px;
	border-left: 1px solid #cbd2d6;
}

.system-readout__dot {
	width: 7px;
	height: 7px;
	border-radius: 50%;
	background: #268768;
	box-shadow: 0 0 0 3px rgba(38, 135, 104, 0.12);
}

.system-readout__label {
	font-size: 10px;
	font-weight: 700;
	color: #4f5a60;
}

.system-readout__value {
	max-width: 160px;
	margin-top: 1px;
	font-size: 9px;
	color: #7b878d;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

@media (max-width: 768px) {
	.rail-close {
		display: flex;
	}

	.header-agent-switcher {
		width: 150px;
		margin-right: 8px;
	}

	.header-agent-switcher__label,
	.system-readout {
		display: none;
	}

	.header-context {
		display: none;
	}
}

@media (max-width: 480px) {
	.header-title-block {
		display: none;
	}

	.header-agent-switcher {
		flex: 1;
		width: auto;
		margin-right: 0;
	}
}
</style>
