/**
 * Environment variables, read and defaulted in one place so no component ever touches
 * `import.meta.env` directly.
 */
export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
} as const
