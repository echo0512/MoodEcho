<script setup>
// Vue 3 组合式 API：用于声明响应式状态、计算属性、生命周期和监听器。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
// Vue Router：route 读取当前地址参数，router 负责跳转或替换地址。
import { useRoute, useRouter } from 'vue-router'
import ActionIcon from '@/components/ActionIcon.vue'
// 日记卡片里的首图缩略图组件。
import DiaryThumbnail from '@/components/DiaryThumbnail.vue'
import DiaryCreateView from '@/views/diary/DiaryCreateView.vue'
import DiaryDetailView from '@/views/diary/DiaryDetailView.vue'
import DiaryEditView from '@/views/diary/DiaryEditView.vue'
// 我的日记页需要的接口：按日期查日记、查本月有记录日期、按关键词和语义搜索。
import {
  getDiariesByDateApi,
  getRecordedDatesApi,
  searchDiariesApi,
  semanticSearchDiariesApi,
} from '@/api/diary'

// 当前路由对象，用来读取 query 参数，例如 mode、date。
const route = useRoute()
// 路由控制对象，用来进入新建页、详情页，或同步当前页面 query。
const router = useRouter()

// 当前页面模式：地址栏 mode=search 时进入搜索，否则默认日历浏览。
const mode = ref(route.query.mode === 'search' ? 'search' : 'calendar')
// 日历模式下，按日期加载日记列表时的 loading 状态。
const loading = ref(false)
// 日历模式下，加载“哪些日期写过日记”时的 loading 状态。
const datesLoading = ref(false)
const showCalendarSkeleton = ref(false)
let calendarSkeletonTimer = null
let diaryDateRequestId = 0
const createDialogVisible = ref(false)
const selectedDiaryId = ref(null)
const diaryDialogMode = ref('detail')
// 搜索模式下，请求搜索接口时的 loading 状态。
const searchLoading = ref(false)
// 当前选中日期下的日记列表。
const diaries = ref([])
// 当前日历月份中有记录的日期，用来在日历格子上显示小点。
const recordedDates = ref([])
// 搜索框输入的关键词；优先从地址栏 q 恢复，便于从详情页返回时保留搜索状态。
const initialSearchKeyword = typeof route.query.q === 'string' ? route.query.q : ''
const searchKeyword = ref(initialSearchKeyword)
const searchedKeyword = ref(initialSearchKeyword.trim())
const searchType = ref(route.query.searchType === 'semantic' ? 'semantic' : 'keyword')
const searchedSearchType = ref(searchType.value)
// 搜索接口返回的日记列表。
const searchResults = ref([])
// 是否已经执行过搜索，用来区分“未搜索”和“搜索后无结果”的空状态。
const hasSearched = ref(mode.value === 'search' && Boolean(initialSearchKeyword.trim()))
// 搜索结果按日期导航时，当前选中的日期。
const selectedSearchDate = ref('')
// 搜索结果分组标题的 DOM 引用，用于点击时间轴后滚动定位。
const searchGroupRefs = ref({})
// 搜索结果右侧列表容器，用于滚动时同步左侧时间轴高亮。
const searchResultsListRef = ref(null)
const searchTimelineRef = ref(null)
const searchTimelineItemRefs = ref({})
const searchDateStripRef = ref(null)
const searchDateChipRefs = ref({})
const isSearchTimelineJumping = ref(false)
const searchTypeMenuOpen = ref(false)
const searchTypeMenuRef = ref(null)
const searchTypeOptions = [
  { label: '找一找', value: 'keyword' },
  { label: '猜你想找', value: 'semantic' },
]
let searchPageScrollContainer = null

