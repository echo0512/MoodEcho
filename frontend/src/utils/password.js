export const PASSWORD_ALLOWED_PATTERN = /^[A-Za-z\d_]+$/
export const PASSWORD_REQUIRED_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d_]+$/

export const createPasswordRules = (requiredMessage = '请输入密码') => [
  { required: true, message: requiredMessage, trigger: 'submit' },
  { min: 8, max: 64, message: '密码长度应为 8 到 64 位', trigger: 'submit' },
  {
    pattern: PASSWORD_REQUIRED_PATTERN,
    message: '密码须同时包含字母和数字，且只能使用字母、数字和下划线',
    trigger: 'submit',
  },
]
