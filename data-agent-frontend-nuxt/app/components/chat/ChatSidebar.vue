/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<div
		class="sidebar-wrapper"
		:class="{ collapsed: store.chatSidebarCollapsed }"
	>
		<!-- Expanded panel -->
		<div class="chat-sidebar">
			<!-- Header -->
			<div class="sidebar-header">
				<div>
					<span class="sidebar-eyebrow instrument-code">RESEARCH INDEX</span>
					<span class="sidebar-title">分析索引</span>
				</div>
				<v-btn
					icon
					variant="text"
					density="compact"
					size="small"
					class="toggle-btn"
					title="折叠侧边栏"
					@click="store.chatSidebarCollapsed = true"
				>
					<v-icon size="18">mdi-chevron-left</v-icon>
				</v-btn>
			</div>

			<!-- Session List -->
			<div class="session-list custom-scrollbar">
				<div class="session-group-label">工作记录 / RECENT</div>

				<div
					v-for="session in store.sessions"
					:key="session.id"
					class="session-item"
					:class="{ active: store.currentSession?.id === session.id }"
					@click="handleSelectSession(session)"
				>
					<template v-if="session.editing">
						<input
							v-model="session.editingTitle"
							class="session-rename-input"
							@click.stop
							@blur="handleSaveTitle(session)"
							@keyup.enter="handleSaveTitle(session)"
							@keyup.esc="cancelEdit(session)"
						/>
					</template>
					<template v-else>
						<div class="session-item-info" @dblclick.stop="startEdit(session)">
							<span class="session-item-title">{{
								session.title || '新会话'
							}}</span>
							<span class="session-item-time">{{
								formatTime(session.createTime || session.updateTime) || '—'
							}}</span>
						</div>
						<div class="session-item-actions">
							<v-btn
								icon
								variant="text"
								density="compact"
								size="x-small"
								class="action-btn--edit"
								title="重命名"
								@click.stop="startEdit(session)"
							>
								<v-icon size="14">mdi-pencil-outline</v-icon>
							</v-btn>
							<v-btn
								icon
								variant="text"
								density="compact"
								size="x-small"
								class="action-btn--star"
								title="收藏"
								@click.stop="handlePin(session)"
							>
								<v-icon size="14" :color="session.isPinned ? '#f59e0b' : ''">
									{{ session.isPinned ? 'mdi-star' : 'mdi-star-outline' }}
								</v-icon>
							</v-btn>
							<v-btn
								icon
								variant="text"
								density="compact"
								size="x-small"
								class="action-btn--danger"
								title="删除"
								@click.stop="handleDelete(session)"
							>
								<v-icon size="14">mdi-delete-outline</v-icon>
							</v-btn>
						</div>
					</template>
				</div>

				<div v-if="store.sessions.length === 0" class="empty-sessions">
					暂无历史会话
				</div>
			</div>

			<!-- Bottom: New Session Button -->
			<div class="sidebar-bottom">
				<v-btn
					block
					variant="outlined"
					color="primary"
					prepend-icon="mdi-plus-circle-outline"
					class="new-session-btn"
					@click="handleCreateNewSession"
				>
					新建研究页
				</v-btn>
			</div>
		</div>

		<!-- Collapsed FAB: floats at top-left of chat area -->
		<v-btn
			v-show="store.chatSidebarCollapsed"
			icon
			variant="tonal"
			color="primary"
			size="small"
			class="expand-fab"
			title="展开历史会话"
			@click="store.chatSidebarCollapsed = false"
		>
			<v-icon size="18">mdi-chevron-right</v-icon>
		</v-btn>
	</div>

	<!-- Confirm Dialog -->
	<v-dialog v-model="showDeleteConfirm" max-width="360">
		<v-card rounded="xl">
			<v-card-title class="text-subtitle-1 font-weight-bold pa-5 pb-2"
				>删除会话</v-card-title
			>
			<v-card-text class="px-5 text-body-2 text-medium-emphasis"
				>确定要删除这个会话吗？</v-card-text
			>
			<v-card-actions class="px-5 pb-4 gap-2">
				<v-spacer />
				<v-btn
					variant="text"
					size="small"
					class="text-none"
					@click="showDeleteConfirm = false"
					>取消</v-btn
				>
				<v-btn
					color="error"
					variant="flat"
					size="small"
					class="text-none"
					@click="confirmDelete"
					>确定</v-btn
				>
			</v-card-actions>
		</v-card>
	</v-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useChatStore, type ExtendedChatSession } from '~/stores/chat';
import type { ChatSession } from '~/services/chat/index';

const store = useChatStore();
const showDeleteConfirm = ref(false);
let sessionToDelete: ChatSession | null = null;