// 把 Date 对象或日期字符串统一整理成 YYYY-MM-DD。
// 这样日历、接口参数、路由 query 都使用同一种日期格式。
const formatDate = (date) => {
  // 没有日期时返回空字符串，避免后续 split、slice 或 Date 方法报错。
  if (!date) return ''

  // 如果后端或路由已经给了字符串，只取日期前 10 位。
  // 这样可以兼容 2026-05-23 和 2026-05-23T12:00:00 这两种形式。
  if (typeof date === 'string') {
    return date.slice(0, 10)
  }

  // Date 对象需要手动拆成年、月、日。
  // 月份和日期都补成两位，保证最终展示和接口参数稳定。
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

// 把 YYYY-MM-DD 字符串转换为本地 Date 对象。
// Element Plus 的 el-calendar 使用 Date 对象作为 v-model。
const parseLocalDate = (value) => {
  // 地址栏没有 date 参数时，默认选中今天。
  if (!value) return new Date()

  // 手动拆年月日，避免 new Date('YYYY-MM-DD') 在不同时区产生日期偏移。
  const [year, month, day] = value.split('-').map(Number)
  // 如果 query 里传入了非法日期，兜底回今天，避免日历初始化失败。
  if (!year || !month || !day) return new Date()

  // JavaScript Date 的月份从 0 开始，所以这里要 month - 1。
  return new Date(year, month - 1, day)
}

// 初始日期优先来自地址栏 query，方便从详情页返回时恢复原来的日期。
const initialDate = typeof route.query.date === 'string' ? route.query.date : formatDate(new Date())
// 日历组件绑定的 Date 对象。
const calendarDate = ref(parseLocalDate(initialDate))
// 接口请求和标题展示使用的字符串日期。
const selectedDate = ref(formatDate(calendarDate.value))
const loadedDiaryDate = ref(selectedDate.value)

// 当前标题直接展示 selectedDate；用 computed 保留后续扩展格式化文案的空间。
const selectedDateText = computed(() => selectedDate.value)

// 把有记录的日期数组转成 Set，日历每个格子判断小点时查找更直接。
const recordedDateSet = computed(() => new Set(recordedDates.value))

watch(
  [loading, datesLoading],
  ([isLoading, isDatesLoading]) => {
    clearTimeout(calendarSkeletonTimer)
    calendarSkeletonTimer = null

    if (!isLoading && !isDatesLoading) {
      showCalendarSkeleton.value = false
      return
    }

    calendarSkeletonTimer = setTimeout(() => {
      showCalendarSkeleton.value = true
    }, 180)
  },
  { flush: 'sync' },
)

const searchResultGroups = computed(() => {
  const groups = new Map()

  searchResults.value.forEach((diary) => {
    const recordDate = formatDate(diary.recordDate)

    if (!recordDate) return

    if (!groups.has(recordDate)) {
      groups.set(recordDate, [])
    }

    groups.get(recordDate).push(diary)
  })

  return Array.from(groups.entries())
    .map(([date, items]) => ({
      date,
      items,
    }))
    .sort((a, b) => b.date.localeCompare(a.date))
})

const searchResultStats = computed(() => {
  const groups = searchResultGroups.value

  if (groups.length === 0) {
    return {
      diaryCount: 0,
      dateCount: 0,
      dateStart: '',
      dateEnd: '',
    }
  }

  return {
    diaryCount: searchResults.value.length,
    dateCount: groups.length,
    dateStart: groups[groups.length - 1].date,
    dateEnd: groups[0].date,
  }
})

const currentSearchTypeLabel = computed(() => {
  return searchTypeOptions.find((option) => option.value === searchType.value)?.label || '找一找'
})

watch(
  searchResultGroups,
  (groups) => {
    searchGroupRefs.value = {}
    searchTimelineItemRefs.value = {}
    searchDateChipRefs.value = {}

    if (groups.length === 0) {
      selectedSearchDate.value = ''
      return
    }

    if (!groups.some((group) => group.date === selectedSearchDate.value)) {
      selectedSearchDate.value = groups[0].date
    }
  },
  {
    flush: 'post',
  },
)

// 从日期中拆出年份和月份，用于请求“当前月哪些日期有日记”。
const getYearMonth = (date) => {
  const formattedDate = formatDate(date)
  const [year, month] = formattedDate.split('-')

  return {
    year: Number(year),
    month: Number(month),
  }
}

// 加载当前选中日期下的日记列表。
// 结果会渲染到右侧的卡片列表中。
const loadDiariesByDate = async () => {
  const requestId = ++diaryDateRequestId
  const requestDate = selectedDate.value

  loading.value = true

  try {
    // 按 selectedDate 调接口，返回当天所有正式日记。
    const nextDiaries = await getDiariesByDateApi(requestDate)

    if (requestId !== diaryDateRequestId) return

    diaries.value = nextDiaries
    loadedDiaryDate.value = requestDate
  } finally {
    if (requestId === diaryDateRequestId) {
      loading.value = false
    }
  }
}

// 加载当前日历月份里哪些日期有记录。
// 这些日期会在日历格子上显示小点。
const loadRecordedDates = async () => {
  datesLoading.value = true

  try {
    // 后端按年月返回有记录的日期，因此先从当前日历日期里取年月。
    const { year, month } = getYearMonth(calendarDate.value)

    recordedDates.value = await getRecordedDatesApi({
      year,
      month,
    })
  } finally {
    datesLoading.value = false
  }
}

// 监听日历选中日期变化。
// 用户点某一天时，需要同步标题、地址栏、日记列表，以及必要时刷新当月小点。
watch(calendarDate, async (newValue, oldValue) => {
  // 没有日期或当前在搜索模式时，不处理日历变化。
  if (!newValue || mode.value !== 'calendar') return

  // 把 Date 对象转成字符串，后续接口和路由都用字符串。
  selectedDate.value = formatDate(newValue)
  // 用 replace 同步地址栏，避免每点一天都新增一条浏览器历史记录。
  router.replace({
    path: '/diaries',
    query: {
      mode: 'calendar',
      date: selectedDate.value,
    },
  })

  // 日期变了，右侧日记列表要重新加载。
  await loadDiariesByDate()

  // oldValue 为空通常是首次触发，直接加载当前月标记即可。
  if (!oldValue) {
    await loadRecordedDates()
    return
  }

  // 如果只是同月内换日期，不必重新请求整个月的小点。
  // 如果跨月了，才重新加载 recordedDates。
  const oldYearMonth = getYearMonth(oldValue)
  const newYearMonth = getYearMonth(newValue)

  if (oldYearMonth.year !== newYearMonth.year || oldYearMonth.month !== newYearMonth.month) {
    await loadRecordedDates()
  }
})

// 监听地址栏 mode 参数。
// 这样浏览器前进/后退或外部链接进入时，页面模式能和 URL 保持一致。
watch(
  () => route.query.mode,
  (queryMode) => {
    mode.value = queryMode === 'search' ? 'search' : 'calendar'
  },
)

// 监听地址栏搜索词，支持浏览器前进/后退和从详情页返回时恢复搜索结果。
watch(
  () => route.query.q,
  async (queryKeyword) => {
    if (mode.value !== 'search') return

    const keyword = typeof queryKeyword === 'string' ? queryKeyword : ''
    searchType.value = route.query.searchType === 'semantic' ? 'semantic' : 'keyword'
    searchKeyword.value = keyword

    if (!keyword.trim()) {
      searchResults.value = []
      hasSearched.value = false
      return
    }

    await searchDiaries(keyword)
  },
)

watch(
  () => route.query.searchType,
  async (querySearchType) => {
    if (mode.value !== 'search') return

    const nextSearchType = querySearchType === 'semantic' ? 'semantic' : 'keyword'
    if (searchType.value === nextSearchType) return

    searchType.value = nextSearchType

    if (searchKeyword.value.trim()) {
      await searchDiaries(searchKeyword.value.trim())
    }
  },
)

// 切换“日历浏览 / 搜索”模式。
// 切回日历时会立即刷新日历列表和月份记录点。
const switchMode = async (nextMode) => {
  mode.value = nextMode

  if (nextMode === 'calendar') {
    // 日历模式需要把当前日期写回 URL，便于刷新和返回时恢复。
    router.replace({
      path: '/diaries',
      query: {
        mode: 'calendar',
        date: selectedDate.value,
      },
    })

    // 同时加载日记列表和日历小点，两个请求互不依赖，可以并行。
    await Promise.all([loadDiariesByDate(), loadRecordedDates()])
    return
  }

  // 搜索模式不需要日期参数，只保留 mode=search。
  router.replace({
    path: '/diaries',
    query: {
      mode: 'search',
      searchType: searchType.value,
      ...(searchKeyword.value.trim() ? { q: searchKeyword.value.trim() } : {}),
    },
  })
}

// 请求搜索接口。URL 同步由 handleSearch 负责，避免恢复路由时重复改地址。
const searchDiaries = async (keyword) => {
  searchLoading.value = true
  // 一旦发起搜索，就进入“已搜索”状态；之后无结果时显示“没有找到相关日记”。
  hasSearched.value = true
  searchedKeyword.value = keyword.trim()
  searchedSearchType.value = searchType.value

  try {
    // 搜索结果渲染到搜索模式下的卡片列表。
    searchResults.value =
      searchType.value === 'semantic'
        ? await semanticSearchDiariesApi({
            query: keyword,
            limit: 10,
          })
        : await searchDiariesApi(keyword)
  } finally {
    searchLoading.value = false
  }
}

// 执行搜索。
// 搜索框回车或点击搜索按钮都会走这里。
const handleSearch = async () => {
  const keyword = searchKeyword.value.trim()

  // 空关键词不请求接口，同时恢复到“未搜索”的空状态。
  if (!keyword) {
    searchResults.value = []
    hasSearched.value = false
    searchedKeyword.value = ''
    router.replace({
      path: '/diaries',
      query: {
        mode: 'search',
        searchType: searchType.value,
      },
    })
    return
  }

  const currentKeyword = typeof route.query.q === 'string' ? route.query.q : ''
  const currentSearchType = route.query.searchType === 'semantic' ? 'semantic' : 'keyword'

  if (
    route.query.mode !== 'search' ||
    currentKeyword !== keyword ||
    currentSearchType !== searchType.value
  ) {
    await router.replace({
      path: '/diaries',
      query: {
        mode: 'search',
        searchType: searchType.value,
        q: keyword,
      },
    })

    if (currentKeyword === keyword && currentSearchType !== searchType.value) {
      await searchDiaries(keyword)
    }

    return
  }

  await searchDiaries(keyword)
}

const handleSearchTypeChange = async () => {
  if (mode.value !== 'search') return

  if (!searchKeyword.value.trim()) {
    router.replace({
      path: '/diaries',
      query: {
        mode: 'search',
        searchType: searchType.value,
      },
    })
    return
  }

  await handleSearch()
}

const toggleSearchTypeMenu = () => {
  searchTypeMenuOpen.value = !searchTypeMenuOpen.value
}

const selectSearchType = async (nextSearchType) => {
  searchTypeMenuOpen.value = false

  if (searchType.value === nextSearchType) return

  searchType.value = nextSearchType
  await handleSearchTypeChange()
}

const closeSearchTypeMenuOnOutsideClick = (event) => {
  if (!searchTypeMenuOpen.value) return
  if (searchTypeMenuRef.value?.contains(event.target)) return

  searchTypeMenuOpen.value = false
}

// 清空搜索框时同步清空搜索结果，并回到未搜索状态。
const clearSearch = () => {
  searchKeyword.value = ''
  searchResults.value = []
  hasSearched.value = false
  searchedKeyword.value = ''
  router.replace({
    path: '/diaries',
    query: {
      mode: 'search',
      searchType: searchType.value,
    },
  })
}

const shiftMonth = (offset) => {
  const current = calendarDate.value
  const targetYear = current.getFullYear()
  const targetMonth = current.getMonth() + offset
  const targetDay = current.getDate()
  const lastDayOfTargetMonth = new Date(targetYear, targetMonth + 1, 0).getDate()

  return new Date(targetYear, targetMonth, Math.min(targetDay, lastDayOfTargetMonth))
}

const goPreviousMonth = () => {
  calendarDate.value = shiftMonth(-1)
}

const goToday = () => {
  calendarDate.value = new Date()
}

const goNextMonth = () => {
  calendarDate.value = shiftMonth(1)
}

const goCreate = () => {
  createDialogVisible.value = true
}

const closeCreateDialog = () => {
  createDialogVisible.value = false
}

const refreshVisibleDiaries = async () => {
  if (mode.value === 'search') {
    const keyword = searchKeyword.value.trim()
    if (keyword) await searchDiaries(keyword)
    return
  }

  await Promise.all([loadDiariesByDate(), loadRecordedDates()])
}

const handleDiarySaved = async () => {
  createDialogVisible.value = false
  await refreshVisibleDiaries()
}

const openDiaryDetail = (id) => {
  selectedDiaryId.value = id
  diaryDialogMode.value = 'detail'
}

const closeDiaryDialog = () => {
  selectedDiaryId.value = null
  diaryDialogMode.value = 'detail'
}

const openDiaryEdit = (id) => {
  selectedDiaryId.value = id
  diaryDialogMode.value = 'edit'
}

const closeDiaryEdit = () => {
  diaryDialogMode.value = 'detail'
}

const handleDiaryUpdated = async () => {
  diaryDialogMode.value = 'detail'
  await refreshVisibleDiaries()
}

const handleDiaryDeleted = async () => {
  closeDiaryDialog()
  await refreshVisibleDiaries()
}

// 日历和搜索模式中的卡片都在当前页面打开详情弹层。
const goDetailFromCalendar = (id) => {
  openDiaryDetail(id)
}

const goDetailFromSearch = (diaryOrId) => {
  const id = typeof diaryOrId === 'object' ? diaryOrId.id : diaryOrId
  openDiaryDetail(id)
}

const setSearchGroupRef = (date, element) => {
  if (!element) return

  searchGroupRefs.value[date] = element
}

const setSearchTimelineItemRef = (date, element) => {
  if (!element) return

  searchTimelineItemRefs.value[date] = element
}

const setSearchDateChipRef = (date, element) => {
  if (!element) return

  searchDateChipRefs.value[date] = element
}

const keepSearchTimelineDateVisible = (date) => {
  const container = searchTimelineRef.value
  const element = searchTimelineItemRefs.value[date]

  if (!container || !element) return

  const containerRect = container.getBoundingClientRect()
  const elementRect = element.getBoundingClientRect()
  const topGap = elementRect.top - containerRect.top
  const bottomGap = elementRect.bottom - containerRect.bottom

  if (topGap >= 0 && bottomGap <= 0) return

  const targetTop =
    bottomGap > 0
      ? container.scrollTop + bottomGap + 12
      : container.scrollTop + topGap - 12

  container.scrollTo({
    top: Math.max(targetTop, 0),
    behavior: 'auto',
  })
}

const keepSearchDateChipVisible = (date) => {
  const container = searchDateStripRef.value
  const element = searchDateChipRefs.value[date]

  if (!container || !element) return

  const containerRect = container.getBoundingClientRect()
  const elementRect = element.getBoundingClientRect()
  const leftGap = elementRect.left - containerRect.left
  const rightGap = elementRect.right - containerRect.right

  if (leftGap >= 0 && rightGap <= 0) return

  const targetLeft =
    rightGap > 0
      ? container.scrollLeft + rightGap + 16
      : container.scrollLeft + leftGap - 16

  container.scrollTo({
    left: Math.max(targetLeft, 0),
    behavior: 'auto',
  })
}

const keepSearchDateVisible = (date) => {
  keepSearchTimelineDateVisible(date)
  keepSearchDateChipVisible(date)
}

const getSearchResultsStartOffset = () => {
  const container = searchResultsListRef.value

  if (!container) return 0

  return Number.parseFloat(window.getComputedStyle(container).paddingTop) || 0
}

const getSearchPageScrollContainer = () => {
  return searchPageScrollContainer || searchDateStripRef.value?.closest('.app-main') || null
}

const selectSearchDate = async (date) => {
  selectedSearchDate.value = date
  keepSearchDateVisible(date)
  isSearchTimelineJumping.value = true

  await nextTick()

  const container = searchResultsListRef.value
  const element = searchGroupRefs.value[date]

  if (!element) {
    isSearchTimelineJumping.value = false
    return
  }

  if (!container || window.getComputedStyle(container).overflowY === 'visible') {
    const dateStrip = searchDateStripRef.value
    const dateStripVisible =
      dateStrip && window.getComputedStyle(dateStrip).display !== 'none'

    if (dateStripVisible) {
      const pageScrollContainer = getSearchPageScrollContainer()
      const resultStartOffset = getSearchResultsStartOffset()
      const targetTop =
        (pageScrollContainer?.scrollTop || window.scrollY) +
        element.getBoundingClientRect().top -
        dateStrip.getBoundingClientRect().bottom -
        resultStartOffset

      if (pageScrollContainer) {
        pageScrollContainer.scrollTo({
          top: Math.max(targetTop, 0),
          behavior: 'auto',
        })
      } else {
        window.scrollTo({
          top: Math.max(targetTop, 0),
          behavior: 'auto',
        })
      }
    } else {
      element.scrollIntoView({
        behavior: 'auto',
        block: 'start',
      })
    }

    isSearchTimelineJumping.value = false
    return
  }

  const containerRect = container.getBoundingClientRect()
  const elementRect = element.getBoundingClientRect()
  const targetTop =
    container.scrollTop + elementRect.top - containerRect.top - getSearchResultsStartOffset()

  container.scrollTo({
    top: Math.max(targetTop, 0),
    behavior: 'auto',
  })

  window.requestAnimationFrame(() => {
    isSearchTimelineJumping.value = false
  })
}

const syncSearchTimelineOnScroll = () => {
  const container = searchResultsListRef.value

  if (isSearchTimelineJumping.value) return
  if (!container || searchResultGroups.value.length === 0) return

  const dateStrip = searchDateStripRef.value
  const dateStripVisible =
    dateStrip && window.getComputedStyle(dateStrip).display !== 'none'
  const pageScrollContainer = dateStripVisible ? getSearchPageScrollContainer() : null
  const lastGroup = searchResultGroups.value[searchResultGroups.value.length - 1]
  const isAtBottom = dateStripVisible
    ? pageScrollContainer
      ? pageScrollContainer.scrollTop + pageScrollContainer.clientHeight >= pageScrollContainer.scrollHeight - 2
      : window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2
    : container.scrollTop + container.clientHeight >= container.scrollHeight - 2
  const activationTop = dateStripVisible
    ? dateStrip.getBoundingClientRect().bottom + 18
    : container.getBoundingClientRect().top + 12
  let activeDate = isAtBottom ? lastGroup.date : searchResultGroups.value[0].date

  if (!isAtBottom) {
    for (const group of searchResultGroups.value) {
      const element = searchGroupRefs.value[group.date]

      if (!element) continue

      if (element.getBoundingClientRect().top <= activationTop) {
        activeDate = group.date
        continue
      }

      break
    }
  }

  if (selectedSearchDate.value !== activeDate) {
    selectedSearchDate.value = activeDate
    keepSearchDateVisible(activeDate)
  }
}

// 卡片标题兜底：没有标题时显示“无标题日记”。
const getTitle = (diary) => diary.title || '无标题日记'

// 获取日记第一张图片。
// 列表卡片只展示首图作为缩略图，避免卡片信息过重。
const getFirstImage = (diary) => {
  if (!diary.images || diary.images.length === 0) {
    return null
  }

  return diary.images[0]
}

// 从富文本 HTML 中提取纯文字。
// 列表摘要不直接显示 HTML 标签和图片节点。
const getPlainText = (html) => {
  if (!html) return ''

  const div = document.createElement('div')
  div.innerHTML = html

  // 移除富文本图片标签，避免摘要里出现图片占位内容。
  div.querySelectorAll('img').forEach((img) => {
    img.remove()
  })

  // 兼容旧的 Markdown 图片占位格式，把 ![图片](moodecho-image:id) 去掉。
  return div.textContent.replace(/!\[图片\]\(moodecho-image:\d+\)/g, '').trim()
}

// 生成卡片摘要。
// 有文字时展示文字摘要；只有图片或完全为空时不显示摘要。
const getSummary = (diary) => {
  if (diary.summary) {
    return diary.summary
  }

  const text = getPlainText(diary.content)

  if (!text) {
    return ''
  }

  return text.length > 180 ? `${text.slice(0, 180)}...` : text
}

// 判断某一天是否写过日记。
// 日历单元格通过它决定是否显示记录小点。
const hasRecord = (date) => recordedDateSet.value.has(formatDate(date))

// 组件挂载后初始化数据。
// 默认日历模式下，需要同时加载当天日记和当月记录日期。
onMounted(async () => {
  window.addEventListener('scroll', syncSearchTimelineOnScroll, { passive: true })
  searchPageScrollContainer = document.querySelector('.app-main')
  searchPageScrollContainer?.addEventListener('scroll', syncSearchTimelineOnScroll, { passive: true })
  window.addEventListener('click', closeSearchTypeMenuOnOutsideClick)

  if (mode.value === 'calendar') {
    await Promise.all([loadDiariesByDate(), loadRecordedDates()])
    return
  }

  if (searchKeyword.value.trim()) {
    await searchDiaries(searchKeyword.value.trim())
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', syncSearchTimelineOnScroll)
  searchPageScrollContainer?.removeEventListener('scroll', syncSearchTimelineOnScroll)
  window.removeEventListener('click', closeSearchTypeMenuOnOutsideClick)
  clearTimeout(calendarSkeletonTimer)
})
</script>

<template>
  <!-- 整个“我的日记”页面容器，内部根据 mode 切换日历浏览和搜索两种视图。 -->
  <main
    class="page"
    :class="{
      'search-page': mode === 'search',
      'search-empty-page':
        mode === 'search' && !searchLoading && (!hasSearched || searchResults.length === 0),
      'calendar-empty-page': mode === 'calendar' && diaries.length === 0 && !showCalendarSkeleton,
    }"
  >
    <!-- 顶部模式切换容器：控制“日历浏览 / 搜索”。 -->
    <div class="mode-switch">
      <!--
        Element Plus 分段选择器。
        v-model 绑定 mode，用户切换后触发 switchMode，同步页面内容和地址栏 query。
      -->
      <el-segmented
        v-model="mode"
        :options="[
          { label: '日历浏览', value: 'calendar' },
          { label: '搜索', value: 'search' },
        ]"
        @change="switchMode"
      />
    </div>

    <!-- 日历模式：左侧日历，右侧选中日期的日记列表。 -->
    <div v-if="mode === 'calendar'" class="main-grid">
      <!-- 日历卡片只作为布局容器，shadow=never 保持纸面风格轻量。 -->
      <el-card class="calendar-card" shadow="never">
        <!-- 日历 v-model 绑定 calendarDate，点击日期会触发上面的 watch。 -->
        <el-calendar v-model="calendarDate">
          <template #header="{ date }">
            <span class="calendar-title">{{ date }}</span>
            <div class="calendar-actions">
              <button class="calendar-action" type="button" @click="goPreviousMonth">上月</button>
              <button class="calendar-action" type="button" @click="goToday">今日</button>
              <button class="calendar-action" type="button" @click="goNextMonth">下月</button>
            </div>
          </template>

          <!-- 自定义日期格内容，用于显示日期数字和“有记录”的小点。 -->
          <template #date-cell="{ data }">
            <div class="calendar-day-cell">
              <!-- data.day 是 YYYY-MM-DD，这里只取最后的日数字。 -->
              <span class="day-number">
                {{ data.day.split('-').slice(2).join('') }}
              </span>

              <!-- 如果当天写过日记，就显示一个小点作为视觉提示。 -->
              <span v-if="hasRecord(data.day)" class="record-dot" />
            </div>
          </template>
        </el-calendar>
      </el-card>

      <!-- 右侧日记列表区域，包含标题、加载态、空态和卡片列表。 -->
      <section class="diary-section">
        <!--
          列表标题区域。
          宽度较窄时会 sticky 固定在顶部，所以样式里给它加了纸纹背景遮住滚动内容。
        -->
        <div class="section-header">
          <div class="section-meta">
            <!-- 当前选中日期标题。 -->
            <h2>{{ selectedDateText }} </h2>
            <!-- 当前日期下日记篇数。 -->
            <p>共 {{ diaries.length }} 篇</p>
          </div>

          <!-- 进入新建日记页，并带上当前选中日期。 -->
          <button class="create-link" type="button" aria-label="写一篇" title="写一篇" @click="goCreate">
            <ActionIcon name="plus" />
          </button>
        </div>

        <div class="calendar-list-frame">
          <!-- 日记列表或日历小点还在加载时，显示骨架屏。 -->
          <el-skeleton v-if="showCalendarSkeleton" :rows="5" animated />

          <transition v-else name="calendar-list" mode="out-in">
            <!-- 当前日期没有日记时的空状态。 -->
            <div v-if="diaries.length === 0" :key="`empty-${loadedDiaryDate}`" class="empty-record">
              今日未有记录
            </div>

            <!-- 当前日期有日记时，渲染卡片列表。 -->
            <div v-else :key="loadedDiaryDate" class="diary-list calendar-diary-list">
              <!-- 每张卡片对应一篇日记，点击进入详情页。 -->
              <el-card
                v-for="diary in diaries"
                :key="diary.id"
                class="diary-card"
                :class="{ 'no-title': !diary.title, 'no-image': !getFirstImage(diary) }"
                shadow="never"
                @click="goDetailFromCalendar(diary.id)"
              >
                <!-- 卡片主体：左侧文字，右侧首图缩略图。 -->
                <div class="diary-card-content">
                  <!-- 日记文字区域，内部做标题和摘要截断。 -->
                  <div class="diary-card-copy">
                    <!-- 有标题才显示标题；没有标题时让摘要占更多空间。 -->
                    <h3 v-if="diary.title">{{ getTitle(diary) }}</h3>
                    <!-- 从富文本正文中提取出的纯文字摘要。 -->
                    <p v-if="getSummary(diary)" class="summary">{{ getSummary(diary) }}</p>
                  </div>

                  <!-- 如果有图片，展示第一张图片缩略图。 -->
                  <DiaryThumbnail v-if="getFirstImage(diary)" :image="getFirstImage(diary)" />
                </div>
              </el-card>
            </div>
          </transition>
        </div>
      </section>
    </div>

    <!-- 搜索模式：顶部搜索框 + 日期时间轴 + 搜索结果列表。 -->
    <template v-else>
      <el-card class="search-card" shadow="never">
        <div class="search-panel">
          <div class="search-box" :class="{ open: searchTypeMenuOpen }">
            <div
              ref="searchTypeMenuRef"
              class="search-type-dropdown"
              :class="{ open: searchTypeMenuOpen }"
            >
              <button
                class="search-type-trigger"
                type="button"
                :aria-expanded="searchTypeMenuOpen"
                @click.stop="toggleSearchTypeMenu"
              >
                {{ currentSearchTypeLabel }}
              </button>

              <transition name="search-type-menu">
                <div v-if="searchTypeMenuOpen" class="search-type-menu">
                  <button
                    v-for="option in searchTypeOptions"
                    :key="option.value"
                    class="search-type-option"
                    :class="{ active: searchType === option.value }"
                    type="button"
                    @click.stop="selectSearchType(option.value)"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </transition>
            </div>

            <el-input
              v-model="searchKeyword"
              :placeholder="
                searchType === 'semantic'
                  ? '搜索某个情绪或场景'
                  : '搜索标题或正文中关键词'
              "
              clearable
              @keyup.enter="handleSearch"
              @clear="clearSearch"
            />

            <el-button
              class="search-submit"
              type="primary"
              :loading="searchLoading"
              aria-label="搜索"
              title="搜索"
              @click="handleSearch"
            >
              <ActionIcon name="search" />
            </el-button>
          </div>
        </div>
      </el-card>

      <section class="search-section">
        <el-skeleton v-if="searchLoading" :rows="5" animated />

        <div v-else-if="hasSearched && searchResults.length === 0" class="search-empty">
          <h3>没有找到相关日记</h3>
          <p>换一个描述试试。</p>
        </div>

        <div v-else-if="!hasSearched" class="search-empty">
          <h3>输入内容，找找那一天。</h3>
          <p>
            {{
              searchType === 'semantic'
                ? 'back to that day'
                : 'back to that day'
            }}
          </p>
        </div>

        <div v-else class="search-results-layout">
          <aside ref="searchTimelineRef" class="search-timeline" aria-label="搜索结果日期">
            <button
              v-for="group in searchResultGroups"
              :key="group.date"
              :ref="(element) => setSearchTimelineItemRef(group.date, element)"
              class="timeline-item"
              :class="{ active: group.date === selectedSearchDate }"
              type="button"
              @click="selectSearchDate(group.date)"
            >
              <span class="timeline-dot" />
              <span class="timeline-date">{{ group.date }}</span>
            </button>
          </aside>

          <div ref="searchDateStripRef" class="search-date-strip" aria-label="搜索结果日期">
            <button
              v-for="group in searchResultGroups"
              :key="group.date"
              :ref="(element) => setSearchDateChipRef(group.date, element)"
              class="date-chip"
              :class="{ active: group.date === selectedSearchDate }"
              type="button"
              @click="selectSearchDate(group.date)"
            >
              {{ group.date }}
            </button>
          </div>

          <div
            ref="searchResultsListRef"
            class="diary-list search-results-list"
            @scroll="syncSearchTimelineOnScroll"
          >
            <section
              v-for="group in searchResultGroups"
              :key="group.date"
              :ref="(element) => setSearchGroupRef(group.date, element)"
              class="search-result-group"
            >
              <div class="search-group-heading">
                <h3>{{ group.date }}</h3>
                <p>{{ group.items.length }} 篇</p>
              </div>

              <div class="search-group-diaries">
                <el-card
                  v-for="diary in group.items"
                  :key="diary.id"
                  class="diary-card"
                  :class="{ 'no-title': !diary.title, 'no-image': !getFirstImage(diary) }"
                  shadow="never"
                  @click="goDetailFromSearch(diary)"
                >
                  <div class="diary-card-content">
                    <div class="diary-card-copy">
                      <h3 v-if="diary.title">{{ getTitle(diary) }}</h3>
                      <p v-if="getSummary(diary)" class="summary">{{ getSummary(diary) }}</p>
                    </div>

                    <DiaryThumbnail v-if="getFirstImage(diary)" :image="getFirstImage(diary)" />
                  </div>
                </el-card>
              </div>
            </section>
          </div>

          <aside class="search-summary" aria-label="搜索结果概览">
            <p>找到 <span class="summary-stat">{{ searchResultStats.diaryCount }}</span> 篇相关记录</p>
            <p>散落在 <span class="summary-stat">{{ searchResultStats.dateCount }}</span> 天里</p>
            <p class="summary-range">
              <span>从 <span class="summary-stat">{{ searchResultStats.dateStart }}</span></span>
              <span>到 <span class="summary-stat">{{ searchResultStats.dateEnd }}</span></span>
            </p>
          </aside>
        </div>
      </section>
    </template>

    <Teleport to="body">
      <transition name="diary-compose">
        <div v-if="createDialogVisible" class="diary-compose-backdrop">
          <DiaryCreateView
            embedded
            :initial-record-date="selectedDate"
            @close="closeCreateDialog"
            @saved="handleDiarySaved"
          />
        </div>
      </transition>
    </Teleport>

    <Teleport to="body">
      <transition name="diary-compose">
        <div v-if="selectedDiaryId" class="diary-compose-backdrop">
          <DiaryDetailView
            v-if="diaryDialogMode === 'detail'"
            :key="`detail-${selectedDiaryId}`"
            embedded
            :diary-id="selectedDiaryId"
            @close="closeDiaryDialog"
            @deleted="handleDiaryDeleted"
            @edit="openDiaryEdit"
          />

          <DiaryEditView
            v-else
            :key="`edit-${selectedDiaryId}`"
            embedded
            :diary-id="selectedDiaryId"
            @close="closeDiaryEdit"
            @saved="handleDiaryUpdated"
          />
        </div>
      </transition>
    </Teleport>
  </main>
