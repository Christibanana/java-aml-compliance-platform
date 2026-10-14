import {createRouter,createWebHistory} from 'vue-router'; import AppLayout from '../layouts/AppLayout.vue';
const routes=[{path:'/login',component:()=>import('../views/LoginView.vue')},{path:'/',component:AppLayout,children:[
 {path:'',redirect:'/dashboard'},{path:'dashboard',component:()=>import('../views/DashboardView.vue'),meta:{title:'首页仪表盘'}},
 {path:'customers',component:()=>import('../views/CustomersView.vue'),meta:{title:'客户管理'}},{path:'accounts',component:()=>import('../views/AccountsView.vue'),meta:{title:'账户管理'}},
 {path:'transactions',component:()=>import('../views/TransactionsView.vue'),meta:{title:'交易流水'}},{path:'rules',component:()=>import('../views/RulesView.vue'),meta:{title:'规则管理'}},
 {path:'alerts',component:()=>import('../views/AlertsView.vue'),meta:{title:'预警中心'}},{path:'cases',component:()=>import('../views/CasesView.vue'),meta:{title:'案件管理'}},
 {path:'screening',component:()=>import('../views/ScreeningView.vue'),meta:{title:'名单筛查'}},{path:'risk-scores',component:()=>import('../views/RiskScoresView.vue'),meta:{title:'风险评分'}},{path:'audit',component:()=>import('../views/AuditView.vue'),meta:{title:'审计日志'}}]}];
const router=createRouter({history:createWebHistory(),routes});router.beforeEach(to=>{if(to.path!='/login'&&!localStorage.getItem('aml_token'))return '/login'});export default router;
