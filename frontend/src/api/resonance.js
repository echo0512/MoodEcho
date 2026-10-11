import request from './request'

export function getResonanceTopicsApi(config) {
  return request.get('/resonance-topics', config)
}

export function createResonancePostApi(data) {
  return request.post('/resonance-posts', data)
}

export function getResonancePlazaApi(params) {
  return request.get('/resonance-posts/plaza', {
    params,
  })
}

export function getResonanceTopicPlazaApi(topicId, params) {
  return request.get(`/resonance-posts/plaza/topic/${topicId}`, {
    params,
  })
}

export function getMyResonancePostsApi() {
  return request.get('/resonance-posts/my')
}

export function withdrawResonancePostApi(id) {
  return request.put(`/resonance-posts/${id}/withdraw`)
}