</template>


<style scoped>
.page {
  display: grid;
  --diary-mode-height: 40px; /*日历浏览的内高度*/
  --diary-mode-sticky-height: 50px; /*日历浏览/搜索的小框固定时的标题栏高度*/
  --diary-mode-sticky-padding-top: 8px;
  --diary-section-sticky-gap: 0px;
  --diary-sticky-top: 64px;
  --diary-sticky-edge: 24px;
  gap: 10px;/*日历浏览/搜索到底下板块的间隔*/
  height: calc(100dvh - 155px);
  grid-template-rows: auto minmax(0, 1fr);
  margin-top: 5px;
  overflow: hidden;
}

.page.search-page {
  grid-template-rows: auto auto minmax(0, 1fr);
}

.diary-compose-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(47, 43, 38, 0.16);
  backdrop-filter: blur(8px);
}

.diary-compose-enter-active,
.diary-compose-leave-active {
  transition: opacity 220ms ease;
}

.diary-compose-enter-active :deep(.record-card),
.diary-compose-leave-active :deep(.record-card),
.diary-compose-enter-active :deep(.detail-card),
.diary-compose-leave-active :deep(.detail-card) {
  transition: transform 220ms ease;
}

.diary-compose-enter-from,
.diary-compose-leave-to {
  opacity: 0;
}

