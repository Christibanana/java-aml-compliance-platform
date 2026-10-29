<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { http } from '../api/http';
import { useAuthStore } from '../stores/auth';

interface Customer {
  id: string; customerNo: string; customerType: string; fullName: string; idType?: string;
  idNumberMasked?: string; countryCode?: string; riskLevel: string; status: string;
}

const router = useRouter();
const auth = useAuthStore();
interface KycRecord { status: string; documentSummary: string; submittedAt: string; reviewedAt?: string;
  reviewedBy?: string; reviewReason?: string; expiresAt?: string }
interface KycEvent { fromStatus?: string; toStatus: string; reason?: string; occurredAt: string }
interface KycDetail { current: KycRecord | null; history: KycEvent[] }
const kycVisible = ref(false);
const kycLoading = ref(false);
const kycSaving = ref(false);
const kycCustomer = ref<Customer>();
const kyc = ref<KycDetail>();
const kycSummary = ref('');
const reviewReason = ref('');
const reviewExpiry = ref<Date>();
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
async function openKyc(customer: Customer) {
  kycCustomer.value = customer;
  kycSummary.value = '';
  reviewReason.value = '';
  reviewExpiry.value = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000);
  kycVisible.value = true;
  await loadKyc();
}
async function loadKyc() {
  if (!kycCustomer.value) return;
  kycLoading.value = true;
  try { kyc.value = (await http.get<KycDetail>(`/customers/${kycCustomer.value.id}/kyc`)).data; }
  finally { kycLoading.value = false; }
}
async function submitKyc() {
  if (!kycCustomer.value || !kycSummary.value.trim()) { ElMessage.warning('请填写脱敏资料摘要'); return; }
  kycSaving.value = true;
  try {
    kyc.value = (await http.post<KycDetail>(`/customers/${kycCustomer.value.id}/kyc/submit`,
      { documentSummary: kycSummary.value.trim() })).data;
    kycSummary.value = '';
    ElMessage.success('KYC 已提交审核');
  } finally { kycSaving.value = false; }
}
async function reviewKyc(decision: 'VERIFIED' | 'REJECTED') {
  if (!kycCustomer.value || !reviewReason.value.trim()) { ElMessage.warning('请填写审核理由'); return; }
  if (decision === 'VERIFIED' && (!reviewExpiry.value || reviewExpiry.value.getTime() <= Date.now())) {
    ElMessage.warning('请选择未来的到期时间'); return;
  }
  kycSaving.value = true;
  try {
    kyc.value = (await http.post<KycDetail>(`/customers/${kycCustomer.value.id}/kyc/review`,
      { decision, reason: reviewReason.value.trim(), expiresAt: decision === 'VERIFIED' ? reviewExpiry.value!.toISOString() : null })).data;
    reviewReason.value = '';
    reviewExpiry.value = undefined;
    ElMessage.success(decision === 'VERIFIED' ? 'KYC 审核通过' : 'KYC 已退回');
  } finally { kycSaving.value = false; }
}
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
        <el-table-column label="操作" width="260" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="primary" @click="openKyc(row)">KYC</el-button><el-button link type="primary" @click="router.push({path:'/accounts',query:{customerId:row.id,customerName:row.fullName}})">查看账户</el-button></template></el-table-column>
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
    <el-dialog v-model="kycVisible" :title="`客户 KYC · ${kycCustomer?.fullName || ''}`" width="720px">
      <div v-loading="kycLoading">
        <el-alert title="仅用于合成数据演示，请勿填写真实证件号码或上传真实身份材料" type="warning" :closable="false" show-icon />
        <p>当前状态：<strong>{{ kyc?.current?.status || '尚未提交' }}</strong></p>
        <template v-if="kyc?.current">
          <p>资料摘要：{{ kyc.current.documentSummary }}</p>
          <p v-if="kyc.current.reviewedAt">审核时间：{{ new Date(kyc.current.reviewedAt).toLocaleString() }}；审核人：{{ kyc.current.reviewedBy }}</p>
          <p v-if="kyc.current.reviewReason">审核理由：{{ kyc.current.reviewReason }}</p>
          <p v-if="kyc.current.expiresAt">有效期至：{{ new Date(kyc.current.expiresAt).toLocaleString() }}</p>
        </template>
        <div v-if="auth.has('CUSTOMER_WRITE') && (!kyc?.current || ['REJECTED', 'EXPIRED'].includes(kyc.current.status))" class="panel" style="padding:16px;margin:16px 0">
          <h3>提交脱敏资料摘要</h3>
          <el-input v-model="kycSummary" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="仅记录虚构或脱敏的核验摘要" />
          <el-button type="primary" :loading="kycSaving" style="margin-top:12px" @click="submitKyc">提交审核</el-button>
        </div>
        <div v-if="auth.has('KYC_REVIEW') && kyc?.current?.status === 'PENDING'" class="panel" style="padding:16px;margin:16px 0">
          <h3>人工审核</h3>
          <el-input v-model="reviewReason" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="填写审核依据或退回原因" />
          <div style="margin:12px 0">通过有效期：<el-date-picker v-model="reviewExpiry" type="datetime" placeholder="审核通过时必填" /></div>
          <el-button type="success" :loading="kycSaving" @click="reviewKyc('VERIFIED')">审核通过</el-button>
          <el-button type="warning" :loading="kycSaving" @click="reviewKyc('REJECTED')">退回补充</el-button>
        </div>
        <h3>操作历史</h3>
        <el-table :data="kyc?.history || []" empty-text="暂无 KYC 历史">
          <el-table-column prop="toStatus" label="状态" width="120" />
          <el-table-column prop="reason" label="说明" min-width="200" />
          <el-table-column label="时间" width="190"><template #default="{row}">{{ new Date(row.occurredAt).toLocaleString() }}</template></el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>
