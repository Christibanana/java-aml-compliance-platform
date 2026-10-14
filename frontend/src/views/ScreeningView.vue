<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
import { ElMessage, ElMessageBox } from 'element-plus'

const form=reactive({customerId:'',name:'',countryCode:'CN',provider:'MOCK',schema:'LegalEntity'})
const result=ref<any>(), history=ref<any[]>([]), providers=ref<any[]>([]), loading=ref(false)
const selectedProvider=computed(()=>providers.value.find(p=>p.name===form.provider))

async function load(){const [p,h]=await Promise.all([http.get('/screenings/providers'),http.get('/screenings')]);providers.value=p.data;history.value=h.data;const d=providers.value.find((x:any)=>x.defaultProvider&&x.available);if(d)form.provider=d.name}
async function run(){if(!form.name.trim())return ElMessage.warning('请输入筛查名称');loading.value=true;try{result.value=(await http.post('/screenings',{...form,customerId:form.customerId||null})).data;ElMessage.success('筛查请求已完成');await load()}finally{loading.value=false}}
async function show(id:string){result.value=(await http.get(`/screenings/${id}`)).data}
async function review(match:any,decision:string){const {value}=await ElMessageBox.prompt('请输入人工复核依据','复核候选',{inputValidator:v=>!!v||'复核意见不能为空'});result.value=(await http.post(`/screenings/${result.value.request.id}/matches/${match.id}/review`,{decision,comment:value})).data;ElMessage.success('人工复核已保存');await load()}
function modeType(mode:string){return mode==='REAL'?'success':'warning'}
onMounted(load)
</script>

<template><div>
  <div class="page-head"><h2>名单筛查与人工复核</h2><el-button @click="load">刷新</el-button></div>
  <el-alert v-if="selectedProvider" :type="modeType(selectedProvider.dataMode)" show-icon :closable="false" :title="selectedProvider.dataMode==='REAL'?'真实数据 Provider：外部候选仍必须人工复核。':'模拟数据 Provider：结果仅用于测试和演示，不代表真实名单命中。'"/>
  <div class="panel form-panel"><el-form inline>
    <el-form-item label="客户 ID"><el-input v-model="form.customerId" placeholder="可选 UUID" style="width:260px"/></el-form-item>
    <el-form-item label="名称"><el-input v-model="form.name" placeholder="Mock 输入 demo 可生成候选" style="width:240px"/></el-form-item>
    <el-form-item label="国家代码"><el-input v-model="form.countryCode" maxlength="2" style="width:90px"/></el-form-item>
    <el-form-item label="实体类型"><el-select v-model="form.schema" style="width:150px"><el-option v-for="s in ['LegalEntity','Person','Organization']" :key="s" :value="s"/></el-select></el-form-item>
    <el-form-item label="Provider"><el-select v-model="form.provider" style="width:140px"><el-option v-for="p in providers" :key="p.name" :label="`${p.name} · ${p.dataMode}${p.available?'':'（未启用）'}`" :value="p.name" :disabled="!p.available"/></el-select></el-form-item>
    <el-button type="primary" :loading="loading" @click="run">发起筛查</el-button>
  </el-form></div>

  <div v-if="result" class="panel detail-panel">
    <el-alert :title="result.warning" :type="modeType(result.request.dataMode)" show-icon :closable="false"/>
    <el-descriptions :column="3" border><el-descriptions-item label="请求">{{result.request.id}}</el-descriptions-item><el-descriptions-item label="Provider">{{result.request.provider}}</el-descriptions-item><el-descriptions-item label="数据性质"><el-tag :type="modeType(result.request.dataMode)">{{result.request.dataMode}}</el-tag></el-descriptions-item><el-descriptions-item label="状态">{{result.request.status}}</el-descriptions-item><el-descriptions-item label="名称">{{result.request.queryName}}</el-descriptions-item><el-descriptions-item label="错误">{{result.request.errorMessage||'-'}}</el-descriptions-item></el-descriptions>
    <el-table :data="result.matches" empty-text="无候选匹配" style="margin-top:16px"><el-table-column prop="matchedName" label="候选名称"/><el-table-column prop="score" label="匹配分" width="100"/><el-table-column prop="dataset" label="数据集"/><el-table-column label="数据性质" width="110"><template #default="s"><el-tag :type="modeType(s.row.dataMode)">{{s.row.dataMode}}</el-tag></template></el-table-column><el-table-column prop="reviewStatus" label="复核状态" width="150"/><el-table-column label="操作" width="260"><template #default="s"><template v-if="['PENDING','NEEDS_MORE_INFO'].includes(s.row.reviewStatus)"><el-button link @click="review(s.row,'NEEDS_MORE_INFO')">补充资料</el-button><el-button link type="success" @click="review(s.row,'CONFIRMED_MATCH')">确认命中</el-button><el-button link type="danger" @click="review(s.row,'FALSE_POSITIVE')">排除</el-button></template></template></el-table-column></el-table>
  </div>

  <div class="panel"><h3>筛查历史</h3><el-table :data="history" @row-click="(r:any)=>show(r.id)"><el-table-column prop="queryName" label="名称"/><el-table-column prop="provider" label="Provider" width="110"/><el-table-column label="数据性质" width="110"><template #default="s"><el-tag :type="modeType(s.row.dataMode)">{{s.row.dataMode}}</el-tag></template></el-table-column><el-table-column prop="status" label="状态" width="110"/><el-table-column prop="matchCount" label="候选" width="80"/><el-table-column prop="pendingReviewCount" label="待复核" width="90"/><el-table-column prop="createdAt" label="发起时间"/></el-table></div>
</div></template>

<style scoped>.form-panel,.detail-panel{margin-top:16px}.panel+.panel{margin-top:16px}h3{margin-top:0}</style>
