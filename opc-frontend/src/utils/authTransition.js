export const AUTH_SUCCESS_EVENT = 'opc:user-auth-success'

export function showAuthSuccessTransition(mode, target = '/') {
  window.dispatchEvent(new CustomEvent(AUTH_SUCCESS_EVENT, {
    detail: { mode, target },
  }))
}
