<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { http } from '../api/http';

interface Customer {
  id: string; customerNo: string; customerType: string; fullName: string; idType?: string;
  idNumberMasked?: string; countryCode?: string; riskLevel: string; status: string;
}

const router = useRouter();
const rows = ref<Customer[]>([]);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingId = ref<string>();
const keyword = ref('');
const formRef = ref<FormInstance>();
const form = reactive({ customerNo: '', customerType: 'INDIVIDUAL', fullName: '', idType: '',
  idNumberMasked: '', countryCode: 'CN', riskLevel: 'LOW', status: 'ACTIVE' });
const rules: FormRules = {
  customerNo: [{ required: true, message: '请输入客户编号' }, { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线和连字符' }],
  fullName: [{ required: true, message: '请输入客户名称' }],
  countryCode: [{ pattern: /^[A-Za-z]{2}$/, message: '请输入两位国家/地区代码' }]
};

async function load() {
  loading.value = true;
  try { rows.value = (await http.get('/customers', { params: { keyword: keyword.value, size: 100 } })).data.content; }
  finally { loading.value = false; }
}
function openCreate() {
  editingId.value = undefined;
  Object.assign(form, { customerNo: '', customerType: 'INDIVIDUAL', fullName: '', idType: '',
    idNumberMasked: '', countryCode: 'CN', riskLevel: 'LOW', status: 'ACTIVE' });
  dialogVisible.value = true;
}
function openEdit(customer: Customer) {
  editingId.value = customer.id;
  Object.assign(form, customer);
  dialogVisible.value = true;
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return;
  saving.value = true;
  try {
    const payload = { ...form, countryCode: form.countryCode.toUpperCase() };
    if (editingId.value) await http.put(`/customers/${editingId.value}`, payload);
    else await http.post('/customers', payload);
    ElMessage.success(editingId.value ? '客户更新成功' : '客户创建成功');
    dialogVisible.value = false;
    await load();
  } finally { saving.value = false; }
}
onMounted(load);
</script>

<template>
  <div>
    <div class="page-head"><div><h2>客户管理</h2><p class="muted">先创建客户，再进入其账户与交易。</p></div><el-button type="primary" @click="openCreate">新增客户</el-button></div>
    <div class="panel">
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="客户编号或名称" style="max-width:320px" @keyup.enter="load"/><el-button @click="load">查询</el-button></div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无客户">
        <el-table-column prop="customerNo" label="客户编号" min-width="140"/><el-table-column prop="fullName" label="名称" min-width="180"/>
        <el-table-column prop="customerType" label="类型" width="130"/><el-table-column prop="countryCode" label="国家/地区" width="100"/>
        <el-table-column prop="riskLevel" label="风险等级" width="100"/><el-table-column prop="status" label="状态" width="100"/>
        <el-table-column label="操作" width="210" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="primary" @click="router.push({path:'/accounts',query:{customerId:row.id,customerName:row.fullName}})">查看账户</el-button></template></el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑客户' : '新增客户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="客户编号" prop="customerNo"><el-input v-model="form.customerNo" :disabled="!!editingId" maxlength="40"/></el-form-item></el-col><el-col :span="12"><el-form-item label="类型"><el-select v-model="form.customerType" :disabled="!!editingId" style="width:100%"><el-option label="个人" value="INDIVIDUAL"/><el-option label="机构" value="ORGANIZATION"/></el-select></el-form-item></el-col></el-row>
        <el-form-item label="客户名称" prop="fullName"><el-input v-model="form.fullName" maxlength="200"/></el-form-item>
        <el-row :gutter="16"><el-col :span="12"><el-form-item label="证件类型"><el-input v-model="form.idType" maxlength="30"/></el-form-item></el-col><el-col :span="12"><el-form-item label="脱敏证件号"><el-input v-model="form.idNumberMasked" maxlength="100" placeholder="仅填写脱敏值"/></el-form-item></el-col></el-row>
        <el-row :gutter="16"><el-col :span="8"><el-form-item label="国家/地区" prop="countryCode"><el-input v-model="form.countryCode" maxlength="2"/></el-form-item></el-col><el-col :span="8"><el-form-item label="风险等级"><el-select v-model="form.riskLevel" style="width:100%"><el-option v-for="item in ['LOW','MEDIUM','HIGH']" :key="item" :value="item"/></el-select></el-form-item></el-col><el-col :span="8"><el-form-item label="状态"><el-select v-model="form.status" :disabled="!editingId" style="width:100%"><el-option label="启用" value="ACTIVE"/><el-option label="停用" value="INACTIVE"/></el-select></el-form-item></el-col></el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
