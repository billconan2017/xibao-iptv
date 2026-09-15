<template>
  <div id="app-root" class="app-root">
    <router-view />
  </div>
</template>

<script>
export default {
  name: 'App',
  mounted() {
    // TV/遥控器方向键导航
    document.addEventListener('keydown', (e) => {
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', 'Back'].includes(e.key)) {
        e.preventDefault()
      }
    })
  }
}
</script>

<style scoped>
.app-root {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #0a0a0a;
}
</style>