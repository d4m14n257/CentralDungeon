import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { paths } from '@/config/paths'
import { completeOnboardingSchema, useCompleteOnboarding, type CompleteOnboardingForm } from '@/features/users'
import { countriesIn } from '@/lib/countries'

/**
 * The blocking first step: display name and country (#134). Nothing else in the app is reachable
 * until it is done, which is why the redirect lives in the session guard and not in each screen.
 */
export function OnboardingPage() {
  const { t, i18n } = useTranslation('onboarding')
  // Named and sorted in the reader's language: the alphabet changes with it (#198).
  const countries = useMemo(() => countriesIn(i18n.language), [i18n.language])
  const navigate = useNavigate()
  const completeOnboarding = useCompleteOnboarding()
  const form = useForm<CompleteOnboardingForm>({
    resolver: zodResolver(completeOnboardingSchema),
    defaultValues: { name: '', country: '' },
  })

  function onSubmit(values: CompleteOnboardingForm) {
    completeOnboarding.mutate(values, {
      onSuccess: () => navigate(paths.home),
    })
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>{t('title')}</CardTitle>
        <CardDescription>{t('subtitle')}</CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('nameLabel')}</FormLabel>
                  <FormControl>
                    <Input placeholder={t('namePlaceholder')} {...field} />
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
                  <FormLabel>{t('countryLabel')}</FormLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <FormControl>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t('countryPlaceholder')} />
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
            <Button type="submit" disabled={completeOnboarding.isPending} className="w-full">
              {t('submit')}
            </Button>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}

export { OnboardingPage as Component }