.diary-compose-enter-from :deep(.record-card),
.diary-compose-leave-to :deep(.record-card),
.diary-compose-enter-from :deep(.detail-card),
.diary-compose-leave-to :deep(.detail-card) {
  transform: translateY(18px) scale(0.96);
}

.mode-switch {
  display: flex;
  height: var(--diary-mode-height);
  align-items: flex-end;
  background: transparent;
}

.mode-switch :deep(.el-segmented) {
  height: 40px;
  align-items: flex-end;
  gap: 28px;
  background: transparent !important;
  box-shadow: none !important;
}

.mode-switch :deep(.el-segmented__item) {
  min-width: auto;
  height: 40px;
  padding: 0 2px 6px;
  background: transparent !important;
  color: var(--paper-muted);
  font-size: 22px;
  line-height: 38px;
  transition:
    color 180ms ease,
    font-size 180ms ease,
    font-weight 180ms ease;
}

.mode-switch :deep(.el-segmented__item.is-selected) {
  background: transparent !important;
  color: var(--paper-accent-dark);
  font-size: 27px;
  font-weight: 760;
  line-height: 38px;
}

.mode-switch :deep(.el-segmented__item-selected) {
  background: transparent !important;
  box-shadow: none !important;
}

.mode-switch :deep(.el-segmented__item.is-selected::after) {
  right: 2px;
  bottom: 0;
  left: 2px;
  height: 2px;
}

