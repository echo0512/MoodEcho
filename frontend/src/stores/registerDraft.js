import { defineStore } from 'pinia'

const getEmptyDraft = () => ({
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
  agreementChecked: false,
})

export const useRegisterDraftStore = defineStore('registerDraft', {
  state: () => ({
    draft: getEmptyDraft(),
  }),

  actions: {
    save(payload) {
      this.draft = {
        ...this.draft,
        ...payload,
      }
    },

    clear() {
      this.draft = getEmptyDraft()
    },
  },
})
