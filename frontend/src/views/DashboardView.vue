<script setup lang="ts">
import {computed,nextTick,onBeforeUnmount,onMounted,ref} from 'vue';
import {init,use,type ECharts} from 'echarts/core';
import {BarChart,LineChart,PieChart} from 'echarts/charts';
import {GridComponent,LegendComponent,TooltipComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
import {http} from '../api/http';
import {useAuthStore} from '../stores/auth';

type Metric={name:string,value:number};
type RuleMetric={code:string,name:string,severity:string,value:number};
type Dashboard={summary:Record<string,number>,customerRiskDistribution:Metric[],alertTrend:{day:string,value:number}[],ruleHits:RuleMetric[],caseStatuses:Metric[],casePriorities:Metric[],from:string,to:string,generatedAt:string};
const auth=useAuthStore();
const allowed=computed(()=>auth.permissions.length===0||auth.has('REPORT_READ'));
const data=ref<Dashboard|null>(null),loading=ref(false),error=ref(''),forbidden=ref(false);
const dates=ref<string[]>([]),reportType=ref('CUSTOMER_RISK'),exporting=ref(false);
const riskEl=ref<HTMLElement>(),trendEl=ref<HTMLElement>(),rulesEl=ref<HTMLElement>(),casesEl=ref<HTMLElement>();
use([BarChart,LineChart,PieChart,GridComponent,LegendComponent,TooltipComponent,CanvasRenderer]);
const charts:ECharts[]=[];
const labels:Record<string,string>={LOW:'低风险',MEDIUM:'中风险',HIGH:'高风险',OPEN:'待调查',INVESTIGATING:'调查中',PENDING_REVIEW:'待复核',CLOSED:'已关闭'};
const cards=computed(()=>data.value?[['客户',data.value.summary.customers],['交易',data.value.summary.transactions],['预警',data.value.summary.alerts],['待处理预警',data.value.summary.pendingAlerts],['案件',data.value.summary.cases],['处理中案件',data.value.summary.activeCases],['高风险客户',data.value.summary.highRiskCustomers]]:[]);
const hasValues=(items:{value:number}[]|undefined)=>!!items?.some(item=>Number(item.value)>0);

async function load(){
  if(!allowed.value){forbidden.value=true;return}
  loading.value=true;error.value='';forbidden.value=false;
  try{
    const params=dates.value.length===2?{from:dates.value[0],to:dates.value[1]}:{};
    data.value=(await http.get('/dashboard',{params})).data;
    if(!dates.value.length&&data.value)dates.value=[data.value.from,data.value.to];
    await nextTick();renderCharts();
  }catch(e:any){if(e.response?.status===403)forbidden.value=true;else error.value=e.response?.data?.message||'统计数据加载失败'}
  finally{loading.value=false}
}
function chart(element:HTMLElement|undefined){if(!element)return null;const instance=init(element);charts.push(instance);return instance}
function renderCharts(){
  charts.splice(0).forEach(item=>item.dispose());if(!data.value)return;
  if(hasValues(data.value.customerRiskDistribution))chart(riskEl.value)?.setOption({tooltip:{trigger:'item'},legend:{bottom:0},series:[{type:'pie',radius:['45%','70%'],data:data.value.customerRiskDistribution.map(x=>({name:labels[x.name]||x.name,value:x.value}))}]});
  if(hasValues(data.value.alertTrend))chart(trendEl.value)?.setOption({tooltip:{trigger:'axis'},grid:{left:45,right:20,top:25,bottom:45},xAxis:{type:'category',data:data.value.alertTrend.map(x=>x.day.slice(5)),axisLabel:{rotate:40}},yAxis:{type:'value',minInterval:1},series:[{type:'line',smooth:true,areaStyle:{},data:data.value.alertTrend.map(x=>x.value)}]});
  if(hasValues(data.value.ruleHits))chart(rulesEl.value)?.setOption({tooltip:{trigger:'axis'},grid:{left:50,right:20,top:20,bottom:45},xAxis:{type:'category',data:data.value.ruleHits.map(x=>x.code)},yAxis:{type:'value',minInterval:1},series:[{type:'bar',data:data.value.ruleHits.map(x=>x.value),itemStyle:{color:'#3b82f6'}}]});
  if(hasValues(data.value.caseStatuses))chart(casesEl.value)?.setOption({tooltip:{trigger:'item'},legend:{bottom:0},series:[{type:'pie',radius:'68%',data:data.value.caseStatuses.map(x=>({name:labels[x.name]||x.name,value:x.value}))}]});
}
async function exportReport(){
  exporting.value=true;
  try{
    const params=dates.value.length===2?{from:dates.value[0],to:dates.value[1]}:{};
    const response=await http.get(`/reports/${reportType.value}/csv`,{params,responseType:'blob'});
    const disposition=response.headers['content-disposition']||'';
    const encoded=/filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
    const filename=encoded?decodeURIComponent(encoded):`aml-${reportType.value.toLowerCase()}.csv`;
    const url=URL.createObjectURL(response.data),link=document.createElement('a');link.href=url;link.download=filename;link.click();URL.revokeObjectURL(url);
  }catch(e:any){if(e.response?.status===403)forbidden.value=true;else error.value='CSV 报表导出失败'}finally{exporting.value=false}
}
const resize=()=>charts.forEach(item=>item.resize());
onMounted(()=>{load();window.addEventListener('resize',resize)});onBeforeUnmount(()=>{window.removeEventListener('resize',resize);charts.forEach(item=>item.dispose())});
</script>

<template><div>
  <div class="page-head"><div><h2>AML 统计仪表盘</h2><p class="muted">所有指标均由后端实时查询数据库；评分仅用于教学优先级排序。</p></div><span v-if="data" class="muted">数据截止 {{new Date(data.generatedAt).toLocaleString()}}</span></div>
  <el-result v-if="forbidden" icon="warning" title="无权访问统计报表" sub-title="需要 REPORT_READ 权限，请联系系统管理员。"/>
  <el-alert v-else-if="error" type="error" :title="error" :closable="false"><template #default><el-button size="small" @click="load">重新加载</el-button></template></el-alert>
  <div v-else v-loading="loading">
    <div class="panel toolbar filter-bar"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"/><el-button type="primary" @click="load">查询</el-button><el-divider direction="vertical"/><el-select v-model="reportType" style="width:180px"><el-option label="客户风险明细" value="CUSTOMER_RISK"/><el-option label="预警明细" value="ALERTS"/><el-option label="规则命中统计" value="RULE_HITS"/><el-option label="案件明细" value="CASES"/></el-select><el-button :loading="exporting" @click="exportReport">导出 CSV</el-button></div>
    <el-row v-if="data" :gutter="16" class="metrics"><el-col v-for="card in cards" :key="String(card[0])" :xs="12" :sm="8" :lg="6"><el-card class="metric" shadow="never"><span>{{card[0]}}</span><strong>{{card[1]}}</strong></el-card></el-col></el-row>
    <el-empty v-if="!loading&&!data" description="暂无统计数据"/>
    <el-row v-else-if="data" :gutter="16">
      <el-col :xs="24" :lg="12"><div class="panel chart-panel"><h3>客户风险分布</h3><div v-if="hasValues(data.customerRiskDistribution)" ref="riskEl" class="chart"></div><el-empty v-else description="暂无客户风险数据" :image-size="80"/></div></el-col>
      <el-col :xs="24" :lg="12"><div class="panel chart-panel"><h3>预警趋势</h3><div v-if="hasValues(data.alertTrend)" ref="trendEl" class="chart"></div><el-empty v-else description="所选周期暂无预警" :image-size="80"/></div></el-col>
      <el-col :xs="24" :lg="12"><div class="panel chart-panel"><h3>规则命中统计</h3><div v-if="hasValues(data.ruleHits)" ref="rulesEl" class="chart"></div><el-empty v-else description="所选周期暂无规则命中" :image-size="80"/></div></el-col>
      <el-col :xs="24" :lg="12"><div class="panel chart-panel"><h3>案件状态统计</h3><div v-if="hasValues(data.caseStatuses)" ref="casesEl" class="chart"></div><el-empty v-else description="所选周期暂无案件" :image-size="80"/></div></el-col>
      <el-col :span="24"><div class="panel case-table"><h3>案件优先级</h3><el-table :data="data.casePriorities" empty-text="所选周期暂无案件"><el-table-column prop="name" label="优先级"><template #default="scope">{{labels[scope.row.name]||scope.row.name}}</template></el-table-column><el-table-column prop="value" label="案件数"/></el-table></div></el-col>
    </el-row>
  </div>
</div></template>

<style scoped>.filter-bar{align-items:center;flex-wrap:wrap;margin-bottom:16px}.metrics{margin-bottom:0}.metric{margin-bottom:16px}.metric span{color:#64748b}.metric strong{display:block;font-size:30px;margin-top:8px}.chart-panel{min-height:350px;margin-bottom:16px}.chart-panel h3,.case-table h3{margin-top:0}.chart{height:285px}.case-table{margin-bottom:16px}</style>
