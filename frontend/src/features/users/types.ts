/** Espejo de UserDetailResponse. Único tipo de esta feature escrito a mano. */
export interface User {
  id: string
  name: string | null
  country: string | null
  karma: number
  needsOnboarding: boolean
  roles: string[]
}

export interface CompleteOnboardingInput {
  name: NonNullable<User['name']>
  country: NonNullable<User['country']>
}
