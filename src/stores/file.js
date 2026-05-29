import { defineStore } from 'pinia'

export const useFileStore = defineStore('file', {
  state: () => ({
    currentFolderId: null,
    currentPath: [],
    selectedFiles: []
  }),

  getters: {
    /**
     * Breadcrumb-friendly path segments
     */
    breadcrumbPath: (state) => state.currentPath,

    /**
     * Whether any files are currently selected
     */
    hasSelection: (state) => state.selectedFiles.length > 0
  },

  actions: {
    /**
     * Navigate into a folder
     * @param {string} folderId
     * @param {string} folderName
     */
    enterFolder(folderId, folderName) {
      this.currentPath.push({ id: folderId, name: folderName })
      this.currentFolderId = folderId
      this.selectedFiles = []
    },

    /**
     * Navigate back to a specific ancestor in breadcrumb
     * @param {number} index - target index in currentPath
     */
    navigateTo(index) {
      if (index < 0) {
        // root level
        this.currentPath = []
        this.currentFolderId = null
      } else {
        this.currentPath = this.currentPath.slice(0, index + 1)
        this.currentFolderId = this.currentPath[index]?.id || null
      }
      this.selectedFiles = []
    },

    /**
     * Go up one directory level
     */
    goUp() {
      const parentIndex = this.currentPath.length - 2
      this.navigateTo(parentIndex)
    },

    /**
     * Update selected files
     * @param {Array} files - array of file objects with id
     */
    setSelectedFiles(files) {
      this.selectedFiles = files
    },

    /**
     * Clear the selection
     */
    clearSelection() {
      this.selectedFiles = []
    },

    /**
     * Reset store state (on logout, etc.)
     */
    reset() {
      this.currentFolderId = null
      this.currentPath = []
      this.selectedFiles = []
    }
  }
})
