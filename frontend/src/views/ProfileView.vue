<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getDiaryListApi } from '@/api/diary'
import { getMyResonancePostsApi, withdrawResonancePostApi } from '@/api/resonance'
import ActionIcon from '@/components/ActionIcon.vue'
import DiaryThumbnail from '@/components/DiaryThumbnail.vue'
import DiaryUnsavedConfirm from '@/components/DiaryUnsavedConfirm.vue'
import PasswordRequirements from '@/components/PasswordRequirements.vue'
import DiaryDetailView from '@/views/diary/DiaryDetailView.vue'
import DiaryEditView from '@/views/diary/DiaryEditView.vue'
import { changePasswordApi, deleteAccountApi, getCurrentUserStatsApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { getDiaryPlainText } from '@/utils/diaryContent'
import { createPasswordRules } from '@/utils/password'

const userStore = useUserStore()
const router = useRouter()

const editingNickname = ref(false)
const savingNickname = ref(false)
const statsLoading = ref(false)
const nicknameInputRef = ref(null)
const stats = ref(null)
const myDrawerVisible = ref(false)
const myPosts = ref([])
const myPostsLoading = ref(false)
const selectedMyPost = ref(null)
const pendingWithdrawPost = ref(null)
const diaryDrawerVisible = ref(false)
const myDiaries = ref([])
const myDiariesLoading = ref(false)
const selectedDrawerDiary = ref(null)
const diaryDialogVisible = ref(false)
const diaryDialogMode = ref('detail')
const passwordDialogVisible = ref(false)
const passwordFormRef = ref(null)
const changingPassword = ref(false)
const deleteAccountDialogVisible = ref(false)
const deletingAccount = ref(false)
const deleteAccountPassword = ref('')
const deleteAccountConfirmVisible = ref(false)

const form = reactive({
  nickname: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }

  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
    return
  }

  callback()
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'submit' }],
  newPassword: createPasswordRules('请输入新密码'),
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'submit' }],
}

const currentUser = computed(() => userStore.userInfo)
const displayName = computed(() => currentUser.value?.nickname || 'MoodEcho用户')
const nicknameLengthText = computed(() => `${form.nickname.length}/10`)

const metricItems = computed(() => [
  {
    label: '累计日记',
    value: stats.value?.totalDiaryCount ?? 0,
    unit: '篇',
  },
  {
    label: '记录天数',
    value: stats.value?.totalRecordDays ?? 0,
    unit: '天',
  },
  {
    label: '记录字数',
    value: stats.value?.totalWordCount ?? 0,
    unit: '字',
  },
  {
    label: '最早记录',
    value: stats.value?.firstRecordDate || '暂无',
    unit: '',
  },
])

const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const weekdayNames = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

const groupedMyDiaries = computed(() => {
  const sortedDiaries = [...myDiaries.value].sort((a, b) => {
    const recordDateSort = String(b.recordDate || '').localeCompare(String(a.recordDate || ''))

    if (recordDateSort !== 0) {
      return recordDateSort
    }

    return String(b.createdAt || b.updatedAt || '').localeCompare(String(a.createdAt || a.updatedAt || ''))
  })
  const groups = []
  const groupMap = new Map()

  sortedDiaries.forEach((diary) => {
    const date = parseDiaryDate(diary.recordDate)
    const monthLabel = date
      ? `${date.getFullYear()} ${monthNames[date.getMonth()]}`
      : '未知日期'
    const key = date ? `${date.getFullYear()}-${date.getMonth()}` : 'unknown'

    if (!groupMap.has(key)) {
      const group = {
        key,
        label: monthLabel,
        diaries: [],
      }

      groupMap.set(key, group)
      groups.push(group)
    }

    groupMap.get(key).diaries.push(diary)
  })

  return groups
})

const profileSections = computed(() => [
  {
    title: '我的记录',
    items: [
      {
        title: '全部日记',
        icon: 'book-open',
        action: openAllDiaries,
      },
      {
        title: '全部投递',
        icon: 'message-circle',
        action: openMyPosts,
      },
    ],
  },
  {
    title: '账号设置',
    items: [
      {
        title: '修改密码',
        icon: 'lock',
        action: openChangePassword,
      },
      {
        title: '退出登录',
        icon: 'log-out',
        action: logout,
      },
      {
        title: '注销账号',
        icon: 'user-x',
        danger: true,
        action: openDeleteAccount,
      },
    ],
  },
])

watch(
  () => userStore.userInfo,
  (userInfo) => {
    if (!editingNickname.value) {
      form.nickname = userInfo?.nickname || ''
    }
  },
  {
    immediate: true,
  },
)

const loadStats = async () => {
  statsLoading.value = true

  try {
    stats.value = await getCurrentUserStatsApi()
  } finally {
    statsLoading.value = false
  }
}

const startNicknameEdit = async () => {
  form.nickname = currentUser.value?.nickname || ''
  editingNickname.value = true

  await Promise.resolve()
  nicknameInputRef.value?.focus()
}

const cancelNicknameEdit = () => {
  form.nickname = currentUser.value?.nickname || ''
  editingNickname.value = false
}

