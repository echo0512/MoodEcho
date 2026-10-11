import request from './request'

export function getCurrentUserApi() {
  return request.get('/users/me')
}

export function getCurrentUserStatsApi() {
  return request.get('/users/me/stats')
}

export function updateNicknameApi(data) {
  return request.put('/users/me/nickname', data)
}

export function changePasswordApi(data) {
  return request.put('/users/me/password', data)
}

export function deleteAccountApi(data) {
  return request.delete('/users/me', {
    data,
  })
}
