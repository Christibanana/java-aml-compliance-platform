<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
import { ElMessage, ElMessageBox } from 'element-plus'

const rows = ref<any[]>([]), total = ref(0), loading = ref(false), detailVisible = ref(false)
const detail = ref<any>(), investigators = ref<any[]>([]), selectedInvestigator = ref(''), noteContent = ref('')
const filters = reactive({ status: '', priority: '', keyword: '', page: 0, size: 20 })

async function load() { loading.value = true; try { const r = await http.get('/cases', { params: filters }); rows.value = r.data.content; total.value = r.data.total } finally { loading.value = false } }
async function loadInvestigators() { investigators.value = (await http.get('/cases/investigators')).data }
async function show(id: string) { detail.value = (await http.get(`/cases/${id}`)).data; selectedInvestigator.value = detail.value.caseInfo.investigatorId || ''; detailVisible.value = true }
async function refreshDetail() { if (detail.value) await show(detail.value.caseInfo.id) }
async function assign() { if (!selectedInvestigator.value) return ElMessage.warning('请选择调查员'); await http.post(`/cases/${detail.value.caseInfo.id}/assign`, { userId: selectedInvestigator.value }); ElMessage.success('案件已分派'); await refreshDetail(); await load() }
async function addNote() { if (!noteContent.value.trim()) return ElMessage.warning('请输入调查笔记'); await http.post(`/cases/${detail.value.caseInfo.id}/notes`, { content: noteContent.value }); noteContent.value = ''; ElMessage.success('调查笔记已保存'); await refreshDetail(); await load() }
async function move(status: string) { const title = status === 'CLOSED' ? '关闭案件' : '案件状态变更'; const message = status === 'CLOSED' ? '请输入案件调查结论' : '请输入处理意见'; const { value } = await ElMessageBox.prompt(message, title, { inputValidator: v => !!v || '内容不能为空' }); await http.post(`/cases/${detail.value.caseInfo.id}/transition`, { status, comment: value }); ElMessage.success('案件状态已更新'); await refreshDetail(); await load() }
async function upload(event: Event) { const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file) return; if (file.size > 10 * 1024 * 1024) { ElMessage.error('文件不能超过 10MB'); input.value = ''; return } const form = new FormData(); form.append('file', file); try { await http.post(`/cases/${detail.value.caseInfo.id}/evidence`, form); ElMessage.success('证据已上传'); await refreshDetail(); await load() } finally { input.value = '' } }
async function download(item: any) { const response = await http.get(`/cases/${detail.value.caseInfo.id}/evidence/${item.id}/download`, { responseType: 'blob' }); const url = URL.createObjectURL(response.data); const anchor = document.createElement('a'); anchor.href = url; anchor.download = item.originalName; anchor.click(); URL.revokeObjectURL(url) }
async function removeEvidence(item: any) { const { value } = await ElMessageBox.prompt('请输入移除原因（文件仍按审计要求保留）', '移除证据', { inputValidator: v => !!v || '原因不能为空' }); await http.delete(`/cases/${detail.value.caseInfo.id}/evidence/${item.id}`, { data: { reason: value } }); ElMessage.success('证据已从活动清单移除'); await refreshDetail(); await load() }
function rowClick(row: any) { show(row.id) }
function pageChange(page: number) { filters.page = page - 1; load() }
function reset() { Object.assign(filters, { status: '', priority: '', keyword: '', page: 0 }); load() }
onMounted(() => { load(); loadInvestigators() })
</script>