const confirmNicknameEdit = async () => {
  const nickname = form.nickname.trim()

  if (!nickname) {
    ElMessage.error('昵称不能为空')
    return
  }

  if (nickname.length < 2 || nickname.length > 10) {
    ElMessage.error('昵称长度应为 2 到 10 个字符')
    return
  }

  if (nickname === currentUser.value?.nickname) {
    editingNickname.value = false
    return
  }

  savingNickname.value = true

  try {
    await userStore.updateNickname(nickname)
    form.nickname = nickname
    editingNickname.value = false
    ElMessage.success('昵称修改成功')
  } finally {
    savingNickname.value = false
  }
}

const loadMyPosts = async () => {
  myPostsLoading.value = true

  try {
    myPosts.value = await getMyResonancePostsApi()
  } finally {
    myPostsLoading.value = false
  }
}

function openMyPosts() {
  myDrawerVisible.value = true
  loadMyPosts()
}

const openMyPostDetail = (post) => {
  selectedMyPost.value = post
}

const closeMyPostDetail = () => {
  selectedMyPost.value = null
}

const resetMyPostsDrawer = () => {
  closeMyPostDetail()
}

function openChangePassword() {
  passwordDialogVisible.value = true
}

function openDeleteAccount() {
  deleteAccountDialogVisible.value = true
}

function logout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

const loadMyDiaries = async ({ silent = false } = {}) => {
  if (!silent) {
    myDiariesLoading.value = true
  }

  try {
    myDiaries.value = await getDiaryListApi()
    return myDiaries.value
  } finally {
    if (!silent) {
      myDiariesLoading.value = false
    }
  }
}

function openAllDiaries() {
  diaryDrawerVisible.value = true
  loadMyDiaries()
}

const openPrivacy = (tab) => {
  router.push({
    path: '/profile/privacy',
    query: {
      tab,
      from: 'profile',
    },
  })
}

const openDiary = (diary) => {
  selectedDrawerDiary.value = diary
  diaryDialogMode.value = 'detail'
  diaryDialogVisible.value = true
}

const closeDiaryDrawer = () => {
  diaryDrawerVisible.value = false
}

const resetDiaryDrawer = () => {
  selectedDrawerDiary.value = null
  diaryDialogVisible.value = false
  diaryDialogMode.value = 'detail'
}

const handleDiaryDrawerClose = () => {
  closeDiaryDrawer()
}

const closeDiaryDialog = () => {
  diaryDialogVisible.value = false
}

const resetDiaryDialog = () => {
  selectedDrawerDiary.value = null
  diaryDialogMode.value = 'detail'
}

const openDrawerDiaryEdit = () => {
  diaryDialogMode.value = 'edit'
}

const backToDrawerDiaryDetail = () => {
  diaryDialogMode.value = 'detail'
}

const handleDrawerDiarySaved = async () => {
  const updatedDiaries = await loadMyDiaries({ silent: true })
  const updatedDiary = updatedDiaries?.find((diary) => String(diary.id) === String(selectedDrawerDiary.value?.id))

  if (updatedDiary) {
    selectedDrawerDiary.value = updatedDiary
  }

  diaryDialogMode.value = 'detail'
}

const handleDrawerDiaryDeleted = async () => {
  closeDiaryDialog()
  await loadMyDiaries({ silent: true })
}

const parseDiaryDate = (value) => {
  if (!value) return null

  const [year, month, day] = String(value).split('-').map(Number)

  if (!year || !month || !day) {
    return null
  }

  return new Date(year, month - 1, day)
}

const getDiaryDay = (diary) => {
  const date = parseDiaryDate(diary.recordDate)

  return date ? String(date.getDate()).padStart(2, '0') : '--'
}

const getDiaryWeekday = (diary) => {
  const date = parseDiaryDate(diary.recordDate)

  return date ? weekdayNames[date.getDay()] : ''
}

const getFirstDiaryImage = (diary) => {
  if (!diary.images || diary.images.length === 0) {
    return null
  }

  return diary.images[0]
}

const resetDeleteAccountForm = () => {
  deleteAccountPassword.value = ''
  deleteAccountConfirmVisible.value = false
}

const submitDeleteAccount = async () => {
  if (!deleteAccountPassword.value) {
    ElMessage.error('请输入当前密码')
    return
  }

  deleteAccountConfirmVisible.value = true
}

const cancelDeleteAccountConfirm = () => {
  deleteAccountConfirmVisible.value = false
}

