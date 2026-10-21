<script setup lang="ts">defineProps<{title:string;subtitle?:string;back?:string}>()</script>
<template><header class="page-header"><div><el-button v-if="back" link @click="$router.push(back)" class="back-link">← 返回列表</el-button><h1>{{ title }}</h1><p v-if="subtitle">{{ subtitle }}</p></div><div class="header-actions"><slot /></div></header></template>
