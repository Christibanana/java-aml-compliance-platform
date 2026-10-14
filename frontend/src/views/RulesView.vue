<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
import { ElMessage } from 'element-plus'

const rows = ref<any[]>([])
const compatibility = ref<any>({})
const executions = ref<any[]>([])
const running = ref(false)
const dialog = ref(false)
const selected = ref<any>()
const versions = ref<any[]>([])
const form = reactive({ amountThreshold: 100000, countThreshold: 10, windowMinutes: 10, windowHours: 24, description: '' })

const isLarge = computed(() => selected.value?.code === 'AML-R001')
const isFrequency = computed(() => selected.value?.code === 'AML-R002')
const isAggregate = computed(() => selected.value?.code === 'AML-R003')

async function load() {
  const [ruleResult, compatibleResult, executionResult] = await Promise.all([
    http.get('/rules'), http.get('/rules/compatibility'), http.get('/rules/executions')
  ])
  rows.value = ruleResult.data
  compatibility.value = compatibleResult.data
  executions.value = executionResult.data
}

async function run() {
  running.value = true
  try {
    const result = await http.post('/rules/execute', {})
    ElMessage.success(`处理 ${result.data.processed} 笔交易，新命中 ${result.data.hits} 条，生成 ${result.data.alerts} 条预警`)
    await load()
  } finally { running.value = false }
}

async function openVersions(row: any) {
  selected.value = row
  versions.value = (await http.get(`/rules/${row.code}/versions`)).data
  Object.assign(form, { amountThreshold: row.parameters.amountThreshold ?? 100000,
    countThreshold: row.parameters.countThreshold ?? 10, windowMinutes: row.parameters.windowMinutes ?? 10,
    windowHours: row.parameters.windowHours ?? 24, description: '' })
  dialog.value = true
}

async function createVersion() {
  const parameters: any = {}
  if (isLarge.value || isAggregate.value) parameters.amountThreshold = form.amountThreshold
  if (isFrequency.value) Object.assign(parameters, { countThreshold: form.countThreshold, windowMinutes: form.windowMinutes })
  if (isAggregate.value) parameters.windowHours = form.windowHours
  await http.post(`/rules/${selected.value.code}/versions`, { parameters, description: form.description })
  ElMessage.success('新规则版本已发布并激活')
  dialog.value = false
  await load()
}

async function toggle(row: any) {
  await http.put(`/rules/${row.code}/status`, { enabled: !row.enabled })
  ElMessage.success(row.enabled ? '规则已停用' : '规则已启用')
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head"><h2>交易监控规则</h2><el-button type="primary" :loading="running" @click="run">执行规则检测</el-button></div>
    <p class="warning">阈值仅用于教学演示，不代表法律或监管要求。规则参数经过白名单校验，不接受任意 DRL。</p>
    <div class="panel compatibility">
      <el-tag type="success">{{ compatibility.status || '检查中' }}</el-tag>
      <span>Drools {{ compatibility.droolsVersion }}</span><span>Java {{ compatibility.javaVersion }}</span>
      <span>已实现：大额、高频、累计交易</span>
    </div>
    <div class="panel table-panel">
      <el-table :data="rows">
        <el-table-column prop="code" label="编码" width="120"/><el-table-column prop="name" label="名称"/>
        <el-table-column prop="severity" label="风险" width="85"/><el-table-column prop="activeVersion" label="版本" width="75"/>
        <el-table-column prop="implementationStatus" label="实现状态" width="110"/>
        <el-table-column label="参数" min-width="210"><template #default="s"><code>{{ JSON.stringify(s.row.parameters) }}</code></template></el-table-column>
        <el-table-column label="操作" width="170"><template #default="s">
          <el-button link type="primary" @click="openVersions(s.row)">版本配置</el-button>
          <el-button link :type="s.row.enabled ? 'danger' : 'success'" @click="toggle(s.row)">{{ s.row.enabled ? '停用' : '启用' }}</el-button>
        </template></el-table-column>
      </el-table>
    </div>
    <div class="panel table-panel">
      <h3>最近执行</h3>
      <el-table :data="executions" size="small" empty-text="暂无执行记录">
        <el-table-column prop="execution_key" label="执行键" min-width="220"/><el-table-column prop="status" label="状态" width="100"/>
        <el-table-column prop="processed_count" label="处理数" width="90"/><el-table-column prop="hit_count" label="命中数" width="90"/>
        <el-table-column prop="started_at" label="开始时间" min-width="180"/>
      </el-table>
    </div>
    <el-dialog v-model="dialog" :title="`${selected?.code} 规则版本`" width="620px">
      <el-form label-width="110px">
        <el-form-item v-if="isLarge || isAggregate" label="金额阈值"><el-input-number v-model="form.amountThreshold" :min="0.01" :precision="2"/></el-form-item>
        <el-form-item v-if="isFrequency" label="次数阈值"><el-input-number v-model="form.countThreshold" :min="2"/></el-form-item>
        <el-form-item v-if="isFrequency" label="窗口（分钟）"><el-input-number v-model="form.windowMinutes" :min="1"/></el-form-item>
        <el-form-item v-if="isAggregate" label="窗口（小时）"><el-input-number v-model="form.windowHours" :min="1"/></el-form-item>
        <el-form-item label="变更说明"><el-input v-model="form.description" maxlength="500" show-word-limit/></el-form-item>
      </el-form>
      <el-table :data="versions" size="small"><el-table-column prop="versionNo" label="版本" width="70"/><el-table-column prop="parameters" label="参数"><template #default="s"><code>{{ JSON.stringify(s.row.parameters) }}</code></template></el-table-column><el-table-column prop="description" label="说明"/></el-table>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :disabled="!form.description" @click="createVersion">发布新版本</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>.compatibility{display:flex;gap:22px;align-items:center}.table-panel{margin-top:16px}h3{margin-top:0}code{font-size:12px}</style>
