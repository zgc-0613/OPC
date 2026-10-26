import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/global.css'
import './styles/prisma.css'
import './styles/atlas.css'
import './styles/analysis-refresh.css'
import './styles/university-refresh.css'

createApp(App).use(router).mount('#app')
