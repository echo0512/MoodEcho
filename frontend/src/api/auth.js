import request from './request'

export function sendRegisterCodeApi(data) {
  return request.post('/auth/send-register-code', data)
}

export function registerApi(data) {
  return request.post('/auth/register', data)
}

export function loginApi(data) {
  return request.post('/auth/login', data)
}

export function sendResetPasswordCodeApi(data) {
  return request.post('/auth/send-reset-password-code', data)
}

export function resetPasswordApi(data) {
  return request.post('/auth/reset-password', data)
}
