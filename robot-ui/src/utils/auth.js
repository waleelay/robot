import { currentToken } from '@/auth'

export function getToken() {
  return currentToken()
}

export function setToken(token) {
  return token
}

export function removeToken() {
  return undefined
}
