export const MAX_DIARY_CONTENT_LENGTH = 10000
export const MAX_DIARY_IMAGE_COUNT = 9

export const getDiaryPlainText = (content) => {
  const div = document.createElement('div')
  div.innerHTML = content || ''
  div.querySelectorAll('img').forEach((image) => image.remove())

  return div.textContent.trim()
}

export const getDiaryCharacterCount = (content) => {
  const div = document.createElement('div')
  div.innerHTML = content || ''
  div.querySelectorAll('img').forEach((image) => image.remove())

  return div.textContent.length
}

export const isDiaryContentTooLong = (content) =>
  getDiaryCharacterCount(content) > MAX_DIARY_CONTENT_LENGTH

export const isDiaryBlank = ({ title, content, imageCount }) =>
  !title.trim() && !getDiaryPlainText(content) && imageCount === 0
