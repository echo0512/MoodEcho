<script setup>
import { onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Key, Lock, Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { resetPasswordApi, sendResetPasswordCodeApi } from '@/api/auth'
import PasswordRequirements from '@/components/PasswordRequirements.vue'
import { createPasswordRules } from '@/utils/password'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const formShaking = ref(false)
const formShakeTarget = ref('')
let timer = null

const form = reactive({
  email: typeof route.query.email === 'string' ? route.query.email : '',
  code: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }

  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }

  callback()
}

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'submit' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'submit' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'submit' },
    { len: 6, message: '验证码应为 6 位', trigger: 'submit' },
  ],
  password: createPasswordRules('请输入新密码'),
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'submit' }],
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

const getValidationMessage = (error, fallback = '请检查重置信息') => {
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

const startCountdown = () => {
  countdown.value = 60

  timer = window.setInterval(() => {
    countdown.value -= 1

    if (countdown.value <= 0) {
      window.clearInterval(timer)
      timer = null
    }
  }, 1000)
}

const sendCode = async () => {
  if (countdown.value > 0) {
    return
  }

  try {
    await formRef.value.validateField('email')
  } catch (error) {
    triggerFormShake('email')
    ElMessage.warning(getValidationMessage(error, '请先填写正确的邮箱'))
    return
  }

  sendingCode.value = true

  try {
    await sendResetPasswordCodeApi({
      email: form.email,
    })

    ElMessage.success('验证码已发送，请查看邮箱')
    startCountdown()
  } finally {
    sendingCode.value = false
  }
}

const submitResetPassword = async () => {
  if (!(await validateForm())) {
    return
  }

  loading.value = true

  try {
    await resetPasswordApi({
      email: form.email,
      code: form.code,
      password: form.password,
      confirmPassword: form.confirmPassword,
    })

    ElMessage.success('密码重置成功，请使用新密码登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}

const goLogin = () => {
  router.push('/login')
}

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <main class="auth-page login-page">
    <section class="auth-panel login-panel">
      <div class="auth-copy">
        <p class="eyebrow">欢迎回来</p>
        <h1>MoodEcho</h1>
      </div>

      <div class="login-form-block">
        <h2>忘记密码</h2>

        <el-form
          ref="formRef"
          class="auth-form"
          :class="[{ 'is-shaking': formShaking }, formShakeTarget ? `is-shaking-${formShakeTarget}` : '']"
          :model="form"
          :rules="rules"
          label-position="left"
          hide-required-asterisk
          @animationend="formShaking = false"
          @keyup.enter="submitResetPassword"
        >
          <el-form-item class="auth-field-email" label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="邮箱" clearable>
              <template #prefix>
                <el-icon><Message /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item class="auth-field-code" label="验证码" prop="code">
            <div class="code-row">
              <el-input v-model="form.code" placeholder="验证码" clearable>
                <template #prefix>
                  <el-icon><Key /></el-icon>
                </template>
              </el-input>
              <el-button :disabled="countdown > 0" :loading="sendingCode" @click="sendCode">
                {{ countdown > 0 ? `${countdown}s 后重试` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item class="auth-field-password" label="新密码" prop="password">
            <div class="password-field">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="新密码"
                maxlength="64"
                show-password
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
              <PasswordRequirements :password="form.password" />
            </div>
          </el-form-item>

          <el-form-item class="auth-field-confirm-password" label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              maxlength="64"
              show-password
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-button class="submit-button" type="primary" :loading="loading" @click="submitResetPassword">
            确认重置
          </el-button>
        </el-form>

        <div class="bottom-text">
          想起密码了？
          <el-button link type="primary" @click="goLogin">返回登录</el-button>
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

.login-page :deep(.auth-form .el-input__wrapper),
.login-page :deep(.auth-form .code-row .el-button) {
  transition:
    background-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.login-page :deep(.auth-form .el-input__wrapper:hover),
.login-page :deep(.auth-form .el-input__wrapper.is-focus),
.login-page :deep(.auth-form .code-row .el-button:not(.is-disabled):hover) {
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

@media (max-width: 760px) {
  .login-page {
    align-items: stretch;
    overflow-y: auto;
    padding: max(32px, env(safe-area-inset-top, 0px)) 28px max(30px, env(safe-area-inset-bottom, 0px));
    background: #F7F5F2;
  }

  .login-page::before {
    display: none;
  }

  .login-panel {
    display: flex;
    width: min(100%, 420px);
    flex-direction: column;
    justify-content: center;
    gap: clamp(20px, 4dvh, 34px);
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

  .login-page .code-row {
    grid-template-columns: minmax(0, 1fr) 112px;
  }

  .login-page :deep(.auth-form .code-row .el-button) {
    min-height: 45px;
    border: 0;
    border-radius: 5px;
    background: #E4E0DB;
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
  .login-page :deep(.auth-form .code-row .el-button),
  .login-page :deep(.el-button) {
    transition: none;
  }

  .login-page :deep(.auth-form .el-input__wrapper:hover),
  .login-page :deep(.auth-form .el-input__wrapper.is-focus),
  .login-page :deep(.auth-form .code-row .el-button:not(.is-disabled):hover),
  .login-page :deep(.el-button:not(.is-disabled):active),
  .login-page :deep(.el-button.is-link:hover) {
    transform: none;
  }
}
</style>
