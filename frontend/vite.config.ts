import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';

const backend=process.env.AML_BACKEND_PROXY_TARGET||'http://localhost:8080';
const proxy=process.env.AML_E2E_RUN==='isolated'
  ? {target:backend,headers:{origin:'http://localhost:5173'}} : backend;

export default defineConfig({
  plugins:[vue()],
  server:{proxy:{'/api':proxy,'/actuator':backend}},
  build:{rollupOptions:{output:{manualChunks:{
    vue:['vue','vue-router','pinia'],
    element:['element-plus','@element-plus/icons-vue'],
    charts:['echarts/core','echarts/charts','echarts/components','echarts/renderers'],
    axios:['axios']
  }}}}
});