const confirmDeleteAccount = async () => {
  if (deletingAccount.value) {
    return
  }

  deletingAccount.value = true

  try {
    await deleteAccountApi({
      password: deleteAccountPassword.value,
    })
    userStore.logout()
    deleteAccountConfirmVisible.value = false
    deleteAccountDialogVisible.value = false
    ElMessage.success('账号已注销')
    await router.push('/login')
  } finally {
    deletingAccount.value = false
  }
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

const submitPasswordChange = async () => {
  await passwordFormRef.value.validate()

  changingPassword.value = true

  try {
    await changePasswordApi({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword,
    })

    passwordDialogVisible.value = false
    ElMessage.success('密码修改成功')
  } finally {
    changingPassword.value = false
  }
}

const withdrawPost = (post) => {
  pendingWithdrawPost.value = post
}

const cancelWithdrawPost = () => {
  pendingWithdrawPost.value = null
}

const confirmWithdrawPost = async () => {
  const post = pendingWithdrawPost.value

  if (!post) {
    return
  }

  pendingWithdrawPost.value = null
  await withdrawResonancePostApi(post.id)
  myPosts.value = myPosts.value.filter((item) => item.id !== post.id)
  if (String(selectedMyPost.value?.id) === String(post.id)) {
    closeMyPostDetail()
  }
  ElMessage.success('已撤回')
}

const handleProfileKeydown = (event) => {
  if (event.key === 'Escape' && selectedMyPost.value) {
    closeMyPostDetail()
  }
}

const getDiarySummary = (diary) => {
  return getDiaryPlainText(diary.content || '')
}

onMounted(() => {
  loadStats()
  window.addEventListener('keydown', handleProfileKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleProfileKeydown)
})
</script>

<template>
  <main class="profile-page">
    <section class="identity-panel" aria-label="个人资料">
      <div class="identity-copy">
        <div class="nickname-row">
          <template v-if="editingNickname">
            <input
              ref="nicknameInputRef"
              v-model="form.nickname"
              class="nickname-input"
              maxlength="10"
              aria-label="修改昵称"
              @keydown.enter.prevent="confirmNicknameEdit"
              @keydown.esc.prevent="cancelNicknameEdit"
            />
            <span class="nickname-count">{{ nicknameLengthText }}</span>
            <div class="nickname-actions">
              <button
                class="icon-button confirm"
                type="button"
                aria-label="确认修改昵称"
                :disabled="savingNickname"
                @click="confirmNicknameEdit"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M20 6 9 17l-5-5" />
                </svg>
              </button>
              <button
                class="icon-button cancel"
                type="button"
                aria-label="取消修改昵称"
                :disabled="savingNickname"
                @click="cancelNicknameEdit"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m18 6-12 12" />
                  <path d="m6 6 12 12" />
                </svg>
              </button>
            </div>
          </template>

          <template v-else>
            <h2>{{ displayName }}</h2>
            <button class="icon-button edit" type="button" aria-label="修改昵称" @click="startNicknameEdit">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 20h9" />
                <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
              </svg>
            </button>
          </template>
        </div>

        <p class="email">{{ currentUser?.email || '邮箱未获取' }}</p>
      </div>

      <section class="metrics-strip" aria-label="记录统计">
        <div v-for="item in metricItems" :key="item.label" class="metric-item">
          <span class="metric-label">{{ item.label }}</span>
          <strong>{{ statsLoading ? '...' : item.value }}</strong>
          <span v-if="item.unit" class="metric-unit">{{ item.unit }}</span>
        </div>
      </section>
    </section>

    <section class="settings-columns" aria-label="个人主页设置入口">
      <article v-for="section in profileSections" :key="section.title" class="settings-column">
        <header class="column-header">
          <h3>{{ section.title }}</h3>
        </header>

        <button
          v-for="item in section.items"
          :key="item.title"
          class="setting-row"
          :class="{ inactive: item.inactive, danger: item.danger }"
          type="button"
          :aria-disabled="item.inactive ? 'true' : 'false'"
          @click="item.action?.()"
        >
          <span class="row-icon" aria-hidden="true">
            <ActionIcon :name="item.icon" />
          </span>
          <span class="row-copy">
            <span class="row-title">{{ item.title }}</span>
          </span>
          <span class="row-arrow" aria-hidden="true"></span>
        </button>
      </article>
    </section>

    <nav class="profile-legal-links" aria-label="隐私与协议">
      <button type="button" @click="openPrivacy('terms')">《用户协议》</button>
      <button type="button" @click="openPrivacy('privacy')">《隐私政策》</button>
    </nav>

    <el-dialog
      v-model="passwordDialogVisible"
      class="account-action-dialog password-action-dialog"
      title="修改密码"
      width="min(92vw, 480px)"
      @closed="resetPasswordForm"
    >
      <el-form
        ref="passwordFormRef"
        class="auth-form"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
        hide-required-asterisk
        @keyup.enter="submitPasswordChange"
      >
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <div class="password-field">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              placeholder="请输入新密码"
              maxlength="64"
              show-password
            />
            <PasswordRequirements :password="passwordForm.newPassword" />
          </div>
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            maxlength="64"
            show-password
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingPassword" @click="submitPasswordChange">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="deleteAccountDialogVisible"
      class="account-action-dialog delete-account-dialog"
      title="注销账号"
      width="min(92vw, 480px)"
      @closed="resetDeleteAccountForm"
    >
      <p class="delete-account-warning">注销后，日记、图片和匿名投递都会永久删除，且无法恢复。</p>
      <el-input
        v-model="deleteAccountPassword"
        type="password"
        placeholder="请输入当前密码"
        show-password
        @keyup.enter="submitDeleteAccount"
      />

      <template #footer>
        <el-button @click="deleteAccountDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="deletingAccount" @click="submitDeleteAccount">永久注销</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="myDrawerVisible"
      class="my-resonance-drawer"
      title="全部投递"
      size="min(92vw, 520px)"
      @closed="resetMyPostsDrawer"
    >
      <el-skeleton v-if="myPostsLoading" :rows="6" animated />

      <div v-else-if="myPosts.length === 0" class="my-empty">
        <p>你还没有已发布的匿名投递。</p>
      </div>

      <div v-else class="my-post-list">
        <article
          v-for="post in myPosts"
          :key="post.id"
          class="my-post"
          role="button"
          tabindex="0"
          aria-label="查看完整投递"
          @click="openMyPostDetail(post)"
          @keyup.enter.self="openMyPostDetail(post)"
          @keyup.space.self.prevent="openMyPostDetail(post)"
        >
          <p>{{ post.content }}</p>

          <div class="my-post-footer">
            <span v-if="post.topicName">{{ post.topicName }}</span>
            <span v-else aria-hidden="true"></span>
            <button type="button" @click.stop="withdrawPost(post)">撤回</button>
          </div>
        </article>
      </div>
    </el-drawer>

    <Teleport to="body">
      <transition name="echo-detail">
        <div v-if="selectedMyPost" class="echo-detail-backdrop" @click.self="closeMyPostDetail">
          <article class="echo-detail-card" role="dialog" aria-modal="true" aria-label="完整投递">
            <button class="echo-detail-close" type="button" aria-label="关闭完整投递" @click="closeMyPostDetail">
              <span aria-hidden="true"></span>
              <span aria-hidden="true"></span>
            </button>
            <div class="echo-detail-body">
              <p>{{ selectedMyPost.content }}</p>
              <span v-if="selectedMyPost.topicName">{{ selectedMyPost.topicName }}</span>
            </div>
          </article>
        </div>
      </transition>
    </Teleport>

    <DiaryUnsavedConfirm
      :visible="Boolean(pendingWithdrawPost)"
      title="确定撤回这段投递？"
      message="撤回后不会再出现在共鸣广场，也不能重新发布。"
      stay-text="取消"
      confirm-text="撤回"
      danger
      @stay="cancelWithdrawPost"
      @confirm="confirmWithdrawPost"
    />

    <DiaryUnsavedConfirm
      :visible="deleteAccountConfirmVisible"
      title="确定永久注销账号？"
      message="注销后，日记、图片和匿名投递都会永久删除，无法恢复。"
      stay-text="取消"
      confirm-text="永久注销"
      danger
      @stay="cancelDeleteAccountConfirm"
      @confirm="confirmDeleteAccount"
    />

    <el-drawer
      v-model="diaryDrawerVisible"
      class="my-diary-drawer"
      size="min(92vw, 520px)"
      :show-close="false"
      @closed="resetDiaryDrawer"
    >
      <template #header>
        <div class="drawer-header">
          <h3>全部日记</h3>
          <button
            class="drawer-back-button"
            type="button"
            aria-label="关闭全部日记"
            @click="handleDiaryDrawerClose"
          >
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
          </button>
        </div>
      </template>

      <el-skeleton v-if="myDiariesLoading" :rows="6" animated />

      <div v-else-if="myDiaries.length === 0" class="my-empty">
        <p>你还没有写下日记。</p>
      </div>

      <div v-else class="drawer-diary-timeline">
        <section v-for="group in groupedMyDiaries" :key="group.key" class="drawer-diary-month-group">
          <h4 class="drawer-diary-month">{{ group.label }}</h4>

          <div class="drawer-diary-list">
            <article
              v-for="diary in group.diaries"
              :key="diary.id"
              class="drawer-diary-card"
              :class="{ 'has-image': getFirstDiaryImage(diary), 'has-title': diary.title }"
              role="button"
              tabindex="0"
              :aria-label="`查看 ${diary.recordDate} 的日记`"
              @click="openDiary(diary)"
              @keyup.enter="openDiary(diary)"
              @keyup.space.prevent="openDiary(diary)"
            >
              <div class="drawer-diary-date-block">
                <strong>{{ getDiaryDay(diary) }}</strong>
                <span>{{ getDiaryWeekday(diary) }}</span>
              </div>

              <div class="drawer-diary-copy">
                <h5 v-if="diary.title">{{ diary.title }}</h5>
                <p>{{ getDiarySummary(diary) || '这篇日记只有图片。' }}</p>
              </div>

              <DiaryThumbnail v-if="getFirstDiaryImage(diary)" :image="getFirstDiaryImage(diary)" />
            </article>
          </div>
        </section>
      </div>
    </el-drawer>

    <el-dialog
      v-model="diaryDialogVisible"
      class="drawer-diary-dialog"
      width="min(94vw, 690px)"
      :show-close="false"
      :close-on-click-modal="false"
      append-to-body
      destroy-on-close
      @closed="resetDiaryDialog"
    >
      <DiaryDetailView
        v-if="selectedDrawerDiary && diaryDialogMode === 'detail'"
        :key="`detail-${selectedDrawerDiary.id}`"
        embedded
        :diary-id="selectedDrawerDiary.id"
        :initial-diary="selectedDrawerDiary"
        @close="closeDiaryDialog"
        @edit="openDrawerDiaryEdit"
        @deleted="handleDrawerDiaryDeleted"
      />

      <DiaryEditView
        v-else-if="selectedDrawerDiary"
        :key="`edit-${selectedDrawerDiary.id}`"
        embedded
        :diary-id="selectedDrawerDiary.id"
        @close="backToDrawerDiaryDetail"
        @saved="handleDrawerDiarySaved"
      />
    </el-dialog>
  </main>
</template>

<style scoped>
.profile-page {
  display: grid;
  height: calc(100dvh - 150px);
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: visible;
}

.identity-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 0;
  border-bottom: 1px solid rgba(138, 117, 99, 0.14);
}

