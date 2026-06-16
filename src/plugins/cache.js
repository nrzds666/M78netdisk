const session = {
  getJSON(key) {
    try {
      const raw = sessionStorage.getItem(key)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  },
  setJSON(key, value) {
    sessionStorage.setItem(key, JSON.stringify(value))
  },
  remove(key) {
    sessionStorage.removeItem(key)
  }
}

export default { session }
