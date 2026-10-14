import axios from 'axios'; import {ElMessage} from 'element-plus';
export const http=axios.create({baseURL:'/api',timeout:15000});
http.interceptors.request.use(c=>{const t=localStorage.getItem('aml_token');if(t)c.headers.Authorization=`Bearer ${t}`;return c});
http.interceptors.response.use(r=>r,e=>{if(e.response?.status===401){['aml_token','aml_name','aml_roles','aml_permissions'].forEach(key=>localStorage.removeItem(key));if(location.pathname!='/login')location.href='/login'}ElMessage.error(e.response?.data?.message||e.message||'请求失败');return Promise.reject(e)});