.identity-copy {
  flex: 1 1 auto;
  min-width: 0;
}

.nickname-row {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: 8px;
}

.nickname-row h2 {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--paper-ink);
  font-size: 26px;
  font-weight: 760;
  line-height: 1.2;
}

.email {
  margin: 2px 0 0;
  overflow-wrap: anywhere;
  color: var(--paper-muted);
  font-size: 15px;
  line-height: 1.5;
}

.nickname-input {
  width: min(320px, 58vw);
  min-height: 44px;
  border: 0;
  border-bottom: 1px solid rgba(138, 117, 99, 0.42);
  background: rgba(255, 254, 250, 0.76);
  color: var(--paper-ink);
  font-size: 24px;
  font-weight: 720;
  line-height: 1.2;
  outline: none;
  padding: 4px 2px;
}

.nickname-input:focus {
  border-bottom-color: var(--paper-accent-dark);
  box-shadow: 0 3px 0 rgba(138, 117, 99, 0.1);
}

.nickname-count {
  flex: 0 0 auto;
  color: var(--paper-muted);
  font-size: 13px;
  line-height: 1;
}

.nickname-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0;
  white-space: nowrap;
}

.nickname-actions .icon-button {
  margin: 0 -3px;
}

.icon-button {
  display: inline-flex;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: var(--paper-muted);
  cursor: pointer;
  padding: 0;
}