.main-grid {
  display: grid;
  --diary-header-height: 52px;
  grid-template-columns: minmax(320px, 390px) minmax(0, 1fr);
  gap: 80px;
  align-items: start;
  min-height: 0;
  overflow: hidden;
}

.diary-card {
  border: 0;
  border-radius: 10px;
  background: var(--paper-card);
  box-shadow: none;
}

.calendar-card {
  align-self: start;
  border: 0;
  background: transparent;
  box-shadow: none;
  max-height: 100%;
  min-width: 320px;
  overflow: hidden;
}

.calendar-card :deep(.el-calendar) {
  min-width: 320px;
}

.search-card {
  position: relative;
  z-index: 10;
  border: 0;
  background: transparent;
  box-shadow: none;
  overflow: visible;
  padding-top: 6px;
}

.calendar-card :deep(.el-card__body),
.search-card :deep(.el-card__body) {
  padding: 0;
}

.search-card :deep(.el-card__body) {
  overflow: visible;
}

.search-panel {
  display: grid;
  gap: 0;
  overflow: visible;
}

.search-box {
  display: flex;
  align-items: stretch;
  gap: 0;
  min-width: 0;
  overflow: visible;
}

.search-type-dropdown {
  position: relative;
  z-index: 20;
  flex: 0 0 auto;
}

