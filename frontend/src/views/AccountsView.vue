<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { http } from '../api/http';

interface Customer { id: string; customerNo: string; fullName: string; status: string }
interface Relation { customerId: string; customerNo: string; fullName: string; relationType: string; ownershipPercent: number }
interface Account { id: string; accountNo: string; currency: string; status: string; openedAt: string; customers: Relation[] }
const route = useRoute(), router = useRouter();
const rows = ref<Account[]>([]), customers = ref<Customer[]>([]), loading = ref(false), saving = ref(false), dialogVisible = ref(false);
const selectedCustomerId = ref(typeof route.query.customerId === 'string' ? route.query.customerId : '');
const selectedCustomerName = computed(() => customers.value.find(c => c.id === selectedCustomerId.value)?.fullName || route.query.customerName || '全部客户');
const formRef = ref<FormInstance>();
const form = reactive({ accountNo: '', currency: 'CNY', customerId: selectedCustomerId.value, relationType: 'OWNER', ownershipPercent: 100 });
const rules: FormRules = { accountNo: [{ required: true, message: '请输入账号' }, { pattern: /^[A-Za-z0-9_-]+$/, message: '账号格式不正确' }], currency: [{ required: true }, { pattern: /^[A-Za-z]{3}$/, message: '币种必须为三位代码' }], customerId: [{ required: true, message: '请选择客户' }] };

async function load() {
  loading.value = true;
  try {
    const [accountResult, customerResult] = await Promise.all([
      http.get('/accounts', { params: { customerId: selectedCustomerId.value || undefined, size: 100 } }),
      http.get('/customers', { params: { size: 100 } })]);
    rows.value = accountResult.data.content;
    customers.value = customerResult.data.content;
  } finally { loading.value = false; }
}
function applyCustomer() { router.replace({ path: '/accounts', query: selectedCustomerId.value ? { customerId: selectedCustomerId.value } : {} }); load(); }
function openCreate() { Object.assign(form, { accountNo: '', currency: 'CNY', customerId: selectedCustomerId.value, relationType: 'OWNER', ownershipPercent: 100 }); dialogVisible.value = true; }
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return;
  saving.value = true;
  try { await http.post('/accounts', { ...form, currency: form.currency.toUpperCase() }); ElMessage.success('账户创建成功'); dialogVisible.value = false; await load(); }
  finally { saving.value = false; }
}
async function changeStatus(account: Account, status: string) { await http.put(`/accounts/${account.id}/status`, { status }); ElMessage.success('账户状态已更新'); await load(); }
onMounted(load);
</script>

<template>
  <div>
    <div class="page-head"><div><h2>账户管理</h2><p class="muted">当前客户：{{ selectedCustomerName }}</p></div><el-button type="primary" @click="openCreate">新增账户</el-button></div>
    <div class="panel">
      <div class="toolbar"><el-select v-model="selectedCustomerId" clearable filterable placeholder="按客户筛选" style="width:280px" @change="applyCustomer"><el-option v-for="customer in customers" :key="customer.id" :label="`${customer.fullName} (${customer.customerNo})`" :value="customer.id"/></el-select></div>
      <el-table v-loading="loading" :data="rows" empty-text="该客户暂无账户">
        <el-table-column prop="accountNo" label="账号" min-width="160"/><el-table-column prop="currency" label="币种" width="90"/><el-table-column prop="status" label="状态" width="100"/>
        <el-table-column label="关联客户" min-width="220"><template #default="{row}"><span v-for="link in row.customers" :key="link.customerId">{{link.fullName}}（{{link.relationType}}）</span></template></el-table-column>
        <el-table-column prop="openedAt" label="开户时间" min-width="180"/>
        <el-table-column label="操作" width="250" fixed="right"><template #default="{row}"><el-button link type="primary" @click="router.push({path:'/transactions',query:{accountId:row.id,accountNo:row.accountNo}})">查看交易</el-button><el-dropdown trigger="click" @command="(status:string)=>changeStatus(row,status)"><el-button link type="primary">更改状态</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="ACTIVE">启用</el-dropdown-item><el-dropdown-item command="FROZEN">冻结</el-dropdown-item><el-dropdown-item command="CLOSED">关闭</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialogVisible" title="新增账户" width="520px"><el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="账号" prop="accountNo"><el-input v-model="form.accountNo" maxlength="64"/></el-form-item>
      <el-row :gutter="16"><el-col :span="12"><el-form-item label="币种" prop="currency"><el-input v-model="form.currency" maxlength="3"/></el-form-item></el-col><el-col :span="12"><el-form-item label="所有权比例"><el-input-number v-model="form.ownershipPercent" :min="0.01" :max="100" :precision="2" style="width:100%"/></el-form-item></el-col></el-row>
      <el-form-item label="客户" prop="customerId"><el-select v-model="form.customerId" filterable style="width:100%"><el-option v-for="customer in customers.filter(c=>c.status==='ACTIVE')" :key="customer.id" :label="`${customer.fullName} (${customer.customerNo})`" :value="customer.id"/></el-select></el-form-item>
      <el-form-item label="关系"><el-select v-model="form.relationType" style="width:100%"><el-option label="所有人" value="OWNER"/><el-option label="联名所有人" value="JOINT_OWNER"/><el-option label="授权签字人" value="AUTHORIZED_SIGNER"/></el-select></el-form-item>
    </el-form><template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
  </div>
</template>