<template>
  <div>
    <div class="page-head"><h2>案件调查</h2><el-button @click="load">刷新</el-button></div>
    <div class="panel">
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="案件号 / 标题" clearable style="width:220px"/>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width:160px"><el-option v-for="v in ['OPEN','INVESTIGATING','PENDING_REVIEW','CLOSED']" :key="v" :label="v" :value="v"/></el-select>
        <el-select v-model="filters.priority" placeholder="优先级" clearable style="width:130px"><el-option v-for="v in ['HIGH','MEDIUM','LOW']" :key="v" :label="v" :value="v"/></el-select>
        <el-button type="primary" @click="filters.page=0;load()">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无案件" @row-click="rowClick">
        <el-table-column prop="caseNo" label="案件编号" min-width="210"/><el-table-column prop="title" label="标题" min-width="230" show-overflow-tooltip/>
        <el-table-column prop="priority" label="优先级" width="90"/><el-table-column prop="status" label="状态" width="135"/>
        <el-table-column prop="investigatorUsername" label="调查员" width="120"/><el-table-column prop="alertCount" label="预警" width="70"/><el-table-column prop="noteCount" label="笔记" width="70"/><el-table-column prop="evidenceCount" label="证据" width="70"/>
      </el-table>
      <el-pagination class="pager" :current-page="filters.page + 1" :page-size="filters.size" :total="total" @current-change="pageChange"/>
    </div>

    <el-drawer v-model="detailVisible" title="案件调查工作台" size="62%">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="案件编号">{{ detail.caseInfo.caseNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.caseInfo.status }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.caseInfo.title }}</el-descriptions-item><el-descriptions-item label="优先级">{{ detail.caseInfo.priority }}</el-descriptions-item>
          <el-descriptions-item label="调查员">{{ detail.caseInfo.investigatorUsername || '未分派' }}</el-descriptions-item><el-descriptions-item label="结论">{{ detail.conclusion || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.caseInfo.status!=='CLOSED'" class="work-row">
          <el-select v-model="selectedInvestigator" placeholder="选择调查员" style="width:220px"><el-option v-for="u in investigators" :key="u.id" :value="u.id" :label="`${u.displayName} (${u.username})`"/></el-select>
          <el-button type="primary" @click="assign">分派案件</el-button><el-button v-if="detail.caseInfo.status==='OPEN'" type="success" @click="move('INVESTIGATING')">开始调查</el-button><el-button v-if="detail.caseInfo.status==='INVESTIGATING'" type="warning" @click="move('PENDING_REVIEW')">提交复核</el-button><el-button v-if="detail.caseInfo.status==='PENDING_REVIEW'" @click="move('INVESTIGATING')">退回调查</el-button><el-button v-if="detail.caseInfo.status==='PENDING_REVIEW'" type="danger" @click="move('CLOSED')">关闭案件</el-button>
        </div>
        <h3>关联预警</h3><el-table :data="detail.alerts" size="small"><el-table-column prop="alertNo" label="预警号"/><el-table-column prop="ruleCode" label="规则" width="110"/><el-table-column prop="riskLevel" label="风险" width="80"/><el-table-column prop="summary" label="摘要" show-overflow-tooltip/></el-table>
        <h3>调查笔记</h3><div v-if="detail.caseInfo.status!=='CLOSED'" class="note-editor"><el-input v-model="noteContent" type="textarea" :rows="3" maxlength="5000" show-word-limit placeholder="记录调查过程、资金来源核验和判断依据"/><el-button type="primary" @click="addNote">保存笔记</el-button></div><el-timeline><el-timeline-item v-for="n in detail.notes" :key="n.id" :timestamp="n.createdAt">{{ n.authorUsername }}：{{ n.content }}</el-timeline-item></el-timeline>
        <h3>证据文件</h3><label v-if="detail.caseInfo.status!=='CLOSED'" class="upload-button">上传证据<input type="file" accept=".pdf,.png,.jpg,.jpeg,.txt,.csv" @change="upload"/></label><p class="muted">允许 PDF、PNG、JPEG、TXT、CSV，最大 10MB；保存 SHA-256。移除仅做逻辑标记。</p>
        <el-table :data="detail.evidence" size="small"><el-table-column prop="originalName" label="文件名"/><el-table-column prop="contentType" label="类型"/><el-table-column prop="sizeBytes" label="字节" width="100"/><el-table-column prop="sha256" label="SHA-256" show-overflow-tooltip/><el-table-column label="操作" width="130"><template #default="s"><el-button link type="primary" @click.stop="download(s.row)">下载</el-button><el-button v-if="detail.caseInfo.status!=='CLOSED'" link type="danger" @click.stop="removeEvidence(s.row)">移除</el-button></template></el-table-column></el-table>
        <h3>状态历史</h3><el-timeline><el-timeline-item v-for="h in detail.history" :key="h.id" :timestamp="h.occurredAt">{{ h.fromStatus || '创建' }} → {{ h.toStatus }}：{{ h.comment }}</el-timeline-item></el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.pager{margin-top:16px;justify-content:flex-end}.work-row{display:flex;gap:10px;align-items:center;margin-top:20px;flex-wrap:wrap}h3{margin-top:26px}.note-editor{display:flex;gap:12px;align-items:flex-start;margin-bottom:18px}.upload-button{display:inline-block;background:#409eff;color:#fff;padding:8px 15px;border-radius:4px;cursor:pointer}.upload-button input{display:none}.muted{font-size:12px}
</style>