.icon-button svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.9;
}

.icon-button:hover,
.icon-button:focus-visible {
  color: var(--paper-accent-dark);
  outline: none;
}

.icon-button:focus-visible {
  box-shadow: 0 0 0 3px rgba(138, 117, 99, 0.12);
}

.icon-button.confirm {
  color: #1F6B47;
}

.icon-button.cancel,
.setting-row.danger .row-title {
  color: #7a3f46;
}

.icon-button:disabled {
  cursor: wait;
  opacity: 0.54;
}

.metrics-strip {
  display: grid;
  width: min(100%, 600px);
  flex: 0 1 600px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
}

.metric-item {
  min-width: 0;
  padding: 14px 16px 15px 0;
}

.metric-label {
  display: block;
  margin-bottom: 4px;
  color: var(--paper-muted);
  font-size: 13px;
}

.metric-item strong {
  color: var(--paper-ink);
  font-size: 22px;
  font-weight: 760;
  line-height: 1.1;
  overflow-wrap: anywhere;
}

.metric-unit {
  margin-left: 4px;
  color: var(--paper-muted);
  font-size: 13px;
}

.settings-columns {
  align-self: end;
  display: grid;
  /* width: 100vw;
  margin-right: calc(50% - 50vw);
  margin-left: calc(50% - 50vw); */
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  border-radius: 5px;
  background: var(--paper-card);
  padding: 18px;
}

.settings-column {
  min-width: 0;
}

.column-header {
  padding: 6px 2px 14px;
}

.column-header h3 {
  margin: 0;
  color: var(--paper-ink);
  font-size: 18px;
  font-weight: 650;
  line-height: 1.15;
}

.setting-row {
  display: grid;
  width: 100%;
  grid-template-columns: 22px minmax(0, 1fr) 18px;
  gap: 10px;
  align-items: center;
  border: 0;
  border-radius: 5px;
  background: #fff;
  color: inherit;
  cursor: default;
  margin-top: 4px;
  padding: 10px 12px;
  text-align: left;
}

.row-icon {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  color: rgba(138, 117, 99, 0.66);
}

.row-icon :deep(.action-icon) {
  width: 17px;
  height: 17px;
  stroke-width: 2.2;
}

.setting-row.danger .row-icon {
  color: #7a3f46;
}

.row-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.row-title {
  color: var(--paper-ink);
  font-size: 14px;
  /* font-weight: 450; */
  line-height: 1.3;
}

.row-arrow {
  width: 8px;
  height: 8px;
  justify-self: end;
  align-self: center;
  border-top: 1.5px solid currentColor;
  border-right: 1.5px solid currentColor;
  color: rgba(116, 107, 97, 0.72);
  transform: rotate(45deg);
}

.setting-row.inactive {
  opacity: 0.82;
}

.setting-row:not(.inactive) {
  cursor: pointer;
}

.setting-row:not(.inactive):hover .row-title {
  color: var(--paper-accent-dark);
}

.setting-row:focus-visible {
  outline: 3px solid rgba(138, 117, 99, 0.12);
  outline-offset: 3px;
}

.profile-legal-links {
  align-self: end;
  display: flex;
  justify-content: center;
  gap: 14px;
  padding-top: 8px;
}

.profile-legal-links button {
  min-height: 32px;
  border: 0;
  background: transparent;
  color: rgba(116, 107, 97, 0.68);
  cursor: pointer;
  font-size: 12px;
  line-height: 1.4;
  padding: 0;
}

