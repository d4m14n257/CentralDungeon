import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useConfirm } from '@/hooks/useConfirm'

import { useUpdateProfile } from '../api/useUpdateProfile'
import { completeOnboardingSchema, type CompleteOnboardingForm } from '../schemas'
import type { Profile } from '../types'
import { ProfileFields } from './ProfileFields'

interface EditProfileDialogProps {
  /** The profile being edited: its name and country are the form's starting values. */
  profile: Pick<Profile, 'name' | 'country'>
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Edits the reader's own display name and country (ADR-10 §4), with the same fields the onboarding
 * step asks for. The dialog owns its form and its mutation (ADR-19 §6).
 *
 * @param props.profile      the current name and country
 * @param props.open         whether the dialog is showing
 * @param props.onOpenChange called when it is dismissed
 */
export function EditProfileDialog({ profile, open, onOpenChange }: EditProfileDialogProps) {
  const { t } = useTranslation('users')
  const confirm = useConfirm()
  const updateProfile = useUpdateProfile()
  const form = useForm<CompleteOnboardingForm>({
    resolver: zodResolver(completeOnboardingSchema),
    defaultValues: { name: profile.name ?? '', country: profile.country ?? '' },
  })

  function handleOpenChange(next: boolean) {
    if (!next) form.reset({ name: profile.name ?? '', country: profile.country ?? '' })
    onOpenChange(next)
  }

  async function onSubmit(values: CompleteOnboardingForm) {
    // A review before anything is written (ADR-21 §9).
    const confirmed = await confirm({
      title: t('profile.confirmTitle', { name: values.name }),
      description: t('profile.confirmDescription'),
    })
    if (!confirmed) return
    updateProfile.mutate(values, {
      onSuccess: () => {
        toast.success(t('profile.saved'))
        // The profile refetches in the background; the new values become the next starting point.
        form.reset(values)
        onOpenChange(false)
      },
    })
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={t('profile.edit')}
      description={t('profile.editDescription')}
      isDirty={form.formState.isDirty}
    >
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          <ProfileFields form={form} />
          <Button type="submit" disabled={updateProfile.isPending} className="w-full">
            {t('profile.save')}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
