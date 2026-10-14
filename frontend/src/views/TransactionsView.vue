<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { http } from '../api/http';

interface Account { id:string; accountNo:string; currency:string; status:string }
interface Transaction { id:string; externalId:string; accountNo:string; counterpartyAccount?:string; direction:string; amount:number; currency:string; transactionTime:string; transactionType:string; source:string }
interface ImportResult { batchNo:string; total:number; imported:number; duplicates:number; rejected:number; errors:string[] }
interface Integration { name:string; available:boolean; dataMode:string }

const route = useRoute(), router = useRouter();
const rows = ref<Transaction[]>([]), accounts = ref<Account[]>([]), integrations = ref<Integration[]>([]);
const file = ref<File>(), loading = ref(false), importing = ref(false), loadError = ref(''), lastResult = ref<ImportResult>();
const importMode = ref<'LOCAL_CSV'|'AMLSIM_CSV'|'FINERACT_API'>('LOCAL_CSV');
const amlSimCurrency = ref('USD'), fineractAccountId = ref<number>();
const selectedAccountId = ref(typeof route.query.accountId === 'string' ? route.query.accountId : '');
const selectedAccountNo = computed(() => accounts.value.find(a => a.id === selectedAccountId.value)?.accountNo || route.query.accountNo || '全部账户');
const fineractAvailable = computed(() => integrations.value.find(item => item.name === 'FINERACT_API')?.available ?? false);

async function load() {
  loading.value = true; loadError.value = '';
  try {
    const [txResult, accountResult, integrationResult] = await Promise.all([
      http.get('/transactions', { params: { accountId: selectedAccountId.value || undefined, size: 100 } }),
      http.get('/accounts', { params: { size: 100 } }),
      http.get('/transactions/integrations')
    ]);
    rows.value = txResult.data.content; accounts.value = accountResult.data.content; integrations.value = integrationResult.data;
  } catch (error:any) { loadError.value = error.response?.data?.message || '交易与集成状态加载失败'; }
  finally { loading.value = false; }
}
function applyAccount() { router.replace({ path: '/transactions', query: selectedAccountId.value ? { accountId: selectedAccountId.value } : {} }); load(); }
function chooseFile(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0]; }
async function runImport() {
  importing.value = true;
  try {
    if (importMode.value === 'FINERACT_API') {
      if (!fineractAccountId.value) return;
      lastResult.value = (await http.post(`/transactions/import/fineract/${fineractAccountId.value}`)).data;
    } else {
      if (!file.value) return;
      const body = new FormData(); body.append('file', file.value);
      const url = importMode.value === 'AMLSIM_CSV' ? '/transactions/import/amlsim' : '/transactions/import';
      const params = importMode.value === 'AMLSIM_CSV' ? { currency: amlSimCurrency.value } : undefined;
      lastResult.value = (await http.post(url, body, { params })).data;
    }
    ElMessage.success(`成功 ${lastResult.value?.imported}，重复 ${lastResult.value?.duplicates}，拒绝 ${lastResult.value?.rejected}`);
    await load();
  } finally { importing.value = false; }
}
async function downloadTemplate() {
  const amlSim = importMode.value === 'AMLSIM_CSV';
  const response = await http.get(amlSim ? '/transactions/template/amlsim' : '/transactions/template', { responseType: 'blob' });
  const url = URL.createObjectURL(response.data); const anchor = document.createElement('a'); anchor.href = url;
  anchor.download = amlSim ? 'amlsim-transactions-template.csv' : 'transaction-import-template.csv'; anchor.click(); URL.revokeObjectURL(url);
}
onMounted(load);
</script>