.profile-legal-links button:hover,
.profile-legal-links button:focus-visible {
  color: var(--paper-accent-dark);
  text-decoration: underline;
  text-underline-offset: 5px;
}

.my-empty {
  color: var(--paper-muted);
}

:global(.my-resonance-drawer .el-drawer__header),
:global(.my-diary-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding-bottom: 20px;
  background: var(--paper-card);
}

:global(.my-resonance-drawer .el-drawer__body),
:global(.my-diary-drawer .el-drawer__body) {
  background: var(--paper-card);
}

.drawer-diary-timeline {
  display: grid;
  gap: 22px;
}

.drawer-diary-month-group {
  display: grid;
  gap: 8px;
}

.drawer-diary-month {
  position: sticky;
  top: -20.5px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: -1px 0 0;
  padding: 7px 0 9px;
  background: var(--paper-card);
  color: rgba(116, 107, 97, 0.66);
  font-size: 22px;
  font-weight: 720;
  line-height: 1.35;
}

.drawer-diary-month::after {
  content: '';
  height: 1px;
  flex: 1 1 auto;
  background: rgba(138, 117, 99, 0.1);
}

:global(.my-diary-drawer .el-drawer__body) {
  overflow-y: auto;
  overscroll-behavior: contain;
}

.drawer-diary-list {
  display: grid;
  gap: 8px;
}

.drawer-diary-card {
  display: grid;
  min-height: 118px;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  border: 0;
  border-radius: 6px;
  /* background: rgba(255, 253, 247, 0.78); */
  background: var(--paper-bg);
  color: inherit;
  cursor: pointer;
  padding: 14px;
  transition:
    background-color 180ms ease,
    transform 180ms ease;
}

.drawer-diary-card.has-image {
  grid-template-columns: 52px minmax(0, 1fr) 96px;
}

.drawer-diary-card:hover,
.drawer-diary-card:focus-visible {
  /* background: rgba(255, 255, 255, 0.94); */
  background: #EFEEEC;
  outline: none;
  transform: translateY(-1px);
}

.drawer-diary-card:focus-visible {
  box-shadow: 0 0 0 3px rgba(138, 117, 99, 0.1);
}

.drawer-diary-date-block {
  display: grid;
  align-content: start;
  gap: 4px;
  color: var(--paper-accent-dark);
  font-variant-numeric: tabular-nums;
}

.drawer-diary-date-block strong {
  font-size: 25px;
  font-weight: 760;
  line-height: 1;
}

.drawer-diary-date-block span {
  color: rgba(116, 107, 97, 0.72);
  font-size: 12px;
  font-weight: 680;
  line-height: 1.2;
}

.drawer-diary-copy {
  display: grid;
  min-width: 0;
  align-content: start;
  gap: 7px;
}

.drawer-diary-copy h5 {
  overflow: hidden;
  margin: 0;
  color: var(--paper-ink);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-diary-copy p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  color: var(--paper-ink);
  font-size: 13px;
  line-height: 1.6;
}

.drawer-diary-card:not(.has-title) .drawer-diary-copy p {
  -webkit-line-clamp: 4;
}

.drawer-diary-card:not(.has-image) .drawer-diary-copy p {
  -webkit-line-clamp: 5;
}

.drawer-diary-card :deep(.thumbnail-wrapper) {
  width: 96px;
  height: 90px;
}

.drawer-diary-card :deep(.thumbnail-box) {
  border-radius: 5px;
  box-shadow: none;
}

:global(.drawer-diary-dialog.el-dialog) {
  width: min(94vw, 690px) !important;
  margin: max(16px, calc((100dvh - 760px) / 2)) auto 0 !important;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  overflow: visible;
}

:global(.drawer-diary-dialog .el-dialog__header) {
  display: none;
}

:global(.drawer-diary-dialog .el-dialog__body) {
  width: 100%;
  padding: 0;
  overflow: visible;
}

:global(.drawer-diary-dialog .diary-page.embedded) {
  width: 100%;
  overflow: visible;
}

:global(.account-action-dialog.el-dialog) {
  margin: 0 auto !important;
  border: 0;
  border-radius: 10px;
  background: #fffdf7;
  box-shadow:
    0 22px 46px -36px rgba(47, 43, 38, 0.42),
    0 8px 18px -18px rgba(47, 43, 38, 0.24);
  overflow: hidden;
}

:global(.el-overlay-dialog:has(.account-action-dialog)) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

:global(.account-action-dialog .el-dialog__header) {
  margin: 0;
  padding: 10px 30px 15px;
}

:global(.account-action-dialog .el-dialog__title) {
  color: var(--paper-ink);
  font-size: 20px;
  font-weight: 740;
  line-height: 1.35;
}

:global(.account-action-dialog .el-dialog__headerbtn) {
  top: 18px;
  right: 18px;
  width: 40px;
  height: 40px;
}

:global(.account-action-dialog .el-dialog__body) {
  padding: 10px 30px 4px;
}

:global(.account-action-dialog .el-dialog__footer) {
  padding: 12px 30px 24px;
}

