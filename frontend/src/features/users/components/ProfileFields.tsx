import { useMemo } from 'react'
import type { UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { countriesIn } from '@/lib/countries'

import type { CompleteOnboardingForm } from '../schemas'

interface ProfileFieldsProps {
  /** The form that owns the two fields; the caller builds it and decides what submitting does. */
  form: UseFormReturn<CompleteOnboardingForm>
}

/**
 * The display name and country inputs, shared by the onboarding step and the profile edit so the
 * two cannot drift apart (ADR-10 §4). It renders fields only: the frame, the confirmation and the
 * mutation belong to whoever mounts it.
 *
 * @param props.form the form holding `name` and `country`
 */
export function ProfileFields({ form }: ProfileFieldsProps) {
  const { t, i18n } = useTranslation('users')
  // Named and sorted in the reader's language: the alphabet changes with it (ADR-23 §2).
  const countries = useMemo(() => countriesIn(i18n.language), [i18n.language])

  return (
    <>
      <FormField
        control={form.control}
        name="name"
        render={({ field }) => (
          <FormItem>
            <FormLabel>{t('profileForm.nameLabel')}</FormLabel>
            <FormControl>
              <Input placeholder={t('profileForm.namePlaceholder')} {...field} />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
      <FormField
        control={form.control}
        name="country"
        render={({ field }) => (
          <FormItem>
            <FormLabel>{t('profileForm.countryLabel')}</FormLabel>
            <Select onValueChange={field.onChange} value={field.value}>
              <FormControl>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder={t('profileForm.countryPlaceholder')} />
                </SelectTrigger>
              </FormControl>
              <SelectContent>
                {countries.map((country) => (
                  <SelectItem key={country.code} value={country.code}>
                    {country.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        )}
      />
    </>
  )
}
