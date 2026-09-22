<template>
	<svg
		class="brand-mark-svg"
		:class="{ 'brand-mark-svg--animated': animated }"
		:width="size"
		:height="size"
		viewBox="0 0 48 48"
		fill="none"
		role="img"
		aria-label="DataAgent"
	>
		<rect class="brand-frame" x="4.5" y="4.5" width="39" height="39" />
		<path class="brand-axis" d="M11 35.5H37M11.5 11V36" />
		<path class="brand-trace" d="M12 31L20 23L27 27L36 16" />
		<circle class="brand-node brand-node--1" cx="12" cy="31" r="2.5" />
		<circle class="brand-node brand-node--2" cx="20" cy="23" r="2.5" />
		<circle class="brand-node brand-node--3" cx="27" cy="27" r="2.5" />
		<circle class="brand-node brand-node--4" cx="36" cy="16" r="3" />
	</svg>
</template>

<script setup lang="ts">
withDefaults(
	defineProps<{
		size?: number | string;
		animated?: boolean;
	}>(),
	{
		size: 40,
		animated: true,
	},
);
</script>

<style scoped>
.brand-mark-svg {
	display: block;
	overflow: visible;
}

.brand-frame {
	fill: #2b3439;
	stroke: #657178;
}

.brand-axis {
	stroke: #617077;
	stroke-width: 1;
}

.brand-trace {
	stroke: #f1f5f5;
	stroke-width: 2;
	stroke-linecap: square;
	stroke-linejoin: miter;
}

.brand-node {
	fill: #e76f3c;
	stroke: #2b3439;
	stroke-width: 1.5;
	transform-box: fill-box;
	transform-origin: center;
}

.brand-mark-svg--animated .brand-trace {
	stroke-dasharray: 38;
	animation: trace-in 0.75s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.brand-mark-svg--animated .brand-node {
	animation: node-in 0.28s ease-out both;
}

.brand-mark-svg--animated .brand-node--1 {
	animation-delay: 0.16s;
}

.brand-mark-svg--animated .brand-node--2 {
	animation-delay: 0.3s;
}

.brand-mark-svg--animated .brand-node--3 {
	animation-delay: 0.44s;
}

.brand-mark-svg--animated .brand-node--4 {
	animation-delay: 0.58s;
}

@keyframes trace-in {
	from {
		stroke-dashoffset: 38;
	}
	to {
		stroke-dashoffset: 0;
	}
}

@keyframes node-in {
	from {
		opacity: 0;
		transform: scale(0.2);
	}
	to {
		opacity: 1;
		transform: scale(1);
	}
}
</style>
