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
 * Mirror of UserSummaryResponse: how a person looks in a listing or in a picker. It is not derived
 * from `User` with a utility type because it is not a narrower view of the same thing — it carries
 * `discordUsername`, which `/users/me` does not return (arquitectura.md 2.3, 3.2).
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
