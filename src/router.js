import { createRouter, createWebHashHistory } from 'vue-router'
import ConfigView from './views/ConfigView.vue'
import ChannelView from './views/ChannelView.vue'
import PlayerView from './views/PlayerView.vue'

const routes = [
  { path: '/', redirect: '/config' },
  { path: '/config', name: 'Config', component: ConfigView },
  { path: '/channels', name: 'Channels', component: ChannelView },
  { path: '/player', name: 'Player', component: PlayerView },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router