function formatTime(time: Date | string | undefined): string {
	if (!time) return '';
	const d = typeof time === 'string' ? new Date(time) : time;
	if (isNaN(d.getTime())) return '';
	const now = new Date();
	const isToday = d.toDateString() === now.toDateString();
	const pad = (n: number) => String(n).padStart(2, '0');
	if (isToday) return `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
	const isThisYear = d.getFullYear() === now.getFullYear();
	if (isThisYear)
		return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
	return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

async function handleCreateNewSession() {
	if (!store.currentAgentId) return;
	try {
		await store.createNewSession(store.currentAgentId);
	} catch (e) {
		console.error('创建会话失败', e);
	}
}

async function handleSelectSession(session: ChatSession) {
	if (store.currentSession?.id === session.id) return;
	try {
		await store.selectSession(session);
	} catch (e) {
		console.error('切换会话失败', e);
	}
}

function startEdit(session: ExtendedChatSession) {
	session.editing = true;
	session.editingTitle = session.title || '新会话';
}

function cancelEdit(session: ExtendedChatSession) {
	session.editing = false;
}

async function handleSaveTitle(session: ExtendedChatSession) {
	const newTitle = (session.editingTitle || '').trim();
	if (!newTitle) {
		session.editing = false;
		return;
	}
	if (newTitle === session.title) {
		session.editing = false;
		return;
	}
	try {
		await store.renameSession(session, newTitle);
	} catch (e) {
		console.error('重命名失败', e);
		session.editing = false;
	}
}

async function handlePin(session: ChatSession) {
	try {
		await store.pinSession(session);
	} catch (e) {
		console.error('置顶操作失败', e);
	}
}

function handleDelete(session: ChatSession) {
	sessionToDelete = session;
	showDeleteConfirm.value = true;
}

async function confirmDelete() {
	if (!sessionToDelete) return;
	showDeleteConfirm.value = false;
	try {
		await store.removeSession(sessionToDelete);
	} catch (e) {
		console.error('删除会话失败', e);
	}
	sessionToDelete = null;
}
</script>

<style scoped>
/* ── Wrapper: drives the width transition ────────────────────────────────────── */
.sidebar-wrapper {
	position: relative;
	width: 232px;
	min-width: 232px;
	transition:
		width 0.25s ease,
		min-width 0.25s ease;
	overflow: visible;
	height: 100%;
	flex-shrink: 0;
}

.sidebar-wrapper.collapsed {
	width: 0;
	min-width: 0;
}

/* ── Expanded panel ──────────────────────────────────────────────────────────── */
.chat-sidebar {
	width: 232px;
	background: #e9edee;
	border: 0;
	border-radius: 0;
	display: flex;
	flex-direction: column;
	height: 100%;
	overflow: hidden;
	transition: opacity 0.2s ease;
}

.collapsed .chat-sidebar {
	opacity: 0;
	pointer-events: none;
}

/* ── Header ─────────────────────────────────────────────────────────────────── */
.sidebar-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 16px 8px 14px 16px;
	border-bottom: 1px solid var(--da-line);
	min-height: 72px;
	flex-shrink: 0;
}

.sidebar-title {
	display: block;
	font-size: 13px;
	font-weight: 720;
	color: var(--da-graphite-900);
	letter-spacing: 0.3px;
	white-space: nowrap;
}

.sidebar-eyebrow {
	display: block;
	margin-bottom: 4px;
	font-size: 8px;
	color: #7e898f;
	letter-spacing: 0.12em;
}

.toggle-btn {
	color: #78848a !important;
}
.toggle-btn:hover {
	color: var(--da-signal-500) !important;
}

/* ── Session list ────────────────────────────────────────────────────────────── */
.session-list {
	flex: 1;
	overflow-y: auto;
	padding: 0 8px;
}

.session-group-label {
	font-size: 8px;
	font-weight: 600;
	color: #899399;
	letter-spacing: 0.5px;
	padding: 14px 8px 8px;
	font-family: var(--da-font-mono);
}

.session-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 8px 10px;
	border-radius: 0;
	cursor: pointer;
	transition: background 0.12s;
	margin-bottom: 2px;
	min-height: 44px;
}
.session-item:hover {
	background: rgba(255, 255, 255, 0.6);
}
.session-item.active {
	background: #fff;
	box-shadow:
		inset 3px 0 0 var(--da-signal-500),
		0 1px 0 var(--da-line),
		0 -1px 0 var(--da-line);
}

.session-item-info {
	display: flex;
	flex-direction: column;
	flex: 1;
	min-width: 0;
	gap: 1px;
}

.session-item-title {
	font-size: 13px;
	color: var(--da-text);
	line-height: 1.35;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.session-item-time {
	font-size: 11px;
	color: #899399;
	font-family: var(--da-font-mono);
	line-height: 1.2;
}

.session-item.active .session-item-title {
	color: var(--da-graphite-900);
	font-weight: 700;
}

/* ── Actions ─────────────────────────────────────────────────────────────────── */
.session-item-actions {
	display: none;
	flex-shrink: 0;
	align-items: center;
	gap: 12px;
	margin-left: 6px;
}
.session-item:hover .session-item-actions,
.session-item.active .session-item-actions {
	display: flex;
}

.action-btn--edit:hover {
	color: #3b82f6 !important;
}
.action-btn--star:hover {
	color: #f59e0b !important;
}
.action-btn--danger:hover {
	color: #ef4444 !important;
}

/* ── Rename input ────────────────────────────────────────────────────────────── */
.session-rename-input {
	flex: 1;
	font-size: 13px;
	border: 1px solid var(--da-signal-500);
	border-radius: 2px;
	padding: 2px 6px;
	outline: none;
	min-width: 0;
	background: white;
}

/* ── Empty state ─────────────────────────────────────────────────────────────── */
.empty-sessions {
	text-align: center;
	font-size: 12px;
	color: #899399;
	padding: 20px 0;
}

/* ── Bottom new session ──────────────────────────────────────────────────────── */
.sidebar-bottom {
	padding: 12px 16px 16px;
	border-top: 1px solid var(--da-line);
	flex-shrink: 0;
}

.new-session-btn {
	text-transform: none !important;
	letter-spacing: 0 !important;
	font-size: 14px !important;
	border-style: solid !important;
	border-radius: 0 !important;
	color: var(--da-signal-600) !important;
	border-color: rgba(231, 111, 60, 0.55) !important;
}

/* ── Collapsed expand FAB ────────────────────────────────────────────────────── */
.expand-fab {
	position: absolute;
	top: 10px;
	left: 8px;
	z-index: 10;
	border: 1px solid var(--da-line) !important;
	box-shadow: var(--da-shadow-panel) !important;
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

@media (max-width: 768px) {
	.sidebar-wrapper {
		display: none;
	}
}
</style>
