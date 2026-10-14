<script setup lang="ts">
import {computed,onMounted,reactive,ref} from 'vue';
import {http} from '../api/http';import {useAuthStore} from '../stores/auth';
const auth=useAuthStore(),allowed=computed(()=>auth.permissions.length===0||auth.has('AUDIT_READ'));
const rows=ref<any[]>([]),loading=ref(false),error=ref(''),forbidden=ref(false),total=ref(0),exporting=ref(false);
const form=reactive({actor:'',action:'',resourceType:'',result:'',dates:[] as string[],page:0,size:20});
const currentPage=computed({get:()=>form.page+1,set:(value:number)=>{form.page=value-1}});
const actions=['LOGIN','CREATE','UPDATE','IMPORT','EXECUTE','TRANSITION','ESCALATE','ASSIGN','ADD_NOTE','UPLOAD','DOWNLOAD','REMOVE','SCREEN','REVIEW','CALCULATE','EXPORT'];
const results=['SUCCESS','FAILED'];
function params(withPage=true){const value:any={};['actor','action','resourceType','result'].forEach(key=>{const v=(form as any)[key];if(v)value[key]=v});if(form.dates.length===2){value.from=form.dates[0];value.to=form.dates[1]}if(withPage){value.page=form.page;value.size=form.size}return value}
async function load(reset=false){
  if(!allowed.value){forbidden.value=true;return}if(reset)form.page=0;loading.value=true;error.value='';forbidden.value=false;
  try{const {data}=await http.get('/audit-logs',{params:params()});rows.value=data.content;total.value=data.total}
  catch(e:any){if(e.response?.status===403)forbidden.value=true;else error.value=e.response?.data?.message||'审计日志加载失败'}finally{loading.value=false}
}
function clear(){Object.assign(form,{actor:'',action:'',resourceType:'',result:'',dates:[],page:0,size:20});load()}
async function exportCsv(){
  exporting.value=true;try{const response=await http.get('/audit-logs/export',{params:params(false),responseType:'blob'});const disposition=response.headers['content-disposition']||'';const encoded=/filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];const url=URL.createObjectURL(response.data),link=document.createElement('a');link.href=url;link.download=encoded?decodeURIComponent(encoded):'aml-audit.csv';link.click();URL.revokeObjectURL(url)}catch(e:any){if(e.response?.status===403)forbidden.value=true;else error.value='审计 CSV 导出失败'}finally{exporting.value=false}
}
onMounted(()=>load());
</script>
<template><div><div class="page-head"><div><h2>审计日志</h2><p class="muted">审计记录只读、不可删除；查询和导出均由后端权限控制。</p></div></div>
  <el-result v-if="forbidden" icon="warning" title="无权查看审计日志" sub-title="需要 AUDIT_READ 权限，请联系系统管理员。"/>
  <el-alert v-else-if="error" type="error" :title="error" :closable="false"><template #default><el-button size="small" @click="load()">重新加载</el-button></template></el-alert>
  <template v-else><div class="panel toolbar filters"><el-input v-model="form.actor" clearable placeholder="操作者" style="width:150px"/><el-select v-model="form.action" clearable filterable placeholder="动作" style="width:150px"><el-option v-for="item in actions" :key="item" :value="item"/></el-select><el-input v-model="form.resourceType" clearable placeholder="资源类型" style="width:160px"/><el-select v-model="form.result" clearable placeholder="结果" style="width:120px"><el-option v-for="item in results" :key="item" :value="item"/></el-select><el-date-picker v-model="form.dates" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"/><el-button type="primary" @click="load(true)">查询</el-button><el-button @click="clear">重置</el-button><el-button :loading="exporting" @click="exportCsv">导出 CSV</el-button></div>
    <div class="panel table-panel" v-loading="loading"><el-table :data="rows" empty-text="当前条件下暂无审计记录"><el-table-column prop="occurredAt" label="时间" min-width="180"><template #default="scope">{{new Date(scope.row.occurredAt).toLocaleString()}}</template></el-table-column><el-table-column prop="actor" label="操作者" width="130"/><el-table-column prop="action" label="动作" width="130"/><el-table-column prop="resourceType" label="资源" min-width="150"/><el-table-column prop="resourceId" label="资源 ID" min-width="210" show-overflow-tooltip/><el-table-column prop="result" label="结果" width="100"/><el-table-column prop="detailsJson" label="详情" min-width="240" show-overflow-tooltip/></el-table><div class="pagination"><el-pagination v-model:current-page="currentPage" v-model:page-size="form.size" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load()" @size-change="()=>{form.page=0;load()}"/></div></div>
  </template>
</div></template>
<style scoped>.filters{flex-wrap:wrap;align-items:center}.table-panel{margin-top:16px}.pagination{display:flex;justify-content:flex-end;margin-top:16px}</style>