:global(.account-action-dialog .el-form-item) {
  align-items: stretch;
  margin-bottom: 12px;
}

:global(.account-action-dialog .el-form-item__label) {
  display: block;
  width: auto;
  height: auto;
  flex: none;
  margin-bottom: 4px;
  color: var(--paper-muted);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.25;
  padding: 0;
}

:global(.account-action-dialog .el-form-item__content) {
  line-height: 1.35;
}

:global(.account-action-dialog .el-input__wrapper) {
  min-height: 40px;
  border-radius: 0 !important;
  border-bottom: 1px solid rgba(138, 117, 99, 0.16);
  background: transparent;
  box-shadow: none;
  padding: 0 2px 5px;
}

:global(.account-action-dialog .el-input__wrapper:hover),
:global(.account-action-dialog .el-input__wrapper.is-focus) {
  border-bottom-color: rgba(138, 117, 99, 0.42);
  background: transparent;
  box-shadow: none;
}

:global(.account-action-dialog .el-dialog__footer .el-button) {
  min-width: 86px;
  height: 36px;
  border: 0;
  padding: 0 16px;
}

:global(.account-action-dialog .el-dialog__footer .el-button:not(.el-button--primary):not(.el-button--danger)) {
  background: transparent;
  color: var(--paper-muted);
  box-shadow: none;
}

:global(.delete-account-dialog .el-button--danger) {
  background: #7a3f46;
  color: #fffdf8;
  box-shadow: none;
}

:global(.delete-account-dialog .el-button--danger:hover),
:global(.delete-account-dialog .el-button--danger:focus-visible) {
  background: #6b343b;
  color: #fffdf8;
}

:global(.account-action-dialog .password-field) {
  display: grid;
  width: 100%;
  gap: 6px;
}

:global(.account-action-dialog .password-requirements) {
  gap: 1px;
  margin-top: 0;
  line-height: 1.42;
}

.drawer-header {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.drawer-header h3 {
  margin: 0;
  color: var(--paper-ink);
  font-size: 18px;
  font-weight: 720;
}

.drawer-back-button {
  position: relative;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--paper-accent-dark);
  cursor: pointer;
}

.drawer-back-button span {
  position: absolute;
  top: 19px;
  left: 11px;
  width: 18px;
  height: 1px;
  background: currentColor;
  transform: rotate(45deg);
}

.drawer-back-button span + span {
  transform: rotate(-45deg);
}

.drawer-back-button:hover,
.drawer-back-button:focus-visible {
  background: rgba(138, 117, 99, 0.06);
  outline: none;
}

.delete-account-warning {
  margin: 0 0 16px;
  color: #7a3f46;
  font-size: 14px;
  line-height: 1.7;
  padding: 0;
}

.my-post-list {
  display: grid;
  gap: 8px;
}

.my-post {
  display: grid;
  height: 142px;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 12px;
  border: 0;
  border-radius: 6px;
  background: var(--paper-bg);
  color: inherit;
  cursor: pointer;
  padding: 14px;
  transition:
    background-color 180ms ease,
    transform 180ms ease;
}

.my-post:hover,
.my-post:focus-visible {
  background: #EFEEEC;
  outline: none;
  transform: translateY(-1px);
}

.my-post:focus-visible {
  box-shadow: 0 0 0 3px rgba(138, 117, 99, 0.1);
}

@media (hover: none) {
  .drawer-diary-card:hover,
  .drawer-diary-card:focus-visible,
  .drawer-diary-card:active,
  .my-post:hover,
  .my-post:focus-visible,
  .my-post:active {
    background: var(--paper-bg);
    box-shadow: none;
    transform: none;
  }
}

.my-post-footer {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
  color: var(--paper-muted);
  font-size: 13px;
  line-height: 1.3;
}

.my-post-footer > span {
  overflow: hidden;
  min-width: 0;
  color: var(--paper-accent-dark);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-post p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  color: var(--paper-ink);
  line-height: 1.65;
}

.my-post button {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: var(--paper-accent);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  padding: 0 0 2px;
}

.my-post button:hover,
.my-post button:focus-visible {
  text-decoration: underline;
  text-underline-offset: 5px;
}

.echo-detail-backdrop {
  position: fixed;
  z-index: 3000;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(47, 43, 38, 0.16);
  backdrop-filter: blur(8px);
}

.echo-detail-card {
  position: relative;
  display: grid;
  width: min(90vw, 560px);
  max-width: 560px;
  max-height: min(78vh, 680px);
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid rgba(138, 117, 99, 0.16);
  border-radius: 10px;
  background: #fffdf7;
  color: var(--paper-ink);
  padding: 58px 44px 42px;
  box-shadow: 0 30px 70px -28px rgba(47, 43, 38, 0.48);
  text-align: center;
}

.echo-detail-body {
  display: flex;
  max-width: 420px;
  min-height: 0;
  max-height: calc(min(78vh, 680px) - 100px);
  flex-direction: column;
  justify-self: center;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 4px 12px 8px;
  scrollbar-gutter: stable;
}

.echo-detail-body p {
  margin: auto 0;
  line-height: 1.86;
  white-space: pre-wrap;
}