<template><div>
  <div class="page-head"><div><h2>模拟交易导入</h2><p class="muted">当前账户：{{ selectedAccountNo }}。AMLSim 数据明确标记为模拟数据；Fineract 仅在显式启用并连接成功时导入。</p></div><el-button v-if="importMode !== 'FINERACT_API'" @click="downloadTemplate">下载 CSV 模板</el-button></div>
  <el-alert v-if="loadError" :title="loadError" type="error" show-icon><template #default><el-button size="small" @click="load">重试</el-button></template></el-alert>
  <div class="panel import-panel">
    <div class="toolbar">
      <el-select v-model="importMode" style="width:210px"><el-option label="平台 CSV（模拟）" value="LOCAL_CSV"/><el-option label="IBM AMLSim CSV（模拟）" value="AMLSIM_CSV"/><el-option label="Apache Fineract API（外部）" value="FINERACT_API"/></el-select>
      <template v-if="importMode !== 'FINERACT_API'">
        <input type="file" accept=".csv,text/csv" @change="chooseFile"/>
        <el-input v-if="importMode === 'AMLSIM_CSV'" v-model="amlSimCurrency" maxlength="3" placeholder="币种，如 USD" style="width:130px"/>
      </template>
      <el-input-number v-else v-model="fineractAccountId" :min="1" :controls="false" placeholder="Fineract 储蓄账户 ID" style="width:220px"/>
      <el-button type="primary" :disabled="importMode === 'FINERACT_API' ? !fineractAvailable || !fineractAccountId : !file" :loading="importing" @click="runImport">执行导入</el-button>
    </div>
    <el-alert v-if="importMode === 'AMLSIM_CSV'" title="接受 IBM AMLSim 官方默认交易列；CSV 不含币种，必须在导入时指定，并与本地映射账户一致。SAR 标记只是合成标签。" type="warning" :closable="false"/>
    <el-alert v-else-if="importMode === 'FINERACT_API'" :title="fineractAvailable ? 'Fineract 已配置启用；远端失败会明确报错，不会生成替代数据。' : 'Fineract 当前未启用。本地 CSV 与 AMLSim 导入仍可独立使用。'" :type="fineractAvailable ? 'info' : 'warning'" :closable="false"/>
    <el-alert v-else title="CSV 中的账户必须已存在且为 ACTIVE；重复流水按 source + externalId 跳过。" type="info" :closable="false"/>
    <div v-if="lastResult" class="import-result"><b>批次 {{lastResult.batchNo}}</b><span>总计 {{lastResult.total}}</span><span>成功 {{lastResult.imported}}</span><span>重复 {{lastResult.duplicates}}</span><span>拒绝 {{lastResult.rejected}}</span><ul v-if="lastResult.errors.length"><li v-for="error in lastResult.errors" :key="error">{{error}}</li></ul></div>
  </div>
  <div class="panel transaction-table">
    <div class="toolbar"><el-select v-model="selectedAccountId" clearable filterable placeholder="按账户筛选" style="width:260px" @change="applyAccount"><el-option v-for="account in accounts" :key="account.id" :label="`${account.accountNo} / ${account.currency}`" :value="account.id"/></el-select></div>
    <el-table v-loading="loading" :data="rows" empty-text="暂无交易"><el-table-column prop="externalId" label="外部流水号" min-width="170"/><el-table-column prop="accountNo" label="账户" min-width="140"/><el-table-column prop="counterpartyAccount" label="对手方" min-width="140"/><el-table-column prop="direction" label="方向" width="80"/><el-table-column prop="amount" label="金额" width="130"/><el-table-column prop="currency" label="币种" width="80"/><el-table-column prop="transactionType" label="类型" min-width="150"/><el-table-column prop="source" label="来源" width="110"/><el-table-column prop="transactionTime" label="交易时间" min-width="190"/></el-table>
  </div>
</div></template>

<style scoped>.import-panel{margin-bottom:16px}.toolbar{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:12px}.import-result{display:flex;gap:18px;align-items:center;flex-wrap:wrap;margin-top:16px;padding:12px;background:#f5f8fc;border-radius:8px}.import-result ul{width:100%;margin:0;color:#c45656}.transaction-table{padding-top:14px}</style>