.search-type-trigger {
  display: inline-flex;
  width: 104px;
  height: 40px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--paper-line);
  border-radius: 5px 0 0 5px;
  border-right: 0;
  background: rgba(255, 254, 250, 0.9);
  color: var(--paper-ink);
  cursor: pointer;
  font: inherit;
  font-size: 15px;
  font-weight: 400;
  line-height: 1;
  padding: 0 12px;
  white-space: nowrap;
  transition:
    border-color 160ms ease,
    background-color 160ms ease;
}

.search-type-trigger::after {
  width: 6px;
  height: 6px;
  border-right: 1px solid currentColor;
  border-bottom: 1px solid currentColor;
  content: '';
  transform: rotate(45deg) translateY(-2px);
}

.search-type-trigger:hover,
.search-type-trigger:focus-visible {
  border-color: rgba(116, 107, 97, 0.42);
  background: rgba(239, 238, 236, 0.72);
  /* background: #EFEEEC; */
}

.search-type-dropdown.open .search-type-trigger {
  border-color: var(--paper-ink);
  background: rgba(255, 254, 250, 0.9);
}

.search-type-menu {
  position: absolute;
  z-index: 30;
  top: 0;
  left: 0;
  display: grid;
  min-width: 136px;
  width: max-content;
  gap: 0;
  padding: 0;
  border: 1px solid rgba(47, 43, 38, 0.18);
  border-radius: 5px;
  background: #fffdf7;
  box-shadow:
    0 8px 18px rgba(47, 43, 38, 0.14),
    0 2px 6px rgba(47, 43, 38, 0.08);
  overflow: hidden;
  transform-origin: 18px 20px;
}

.search-type-menu-enter-active,
.search-type-menu-leave-active {
  transition:
    opacity 160ms ease,
    transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.search-type-menu-enter-from,
.search-type-menu-leave-to {
  opacity: 0;
  transform: scale(0.96);
}

.search-type-option {
  height: 38px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--paper-ink);
  cursor: pointer;
  font: inherit;
  font-size: 15px;
  font-weight: 400;
  line-height: 1;
  padding: 0 18px;
  text-align: center;
  white-space: nowrap;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}

.search-type-option:hover,
.search-type-option:focus-visible {
  /* background: rgba(239, 238, 236, 0.86); */
  background: rgba(138, 117, 99, 0.07);

  /* background: #EFEEEC; */
  color: var(--paper-ink);
}

.search-type-option.active {
  color: var(--paper-ink);
  font-weight: 400;
}

.search-box .el-input {
  flex: 1;
  min-width: 0;
}

.search-box :deep(.el-input__wrapper) {
  height: 40px;
  border-radius: 0 !important;
}

.search-box:hover:not(.open) :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px rgba(116, 107, 97, 0.42) inset;
}

.search-box :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(138, 117, 99, 0.44) inset;
}

.search-box.open :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px rgba(138, 117, 99, 0.44) inset;
}

.search-submit {
  flex: 0 0 46px;
  width: 46px;
  height: 40px;
  margin-left: 0 !important;
  border-radius: 0 5px 5px 0 !important;
  padding: 0 !important;
}

.search-submit :deep(.action-icon) {
  width: 1.15em;
  height: 1.15em;
}

.search-section {
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  gap: 0;
  height: 100%;
  max-width: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  padding-right: 10px;
  background: #fff;
}

.search-empty {
  display: grid;
  min-height: 240px;
  align-content: center;
  justify-items: center;
  color: var(--paper-muted);
  text-align: center;
}

.search-empty h3 {
  margin: 0 0 10px;
  color: var(--paper-ink);
  font-size: 20px;
  font-weight: 600;
}

.search-empty p {
  margin: 0;
}

.search-results-layout {
  display: grid;
  grid-template-columns: minmax(180px, 210px) minmax(0, 1fr) minmax(180px, 210px);
  gap: 18px;
  max-width: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.search-summary {
  align-self: start;
  padding: 12px 2px;
  color: var(--paper-muted);
  font-size: 15px;
  line-height: 1.65;
}

.search-summary p {
  margin: 0;
}

.search-summary p + p {
  margin-top: 6px;
}

.summary-stat {
  color: var(--paper-ink);
  /* color: var(--paper-muted); */
  font-size: 30px;
  font-weight: 700;
  line-height: 1.5;
}

.summary-range {
  display: grid;
  gap: 0;
}

.search-timeline {
  position: relative;
  height: 100%;
  max-width: 100%;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 12px 10px 12px 2px;
  scrollbar-width: thin;
  scrollbar-color: rgba(138, 117, 99, 0.2) transparent;
}

.search-timeline::-webkit-scrollbar {
  width: 6px;
}

.search-timeline::-webkit-scrollbar-track {
  background: transparent;
}

.search-timeline::-webkit-scrollbar-thumb {
  background: rgba(138, 117, 99, 0.2);
}

.timeline-item {
  position: relative;
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  color: var(--paper-muted);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  line-height: 1.25;
  padding: 9px 8px 9px 0;
  text-align: left;
  transition:
    background-color 160ms ease,
    color 160ms ease,
    transform 160ms ease;
}

.timeline-item::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 10px;
  width: 1px;
  background: rgba(138, 117, 99, 0.16);
  content: '';
}

.timeline-item:first-child::before {
  top: 50%;
}

.timeline-item:last-child::before {
  bottom: 50%;
}

.timeline-item:only-child::before {
  display: none;
}

.timeline-item:hover,
.timeline-item:focus-visible,
.timeline-item.active {
  color: var(--paper-accent-dark);
}

.timeline-item:hover,
.timeline-item:focus-visible {
  background: rgba(255, 253, 247, 0.36);
}

.timeline-item.active {
  font-weight: 720;
}

.timeline-dot {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  width: 21px;
  height: 21px;
  border: 1px solid transparent;
  border-radius: 50%;
  background: #fffdf8;
}

.timeline-dot::after {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(138, 117, 99, 0.32);
  content: '';
}

.timeline-item.active .timeline-dot {
  border-color: rgba(138, 117, 99, 0.24);
  background: rgba(255, 253, 247, 0.88);
}

.timeline-item.active .timeline-dot::after {
  background: var(--paper-accent-dark);
}

.timeline-date {
  min-width: 0;
  overflow: hidden;
  padding-bottom: 2px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-item.active .timeline-date {
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 5px;
}

.search-date-strip {
  display: none;
}

.search-results-list {
  gap: 28px;
  max-width: 100%;
  min-height: 0;
  min-width: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-left: 16px;
  scroll-padding-top: 10px;
  scrollbar-width: thin;
  scrollbar-color: rgba(138, 117, 99, 0.24) transparent;
}

.search-results-list::-webkit-scrollbar {
  width: 8px;
}

.search-results-list::-webkit-scrollbar-track {
  background: transparent;
}

.search-results-list::-webkit-scrollbar-thumb {
  background: rgba(138, 117, 99, 0.24);
}

.search-result-group {
  display: grid;
  gap: 16px;
}

.search-group-diaries {
  display: grid;
  gap: 8px;
}

.search-group-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 0 2px;
}

