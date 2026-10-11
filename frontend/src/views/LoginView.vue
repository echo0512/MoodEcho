<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const formShaking = ref(false)
const formShakeTarget = ref('')

const form = reactive({
  email: '',
  password: '',
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'submit' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'submit' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'submit' }],
}

const triggerFormShake = (target = 'all') => {
  formShakeTarget.value = ''
  formShaking.value = false
  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      formShakeTarget.value = target
      formShaking.value = true
    })
  })
}

const getValidationMessage = (error, fallback = '请检查登录信息') => {
  const firstField = Object.values(error?.fields || {})[0]
  return firstField?.[0]?.message || error?.message || fallback
}

const validateForm = async () => {
  try {
    await formRef.value.validate()
    return true
  } catch (error) {
    triggerFormShake()
    ElMessage.warning(getValidationMessage(error))
    return false
  }
}

const submitLogin = async () => {
  if (!(await validateForm())) {
    return
  }

  loading.value = true

  try {
    await userStore.login({
      email: form.email,
      password: form.password,
    })

    ElMessage.success('登录成功')
    router.push('/home')
  } finally {
    loading.value = false
  }
}

const goRegister = () => {
  router.push('/register')
}

const goResetPassword = () => {
  router.push({
    path: '/reset-password',
    query: form.email ? { email: form.email } : undefined,
  })
}
</script>

<template>
  <main class="auth-page login-page">
    <section class="auth-panel login-panel">
      <div class="auth-copy">
        <p class="eyebrow">欢迎回来</p>
        <h1>MoodEcho</h1>
      </div>

      <div class="login-form-block">
        <h2>登录</h2>

        <el-form
          ref="formRef"
          class="auth-form"
          :class="[{ 'is-shaking': formShaking }, formShakeTarget ? `is-shaking-${formShakeTarget}` : '']"
          :model="form"
          :rules="rules"
          label-position="left"
          hide-required-asterisk
          @animationend="formShaking = false"
          @keyup.enter="submitLogin"
        >
          <el-form-item class="auth-field-email" label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="邮箱" clearable>
              <template #prefix>
                <el-icon><Message /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item class="auth-field-password" label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" show-password>
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <div class="password-actions">
            <el-button link type="primary" @click="goResetPassword">忘记密码？</el-button>
          </div>

          <el-button class="submit-button" type="primary" :loading="loading" @click="submitLogin">
            登录
          </el-button>
        </el-form>

        <div class="bottom-text">
          还没有账号？
          <el-button link type="primary" @click="goRegister">去注册</el-button>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  position: relative;
  overflow: hidden;
  background: #fff;
}

.login-page::before {
  position: absolute;
  inset: 0;
  background: transparent;
  filter: none;
  content: '';
  pointer-events: none;
}

.login-panel {
  position: relative;
  z-index: 1;
  width: min(100%, 1040px);
  grid-template-columns: minmax(0, 1fr) minmax(340px, 400px);
  gap: 72px;
}

.login-form-block {
  width: 100%;
}

.login-form-block h2 {
  margin: 0 0 24px;
  color: var(--paper-ink);
  font-size: 24px;
  font-weight: 720;
  line-height: 1.2;
}

.login-page .auth-copy h1 {
  color: var(--paper-ink);
  font-size: 52px;
  font-weight: 900;
  line-height: 1;
  text-shadow: 0 8px 22px rgba(47, 43, 38, 0.12);
}

.login-page .auth-copy,
.login-form-block {
  animation: auth-section-enter 420ms ease-out both;
}

.login-form-block {
  animation-delay: 80ms;
}

