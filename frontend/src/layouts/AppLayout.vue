<script setup lang="ts">
import {computed} from 'vue';import {useRoute} from 'vue-router';import {useAuthStore} from '../stores/auth';
const route=useRoute(),auth=useAuthStore();
const menus=[
  {path:'/dashboard',label:'仪表盘',permission:'REPORT_READ'},
  {path:'/customers',label:'客户管理',permission:'CUSTOMER_READ'},
  {path:'/accounts',label:'账户管理',permission:'CUSTOMER_READ'},
  {path:'/transactions',label:'交易流水',permission:'CUSTOMER_READ'},
  {path:'/rules',label:'规则管理',permission:'CUSTOMER_READ'},
  {path:'/alerts',label:'预警中心',permission:'ALERT_REVIEW'},
  {path:'/cases',label:'案件管理',permission:'CASE_INVESTIGATE'},
  {path:'/screening',label:'名单筛查',permission:'SCREENING_EXECUTE'},
  {path:'/risk-scores',label:'风险评分',permission:'REPORT_READ'},
  {path:'/audit',label:'审计日志',permission:'AUDIT_READ'}
];
const visibleMenus=computed(()=>auth.permissions.length===0?menus:menus.filter(menu=>auth.has(menu.permission)));
</script>
<template><el-container class="shell"><el-aside width="224px"><div class="brand">AML 合规平台<small>教学演示系统</small></div><el-menu router :default-active="route.path"><el-menu-item v-for="m in visibleMenus" :key="m.path" :index="m.path">{{m.label}}</el-menu-item></el-menu></el-aside><el-container><el-header><span>{{route.meta.title}}</span><div>{{auth.displayName}} <el-button link @click="auth.logout">退出</el-button></div></el-header><el-main><router-view/></el-main></el-container></el-container></template>
<style scoped>.shell{min-height:100vh}.el-aside{background:#14213d;color:#fff}.brand{font-size:19px;padding:22px}.brand small{display:block;color:#91a0bd;font-size:12px;margin-top:5px}.el-menu{border:0;background:transparent}.el-menu-item{color:#c9d2e3}.el-menu-item.is-active{color:#fff;background:#1f6feb}.el-header{display:flex;align-items:center;justify-content:space-between;background:#fff;border-bottom:1px solid #e5eaf1;font-weight:600}</style>
