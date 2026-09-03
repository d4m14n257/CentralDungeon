/** Espejo de UserDetailResponse. */
export interface User {
  id: string
  name: string | null
  country: string | null
  karma: number
  needsOnboarding: boolean
  roles: string[]
  hasManagedTables: boolean
}

/**
 * Espejo de UserSummaryResponse: cómo se ve una persona en un listado o en un selector. No se
 * deriva de `User` con un utility type porque no es una vista reducida de lo mismo — trae
 * `discordUsername`, que `/users/me` no devuelve (arquitectura.md 2.3, 3.2).
 */
export interface UserSummary {
  id: string
  discordUsername: string
  name: string | null
}

/** What onboarding sends: the display name and country that unblock the rest of the app (#134). */
export interface CompleteOnboardingInput {
  name: NonNullable<User['name']>
  country: NonNullable<User['country']>
}
