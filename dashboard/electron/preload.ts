const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('ipcRenderer', {
  on(channel: string, listener: (...args: any[]) => void) {
    return ipcRenderer.on(channel, (event, ...args) => listener(event, ...args))
  },
  off(channel: string, ...args: any[]) {
    return ipcRenderer.off(channel, ...args)
  },
  send(channel: string, ...args: any[]) {
    return ipcRenderer.send(channel, ...args)
  },
  invoke(channel: string, ...args: any[]) {
    return ipcRenderer.invoke(channel, ...args)
  },
})