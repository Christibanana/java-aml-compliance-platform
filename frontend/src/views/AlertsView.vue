<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
import { ElMessage, ElMessageBox } from 'element-plus'

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref<any>()
const filters = reactive({ status: '', riskLevel: '', ruleCode: '', keyword: '', page: 0, size: 20 })

async function load() {
  loading.value = true
  try {
    const result = await http.get('/alerts', { params: { ...filters, page: filters.page } })
    rows.value = result.data.content
    total.value = result.data.total
  } finally { loading.value = false }
}
async function show(id: string) { detail.value = (await http.get(`/alerts/${id}`)).data; detailVisible.value = true }
async function move(id: string, status: string) {
  const title = status === 'CLOSED' ? '关闭预警' : '开始审核'
  const { value } = await ElMessageBox.prompt('请输入审核意见', title, { inputValidator: v => !!v || '审核意见不能为空' })
  await http.post(`/alerts/${id}/transition`, { status, comment: value })
  ElMessage.success('预警状态已更新'); await load(); if (detailVisible.value) await show(id)
}
async function escalate(id: string) {
  const { value } = await ElMessageBox.prompt('请输入案件标题', '升级案件', { inputValidator: v => !!v || '案件标题不能为空' })
  await http.post(`/alerts/${id}/escalate`, { title: value })
  ElMessage.success('已升级为案件'); detailVisible.value = false; await load()
}
function reset() { Object.assign(filters, { status: '', riskLevel: '', ruleCode: '', keyword: '', page: 0 }); load() }
function rowClick(row: any) { show(row.id) }
function pageChange(page: number) { filters.page = page - 1; load() }
onMounted(load)
</script>

<template>
  <div>
    <div class="page-head"><h2>风险预警审核</h2><el-button @click="load">刷新</el-button></div>
    <div class="panel">
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="预警号 / 客户" clearable style="width:220px"/>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width:140px"><el-option v-for="v in ['NEW','ASSIGNED','IN_REVIEW','CLOSED','ESCALATED']" :key="v" :label="v" :value="v"/></el-select>
        <el-select v-model="filters.riskLevel" placeholder="风险等级" clearable style="width:130px"><el-option v-for="v in ['HIGH','MEDIUM','LOW']" :key="v" :label="v" :value="v"/></el-select>
        <el-select v-model="filters.ruleCode" placeholder="规则" clearable style="width:140px"><el-option v-for="v in ['AML-R001','AML-R002','AML-R003']" :key="v" :label="v" :value="v"/></el-select>
        <el-button type="primary" @click="filters.page=0;load()">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无预警" @row-click="rowClick">
        <el-table-column prop="alertNo" label="预警编号" min-width="210"/><el-table-column prop="customerName" label="客户" min-width="120"/>
        <el-table-column prop="ruleCode" label="规则" width="105"/><el-table-column prop="ruleVersion" label="版本" width="65"/>
        <el-table-column prop="riskLevel" label="风险" width="80"/><el-table-column prop="status" label="状态" width="105"/>
        <el-table-column prop="transactionCount" label="交易数" width="80"/><el-table-column prop="summary" label="摘要" min-width="250" show-overflow-tooltip/>
      </el-table>
      <el-pagination class="pager" :current-page="filters.page + 1" :page-size="filters.size" :total="total" @current-change="pageChange"/>
    </div>
    <el-drawer v-model="detailVisible" title="预警审核" size="55%">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="预警编号">{{ detail.alert.alertNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.alert.status }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.alert.customerName }}（{{ detail.alert.customerNo }}）</el-descriptions-item><el-descriptions-item label="规则">{{ detail.alert.ruleCode }} v{{ detail.alert.ruleVersion }}</el-descriptions-item>
          <el-descriptions-item label="命中键" :span="2">{{ detail.hitKey }}</el-descriptions-item><el-descriptions-item label="解释" :span="2">{{ detail.explanation }}</el-descriptions-item>
        </el-descriptions>
        <h3>关联交易</h3><el-table :data="detail.transactions" size="small"><el-table-column prop="transactionNo" label="交易号"/><el-table-column prop="amount" label="金额"/><el-table-column prop="currency" label="币种" width="70"/><el-table-column prop="transactionTime" label="时间"/></el-table>
        <h3>审核轨迹</h3><el-timeline><el-timeline-item v-for="h in detail.history" :key="h.id" :timestamp="h.createdAt">{{ h.fromStatus || '创建' }} → {{ h.toStatus }}：{{ h.comment }}</el-timeline-item></el-timeline>
        <div class="actions"><el-button v-if="['NEW','ASSIGNED'].includes(detail.alert.status)" type="primary" @click="move(detail.alert.id,'IN_REVIEW')">开始审核</el-button><el-button v-if="detail.alert.status==='IN_REVIEW'" type="warning" @click="escalate(detail.alert.id)">升级案件</el-button><el-button v-if="!['CLOSED','ESCALATED'].includes(detail.alert.status)" @click="move(detail.alert.id,'CLOSED')">关闭预警</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>.pager{margin-top:16px;justify-content:flex-end}h3{margin-top:24px}.actions{margin-top:24px}</style>
