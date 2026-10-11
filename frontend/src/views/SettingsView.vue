<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ActionIcon from '@/components/ActionIcon.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  nickname: '',
})

const currentUser = computed(() => userStore.userInfo)

watch(
  () => userStore.userInfo,
  (userInfo) => {
    if (userInfo) {
      form.nickname = userInfo.nickname || ''
    }
  },
  {
    immediate: true,
  },
)

const rules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 10, message: '昵称长度应为 2 到 10 个字符', trigger: 'blur' },
  ],
}

const submit = async () => {
  await formRef.value.validate()

  const nickname = form.nickname.trim()

  if (!nickname) {
    ElMessage.error('昵称不能为空')
    return
  }

  if (nickname === userStore.userInfo?.nickname) {
    ElMessage.info('昵称没有变化')
    return
  }

  loading.value = true

  try {
    await userStore.updateNickname(nickname)
    form.nickname = nickname
    ElMessage.success('昵称修改成功')
  } finally {
    loading.value = false
  }
}

const goHome = () => {
  router.push('/home')
}
</script>

<template>
  <main class="settings-page">
    <section class="settings-shell">
      <el-card class="settings-card" shadow="never">
        <header class="page-header">
          <div>
            <p class="eyebrow">Account Settings</p>
            <h1>个人设置</h1>
            <p>管理你的账号展示信息。</p>
          </div>

          <el-button aria-label="返回首页" title="返回首页" @click="goHome">
            <ActionIcon name="arrow-left" />
          </el-button>
        </header>

        <div v-if="currentUser" class="user-summary">
          <div class="avatar-placeholder">
            {{ currentUser.nickname?.slice(0, 1) || 'M' }}
          </div>

          <div class="summary-copy">
            <div class="summary-name">{{ currentUser.nickname || '未设置昵称' }}</div>
            <div class="summary-email">{{ currentUser.email }}</div>
          </div>
        </div>

        <el-divider />

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="邮箱">
            <el-input :model-value="currentUser?.email" disabled />
          </el-form-item>

          <el-form-item label="昵称" prop="nickname">
            <el-input
              v-model="form.nickname"
              maxlength="10"
              show-word-limit
              placeholder="请输入新的昵称"
              clearable
            />
          </el-form-item>

          <div class="tips">昵称用于你自己页面中的展示，可以重复，不影响登录。</div>

          <div class="actions">
            <el-button @click="goHome">取消</el-button>
            <el-button type="primary" :loading="loading" @click="submit">保存修改</el-button>
          </div>
        </el-form>
      </el-card>
    </section>
  </main>
</template>

<style scoped>
.settings-page {
  display: grid;
  gap: 20px;
}

.settings-shell {
  width: min(100%, 760px);
  margin: 0 auto;
}

.settings-card {
  border: 0;
  border-radius: 10px;
  background: transparent;
  box-shadow: none;
}

.settings-card :deep(.el-card__body) {
  padding: 30px 34px 34px;
}

.page-header {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 6px 0 8px;
  color: var(--paper-ink);
  font-size: 30px;
  font-weight: 740;
  line-height: 1.15;
}

.page-header p {
  margin: 0;
  color: var(--paper-muted);
}

.user-summary {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 0 0 18px;
  border: 0;
  border-radius: 0;
  border-bottom: 1px solid rgba(138, 117, 99, 0.1);
  background: transparent;
}

.avatar-placeholder {
  display: flex;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #DED8D1;
  color: var(--paper-accent-dark);
  font-size: 20px;
  font-weight: 700;
}

.summary-copy {
  min-width: 0;
}

.summary-name {
  overflow-wrap: anywhere;
  color: var(--paper-ink);
  font-size: 18px;
  font-weight: 700;
}

.summary-email {
  margin-top: 4px;
  overflow-wrap: anywhere;
  color: var(--paper-muted);
  font-size: 14px;
}

.tips {
  margin: -4px 0 20px;
  color: var(--paper-muted);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .settings-page {
    gap: 16px;
  }

  .page-header {
    display: block;
  }

  .page-header .el-button {
    margin-top: 18px;
  }

  .actions {
    flex-direction: column-reverse;
  }

  .settings-card :deep(.el-card__body) {
    padding: 24px 18px;
  }

  .actions .el-button {
    width: 100%;
  }
}
</style>
