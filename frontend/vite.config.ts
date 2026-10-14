import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins:[vue()],
  server:{proxy:{'/api':'http://localhost:8080','/actuator':'http://localhost:8080'}},
  build:{rollupOptions:{output:{manualChunks:{
    vue:['vue','vue-router','pinia'],
    element:['element-plus','@element-plus/icons-vue'],
    charts:['echarts/core','echarts/charts','echarts/components','echarts/renderers'],
    axios:['axios']
  }}}}
});