.login-page :deep(.auth-form .el-input__wrapper) {
  transition:
    background-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.login-page :deep(.auth-form .el-input__wrapper:hover),
.login-page :deep(.auth-form .el-input__wrapper.is-focus) {
  transform: translateY(-1px);
}

.login-page :deep(.auth-form .el-input__wrapper.is-focus) {
  box-shadow: 0 10px 24px -18px rgba(47, 43, 38, 0.48);
}

.login-page :deep(.el-button) {
  transition:
    background-color 180ms ease,
    color 180ms ease,
    opacity 180ms ease,
    transform 180ms ease;
}

.login-page :deep(.el-button:not(.is-disabled):active) {
  transform: translateY(1px) scale(0.98);
}

.login-page :deep(.el-button.is-link:hover) {
  transform: translateY(-1px);
}

@keyframes auth-section-enter {
  from {
    opacity: 0;
    transform: translateY(12px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-page :deep(.auth-form .el-form-item__label) {
  display: none;
}

.login-page :deep(.auth-form .el-form-item__content) {
  width: 100%;
}

.password-actions {
  display: flex;
  justify-content: flex-end;
  margin: -12px 0 4px;
}

@media (max-width: 760px) {
  .login-page {
    align-items: stretch;
    padding: max(42px, env(safe-area-inset-top, 0px)) 28px max(34px, env(safe-area-inset-bottom, 0px));
    background: #F7F5F2;
  }

  .login-page::before {
    display: none;
  }

  .login-panel {
    display: flex;
    min-height: calc(100dvh - 86px);
    width: min(100%, 420px);
    flex-direction: column;
    justify-content: center;
    gap: clamp(30px, 7dvh, 56px);
    margin: 0 auto;
  }

  .login-page .auth-copy {
    padding-top: 0;
    text-align: left;
  }

  .login-page .eyebrow {
    margin-bottom: 8px;
  }

  .login-page .auth-copy h1 {
    margin: 0;
    font-size: 42px;
    line-height: 1;
  }

  .login-form-block {
    padding-bottom: 8px;
  }

  .login-form-block h2 {
    margin-bottom: 18px;
    text-align: left;
  }

  .login-page :deep(.auth-form .el-form-item) {
    margin-bottom: 14px;
  }

  .login-page :deep(.auth-form .el-form-item__content) {
    width: 100%;
    line-height: 45px;
  }

  .login-page :deep(.auth-form .el-input__wrapper) {
    min-height: 45px;
    padding: 0 18px;
    border: 0;
    border-radius: 5px !important;
    background: #E4E0DB;
    box-shadow: none;
  }

  .login-page :deep(.auth-form .el-input__wrapper:hover),
  .login-page :deep(.auth-form .el-input__wrapper.is-focus) {
    border: 0;
    background: #E4E0DB;
    box-shadow: none;
  }

  .login-page :deep(.auth-form .el-input__inner) {
    min-height: 45px;
    color: var(--paper-ink);
    font-size: 16px;
  }

  .login-page :deep(.auth-form .el-input__prefix) {
    color: rgba(47, 43, 38, 0.68);
    font-size: 18px;
  }

  .login-page :deep(.auth-form .el-input__inner::placeholder) {
    color: rgba(47, 43, 38, 0.58);
  }

  .login-page :deep(.auth-form .el-form-item.is-error .el-form-item__content .el-input__wrapper),
  .login-page :deep(.auth-form .el-form-item.is-error .el-form-item__content .el-input__wrapper:hover),
  .login-page :deep(.auth-form .el-form-item.is-error .el-form-item__content .el-input__wrapper.is-focus) {
    border: 0;
    background: #E6D9D8;
  }

  .password-actions {
    justify-content: flex-end;
    margin: -6px 0 8px;
  }

  .login-page .submit-button {
    min-height: 45px;
    margin-top: 8px;
    border-radius: 5px;
    font-size: 20px;
  }

  .login-page .bottom-text {
    justify-content: center;
    margin-top: 18px;
    text-align: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-page .auth-copy,
  .login-form-block {
    animation: none;
  }

  .login-page :deep(.auth-form .el-input__wrapper),
  .login-page :deep(.el-button) {
    transition: none;
  }

  .login-page :deep(.auth-form .el-input__wrapper:hover),
  .login-page :deep(.auth-form .el-input__wrapper.is-focus),
  .login-page :deep(.el-button:not(.is-disabled):active),
  .login-page :deep(.el-button.is-link:hover) {
    transform: none;
  }
}

@media (max-width: 760px) and (max-height: 680px) {
  .login-page {
    padding-top: max(28px, env(safe-area-inset-top, 0px));
    padding-bottom: max(28px, env(safe-area-inset-bottom, 0px));
  }

  .login-panel {
    gap: 24px;
  }
}
</style>
