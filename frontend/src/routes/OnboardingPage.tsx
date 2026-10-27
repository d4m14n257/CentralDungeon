import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Form } from '@/components/ui/form'
import { completeOnboardingSchema, ProfileFields, useCompleteOnboarding, type CompleteOnboardingForm } from '@/features/users'
import { useConfirm } from '@/hooks/useConfirm'

/**
 * The first step of a new account: display name and country, asked once (ADR-10 §4).
 *
 * It blocks, whatever the entry point: the session guard in `RootLayout` sends every account that
 * has not completed it here, and takes out of here every account that has. This screen only asks.
 */
export function OnboardingPage() {
  const { t } = useTranslation('onboarding')
  const confirm = useConfirm()
  const completeOnboarding = useCompleteOnboarding()
  const form = useForm<CompleteOnboardingForm>({
    resolver: zodResolver(completeOnboardingSchema),
    defaultValues: { name: '', country: '' },
  })

  async function onSubmit(values: CompleteOnboardingForm) {
    // A review before anything is written (ADR-21 §9): what is about to change, and what follows.
    const confirmed = await confirm({ title: t('confirmTitle', { name: values.name }), description: t('confirmDescription') })
    if (!confirmed) return
    // No navigation here: the answer lands in the `/users/me` cache and the session guard takes the
    // reader out of `/onboarding` by itself.
    completeOnboarding.mutate(values)
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
            <ProfileFields form={form} />
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
