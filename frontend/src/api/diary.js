import request from './request'

export function createDiaryApi(data) {
  return request.post('/diaries', data)
}

export function createDiaryDraftApi(data) {
  return request.post('/diaries/draft', data)
}

export function publishDiaryDraftApi(id, data) {
  return request.put(`/diaries/${id}/publish`, data)
}

export function discardDiaryDraftApi(id) {
  return request.delete(`/diaries/${id}/draft`)
}

export function getDiaryListApi() {
  return request.get('/diaries')
}

export function getDiariesByDateApi(recordDate) {
  return request.get('/diaries/by-date', {
    params: {
      recordDate,
    },
  })
}

export function getRecordedDatesApi(params) {
  return request.get('/diaries/recorded-dates', {
    params,
  })
}

export function searchDiariesApi(keyword) {
  return request.get('/diaries/search', {
    params: {
      keyword,
    },
  })
}

export function semanticSearchDiariesApi(data) {
  return request.post('/diaries/semantic-search', data)
}

export function getRelatedDiariesApi(id, params) {
  return request.get(`/diaries/${id}/related`, { params, silentError: true })
}

export function getDiaryDetailApi(id) {
  return request.get(`/diaries/${id}`)
}

export function updateDiaryApi(id, data) {
  return request.put(`/diaries/${id}`, data)
}

export function deleteDiaryApi(id) {
  return request.delete(`/diaries/${id}`)
}

export function uploadDiaryImageApi(diaryId, file) {
  const formData = new FormData()
  formData.append('file', file)

  return request.post(`/diaries/${diaryId}/images`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export function deleteDiaryImageApi(imageId) {
  return request.delete(`/diary-images/${imageId}`)
}

export function getDiaryImageBlobApi(imageId) {
  return request.get(`/diary-images/${imageId}/view`, {
    responseType: 'blob',
  })
}
