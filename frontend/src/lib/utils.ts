import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** Merges Tailwind classes so later ones win. shadcn/ui expects it at this exact path. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
