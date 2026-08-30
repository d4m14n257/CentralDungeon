import { useMutation } from '@tanstack/react-query'

import { authApi } from './authApi'

export function useLogout() {
  return useMutation({ mutationFn: authApi.logout })
}
