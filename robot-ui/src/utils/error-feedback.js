import { Message } from 'element-ui'
import { isCanceledRequest, requestErrorLevel, requestErrorMessage } from '@/utils/request-error'

const displayedErrors = new WeakSet()

export function notifyActionError(error, fallbackMessage = '操作失败，请稍后重试') {
  if (isCanceledRequest(error)) return false
  if (error && typeof error === 'object' && displayedErrors.has(error)) return false

  const message = requestErrorMessage(error, fallbackMessage)
  Message({
    message,
    type: requestErrorLevel(error),
    duration: 5000
  })
  if (error && typeof error === 'object') displayedErrors.add(error)
  return true
}