.search-group-heading h3 {
  margin: 0;
  color: var(--paper-ink);
  font-size: 18px;
  font-weight: 720;
  line-height: 1.3;
}

.search-group-heading p {
  margin: 0;
  color: var(--paper-muted);
  font-size: 14px;
}

.diary-section {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding-right: 10px;
  background: #fff;
}

.section-header {
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: calc(var(--diary-header-height) + 6px);
  padding: 0 2px 6px;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.section-meta {
  display: flex;
  min-width: 0;
  min-height: var(--diary-header-height);
  align-items: center;
  gap: 14px;
  justify-content: center;
  white-space: nowrap;
}

.section-header h2 {
  flex: 0 1 auto;
  margin: 0;
  color: #2f2b26;
  font-size: 22px;
  font-weight: 560;
  line-height: 1.25;
}

.section-header p {
  flex: 0 0 auto;
  margin: 0;
  color: var(--paper-muted);
  white-space: nowrap;
}

.create-link {
  /* display: inline-grid; */
  flex: 0 0 auto;
  /* width: 40px;
  height: 40px;
  place-items: center; */
  border: 0;
  border-radius: 5px;
  background: #EFEEEC;
  color: var(--paper-ink);
  cursor: pointer;
  font: inherit;
  font-size: 20px;
  font-weight: 450;
  padding: 4px 16px;
  white-space: nowrap;
  transition:
    background-color 160ms ease,
    color 160ms ease,
    transform 160ms ease;
}

.create-link:hover,
.create-link:focus-visible {
  background: rgba(239, 238, 236, 0.72);
  color: var(--paper-ink);
  transform: scale(1.06);
}

.create-link .action-icon {
  width: 1.15em;
  height: 1.15em;
}

.empty-record {
  display: flex;
  min-height: 208px;
  align-items: flex-start;
  justify-content: center;
  padding-top: 170px;
  color: var(--paper-muted);
  font-size: 18px;
}

.calendar-list-frame {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: rgba(138, 117, 99, 0.24) transparent;
}

.calendar-list-enter-active,
.calendar-list-leave-active {
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.calendar-list-enter-from,
.calendar-list-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

.diary-list {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 100%;
  padding-bottom: 25px;
  padding-top: 10px;
  padding-right: 25px;
  padding-left: 25px;
}

.calendar-diary-list {
  gap: 8px;
  padding-right: 0;
  padding-left: 0;
}

.search-results-list {
  gap: 16px;
}

.search-results-list .diary-card {
  height: 160px;
}

.search-results-list .diary-card.no-image {
  height: auto;
  min-height: 124px;
}

@media (prefers-reduced-motion: reduce) {
  .calendar-list-enter-active,
  .calendar-list-leave-active,
  .search-type-menu-enter-active,
  .search-type-menu-leave-active,
  .diary-card,
  .diary-card::before,
  .diary-card::after {
    transition: opacity 100ms ease;
  }

  .calendar-list-enter-from,
  .calendar-list-leave-to,
  .search-type-menu-enter-from,
  .search-type-menu-leave-to {
    transform: none;
  }

  .diary-card:hover,
  .diary-card:focus-visible,
  .diary-card:active {
    transform: none;
  }
}

.calendar-list-frame::-webkit-scrollbar {
  width: 8px;
}

.calendar-list-frame::-webkit-scrollbar-track {
  background: transparent;
}

.calendar-list-frame::-webkit-scrollbar-thumb {
  background: rgba(138, 117, 99, 0.24);
}

.diary-card {
  position: relative;
  cursor: pointer;
  height: 188px;
  max-height: none;
  overflow: hidden;
  box-shadow: none;
  transition:
    background-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.diary-card::before,
.diary-card::after {
  position: absolute;
  z-index: 1;
  width: 16px;
  height: 16px;
  border-color: rgba(163, 183, 204, 1);
  content: '';
  pointer-events: none;
}

.diary-card::before {
  top: 8px;
  left: 8px;
  border-top: 2px solid;
  border-left: 2px solid;
  border-color: rgba(163, 183, 204, 0.4);
  transition: border-color 180ms ease;
}

.diary-card::after {
  right: 8px;
  bottom: 8px;
  border-right: 2px solid;
  border-bottom: 2px solid;
  border-color: rgba(163, 183, 204, 0.4);
  transition: border-color 180ms ease;
}

.diary-card:hover,
.diary-card:focus-visible {
  box-shadow: 0 12px 26px -22px rgba(47, 43, 38, 0.46);
  transform: translateY(-2px);
}

.diary-card:hover::before,
.diary-card:hover::after,
.diary-card:focus-visible::before,
.diary-card:focus-visible::after {
  border-color: rgba(163, 183, 204, 0.72);
}

.diary-card:active {
  transform: translateY(0);
}

.diary-card :deep(.el-card__body) {
  height: 100%;
  padding: 22px 24px 24px;
  max-height: none;
  overflow: hidden;
}

.diary-card-content {
  display: grid;
  height: 100%;
  grid-template-columns: minmax(0, 1fr) 168px;
  gap: 20px;
  align-items: stretch;
}

.diary-card.no-image .diary-card-content {
  grid-template-columns: 1fr;
}

.diary-card-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.diary-card-copy h3 {
  margin: 0 0 10px;
  overflow: hidden;
  color: var(--paper-ink);
  font-size: 20px;
  font-weight: 720;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: var(--paper-ink);
  line-height: 1.7;
  white-space: pre-wrap;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.diary-card.no-title .summary {
  -webkit-line-clamp: 6;
}

.diary-card.no-title.no-image .summary {
  -webkit-line-clamp: 7;
}

:deep(.el-calendar__body) {
  padding: 10px 6px 14px;
}

:deep(.el-calendar) {
  background: transparent;
}

:deep(.el-calendar__header) {
  align-items: stretch;
  display: grid;
  grid-template-columns: max-content max-content;
  justify-content: space-between;
  column-gap: clamp(4px, 1vw, 12px);
  min-height: calc(var(--diary-header-height) + 14px);
  padding: 0 0 14px;
  border-bottom-color: rgba(138, 117, 99, 0.1);
}

:deep(.el-calendar__button-group) {
  display: flex;
  flex-wrap: nowrap;
  min-height: var(--diary-header-height);
  align-items: center;
  overflow-x: visible;
  white-space: nowrap;
}

.calendar-title {
  display: flex;
  min-width: max-content;
  min-height: var(--diary-header-height);
  align-items: center;
  color: #2f2b26;
  font-size: 22px;
  font-weight: 560;
  line-height: 1.25;
  white-space: nowrap;
}

.calendar-actions {
  display: flex;
  min-height: var(--diary-header-height);
  align-items: center;
  white-space: nowrap;
}

.calendar-action {
  display: inline-flex;
  height: 24px;
  align-items: center;
  border: 0;
  background: rgba(255, 254, 250, 0.42);
  /* color: #746B61; */
  color: var(--paper-ink);
  cursor: pointer;
  font-size: 13px;
  font-weight: 650;
  line-height: 1;
  padding: 0 10px;
  white-space: nowrap;
}

.calendar-action + .calendar-action {
  border-left: 1px solid rgba(138, 117, 99, 0.16);
}

.calendar-action:hover,
.calendar-action:focus-visible {
  background: rgba(255, 253, 247, 0.78);
  color: #746B61;
}

:deep(.el-calendar__button-group .el-button) {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  height: 24px;
  min-width: 0;
  padding: 0 clamp(3px, 0.75vw, 8px);
  font-size: clamp(9px, 1.05vw, 13px);
  line-height: 1;
  white-space: nowrap;
}

:deep(.el-calendar__title) {
  display: flex;
  min-width: max-content;
  min-height: var(--diary-header-height);
  align-items: center;
  color: #746B61;
  font-size: clamp(13px, 2vw, 22px);
  font-weight: 560;
  line-height: 1.25;
  white-space: nowrap;
}

:deep(.el-calendar-table td) {
  border-color: rgba(138, 117, 99, 0.08);
}

:deep(.el-calendar-day) {
  height: 56px;
  border-radius: 5px;
}

:deep(.el-calendar-table td.is-selected) {
  background: transparent;
}

:deep(.el-calendar-table td.is-selected .el-calendar-day) {
  background: var(--paper-accent-dark);
}

:deep(.el-calendar-table .el-calendar-day:hover) {
  background: rgba(255, 253, 247, 0.48);
}

.calendar-day-cell {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  flex-direction: column;
}

.day-number {
  line-height: 1;
}

:deep(.is-selected) .day-number {
  color: #fffdf8;
  font-weight: 700;
}

.record-dot {
  width: 6px;
  height: 6px;
  margin-top: 6px;
  border-radius: 0;
  background: var(--paper-accent);
}

:deep(.is-selected) .record-dot {
  background: #fffdf8;
}

@media (max-width: 900px) {
  .page {
    height: auto;
    margin-top: 0;
    min-height: calc(100dvh - 96px);
    overflow: visible;
  }

  .page.calendar-empty-page {
    min-height: 0;
  }

  .page.search-empty-page {
    min-height: calc(100dvh - 96px);
  }

  .page.search-page {
    gap: 0;
  }

  .calendar-empty-page .empty-record {
    min-height: 0;
    padding-top: 28px;
  }

  .mode-switch {
    position: sticky;
    top: var(--diary-sticky-top);
    z-index: 9;
    height: calc(var(--diary-mode-sticky-height) + var(--diary-mode-sticky-padding-top));
    align-items: center;
    margin-right: calc(var(--diary-sticky-edge) * -1);
    margin-left: calc(var(--diary-sticky-edge) * -1);
    padding-right: var(--diary-sticky-edge);
    padding-left: var(--diary-sticky-edge);
    padding-top: var(--diary-mode-sticky-padding-top);
    background: #fff;
  }

  .main-grid {
    grid-template-columns: 1fr;
    gap: 0;
    align-content: start;
    overflow: visible;
  }

  .calendar-card,
  .diary-section {
    height: auto;
    max-height: none;
    overflow: visible;
    padding-right: 0;
  }

  .search-section {
    height: auto;
    max-width: 100%;
    min-height: 0;
    min-width: 0;
    overflow: visible;
    padding-right: 0;
  }

  .search-empty-page .search-section {
    display: grid;
    min-height: calc(
      100dvh - 96px - var(--diary-mode-sticky-height) -
        var(--diary-mode-sticky-padding-top) - 48px
    );
  }

  .search-empty-page .search-empty {
    min-height: 100%;
    align-content: center;
    place-content: center;
  }

  .search-card {
    position: sticky;
    top: calc(var(--diary-sticky-top) + var(--diary-mode-sticky-height) + var(--diary-mode-sticky-padding-top));
    z-index: 8;
    min-height: 48px;
    margin-right: calc(var(--diary-sticky-edge) * -1);
    margin-left: calc(var(--diary-sticky-edge) * -1);
    padding: 7px var(--diary-sticky-edge) 8px;
    background: #fff;
  }

  .search-card::before {
    position: absolute;
    right: 0;
    top: -10px;
    left: 0;
    height: 10px;
    background: #fff;
    content: '';
    pointer-events: none;
  }

  .search-box {
    min-width: 0;
  }

  .search-box .el-input {
    min-width: 0;
  }

  .search-results-layout {
    display: block;
    max-width: 100%;
    min-width: 0;
    overflow: visible;
  }

  .search-timeline {
    display: none;
  }

  .search-summary {
    display: none;
  }

  .search-date-strip {
    box-sizing: border-box;
    display: flex;
    position: sticky;
    top: calc(var(--diary-sticky-top) + var(--diary-mode-sticky-height) + var(--diary-mode-sticky-padding-top) + 48px);
    z-index: 7;
    gap: 18px;
    width: calc(100% + (var(--diary-sticky-edge) * 2));
    max-width: calc(100% + (var(--diary-sticky-edge) * 2));
    min-width: 0;
    margin-right: calc(var(--diary-sticky-edge) * -1);
    margin-left: calc(var(--diary-sticky-edge) * -1);
    overflow-x: auto;
    overflow-y: hidden;
    overscroll-behavior-x: contain;
    padding: 8px var(--diary-sticky-edge) 14px;
    background: #fff;
    scrollbar-width: thin;
  }

  .date-chip {
    position: relative;
    flex: 0 0 auto;
    border: 0;
    background: transparent;
    color: var(--paper-muted);
    cursor: pointer;
    font: inherit;
    font-size: 14px;
    line-height: 1;
    padding: 8px 0 10px;
    white-space: nowrap;
  }

  .date-chip::after {
    position: absolute;
    right: 0;
    bottom: 2px;
    left: 0;
    height: 1px;
    background: transparent;
    content: '';
  }

  .date-chip.active {
    color: var(--paper-accent-dark);
    font-weight: 720;
  }

  .date-chip.active::after {
    background: rgba(138, 117, 99, 0.62);
  }

  .calendar-card {
    overflow: visible;
  }

  :deep(.el-calendar__body) {
    padding-bottom: 8px;
  }

  .calendar-list-frame {
    overflow: visible;
  }

  .search-results-list {
    overflow: visible;
    padding-right: 0;
  }

  .search-results-list {
    max-width: 100%;
    min-width: 0;
    padding-left: 0;
  }

  .section-header {
    position: sticky;
    top: calc(var(--diary-sticky-top) + var(--diary-mode-sticky-height) + var(--diary-mode-sticky-padding-top));
    z-index: 8;
    min-height: calc(var(--diary-header-height) + var(--diary-section-sticky-gap));
    margin-right: calc(var(--diary-sticky-edge) * -1);
    margin-left: calc(var(--diary-sticky-edge) * -1);
    padding-bottom: 0;
    padding-right: var(--diary-sticky-edge);
    padding-left: var(--diary-sticky-edge);
    padding-top: var(--diary-section-sticky-gap);
    background: #fff !important;
  }
}

@media (max-width: 720px) {
  .page {
    position: sticky;
    --diary-mode-sticky-height: 44px;
    --diary-mode-sticky-padding-top: 8px;
    /* --diary-sticky-top: var(--mobile-app-header-height, 70px); 移动端导航栏底部 */
    --diary-sticky-top: -16px;
    --diary-sticky-edge: 16px;
    gap: 4px;
    margin-top: -16px;
    min-height: calc(100dvh - var(--diary-sticky-top) - 16px);
  }

  .diary-compose-backdrop {
    padding: 16px;
  }
}

@media (max-width: 600px) {
  .search-box {
    flex-direction: row;
  }

  .search-box .el-button {
    flex: 0 0 auto;
    width: auto;
  }

  .search-box .search-submit {
    flex: 0 0 46px;
    width: 46px;
    height: 40px;
  }

  .section-header {
    gap: 10px;
  }

  .section-meta {
    gap: 10px;
  }

  .section-header h2,
  .create-link {
    font-size: 20px;
  }

  .section-header p {
    font-size: 15px;
  }

  .section-header .el-button {
    width: 100%;
  }

  .diary-card {
    height: 236px;
  }

  .search-results-list .diary-card {
    height: 176px;
  }

  .search-results-list .diary-card.no-image {
    height: auto;
    min-height: 118px;
  }

  .diary-card-content {
    grid-template-columns: minmax(0, 1fr) 120px;
    gap: 14px;
  }

  :deep(.el-calendar-day) {
    height: 42px;
  }

}
</style>