.echo-detail-body > span {
  margin-top: 26px;
  color: var(--paper-accent-dark);
  font-size: 13px;
  font-weight: 700;
}

.echo-detail-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--paper-accent-dark);
  cursor: pointer;
}

.echo-detail-close span {
  position: absolute;
  top: 19px;
  left: 11px;
  width: 18px;
  height: 1px;
  background: currentColor;
  transform: rotate(45deg);
}

.echo-detail-close span + span {
  transform: rotate(-45deg);
}

.echo-detail-close:hover,
.echo-detail-close:focus-visible {
  background: rgba(138, 117, 99, 0.06);
  outline: none;
}

.echo-detail-enter-active,
.echo-detail-leave-active {
  transition: opacity 220ms ease;
}

.echo-detail-enter-active .echo-detail-card,
.echo-detail-leave-active .echo-detail-card {
  transition: transform 220ms ease;
}

.echo-detail-enter-from,
.echo-detail-leave-to {
  opacity: 0;
}

.echo-detail-enter-from .echo-detail-card,
.echo-detail-leave-to .echo-detail-card {
  transform: translateY(18px) scale(0.96);
}

@media (max-width: 900px) {
  .profile-page {
    height: calc(100dvh - var(--mobile-app-header-height, 70px) - 32px - var(--mobile-safe-area-bottom, 0px));
    grid-template-rows: auto minmax(0, 1fr) auto;
  }

  .identity-panel {
    align-items: stretch;
    flex-direction: column;
    gap: 14px;
  }

  .metrics-strip {
    width: 100%;
    flex-basis: auto;
  }

  .settings-columns {
    align-self: end;
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 14px;
  }
}

@media (max-width: 680px) {
  .profile-page {
    gap: 16px;
  }

  :global(.account-action-dialog.el-dialog) {
    width: min(88vw, 360px) !important;
    max-height: calc(100dvh - 48px);
    margin: 0 auto !important;
  }

  :global(.account-action-dialog .el-dialog__header) {
    padding: 5px 22px 10px;
  }

  :global(.account-action-dialog .el-dialog__title) {
    font-size: 18px;
  }

  :global(.account-action-dialog .el-dialog__headerbtn) {
    top: 12px;
    right: 12px;
    width: 44px;
    height: 44px;
  }

  :global(.account-action-dialog .el-dialog__body) {
    max-height: calc(100dvh - 190px);
    overflow-y: auto;
    padding: 8px 22px 2px;
  }

  :global(.account-action-dialog .el-dialog__footer) {
    padding: 10px 22px 20px;
  }

  :global(.account-action-dialog .el-dialog__footer .dialog-footer),
  :global(.account-action-dialog .el-dialog__footer) {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  :global(.account-action-dialog .el-dialog__footer .el-button) {
    width: auto;
    min-width: 78px;
    min-height: 0;
    height: 36px;
    margin: 0;
    border: 0;
    border-radius: 5px;
    padding: 0 14px;
  }

  :global(.account-action-dialog .el-dialog__footer .el-button:not(.el-button--primary):not(.el-button--danger)) {
    background: transparent;
    color: var(--paper-muted);
    box-shadow: none;
  }

  :global(.account-action-dialog .el-dialog__footer .el-button + .el-button) {
    margin-left: 0;
  }

  :global(.account-action-dialog .el-form-item) {
    margin-bottom: 11px;
  }

  :global(.account-action-dialog .el-form-item__label) {
    font-size: 13px;
  }

  :global(.account-action-dialog .el-input__wrapper) {
    min-height: 40px;
  }

  :global(.delete-account-dialog.el-dialog) {
    width: min(86vw, 340px) !important;
  }

  .delete-account-warning {
    margin-bottom: 10px;
    font-size: 13px;
    padding: 0;
  }

  :global(.drawer-diary-dialog.el-dialog) {
    width: min(94vw, 640px) !important;
    margin: max(16px, calc((100dvh - max(640px, calc(100dvh - 126px))) / 2)) auto 0 !important;
  }

  .drawer-diary-card,
  .drawer-diary-card.has-image {
    grid-template-columns: 46px minmax(0, 1fr) 74px;
    gap: 10px;
    padding: 12px;
  }

  .drawer-diary-card:not(.has-image) {
    grid-template-columns: 46px minmax(0, 1fr);
  }

  .drawer-diary-date-block strong {
    font-size: 22px;
  }

  .drawer-diary-card :deep(.thumbnail-wrapper) {
    width: 74px;
    height: 76px;
  }

  .profile-legal-links {
    padding-top: 0px;
  }

  .profile-legal-links button {
    min-height: 22px;
  }

  .nickname-row {
    flex-wrap: wrap;
  }

  .nickname-row h2,
  .nickname-input {
    font-size: 22px;
  }

  .nickname-input {
    width: min(100%, 260px);
  }

  .metrics-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item {
    padding-right: 10px;
  }

  .echo-detail-card {
    width: min(92vw, 420px);
    max-height: min(72vh, 560px);
    padding: 54px 24px 34px;
  }

  .echo-detail-body {
    max-height: calc(min(72vh, 560px) - 88px);
    padding: 0 8px 6px;
  }

  .column-header h3 {
    font-size: 18px;
  }
}
</style>
