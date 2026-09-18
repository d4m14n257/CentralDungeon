import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'
import { FormDialog } from '@/components/FormDialog'

import { changeTableStatusSchema, type ChangeTableStatusForm } from '../schemas'

interface JustifiedTableActionDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  submitLabel: string
  destructive?: boolean
  isPending: boolean
  onConfirm: (justification: string) => void
  /**
   * What the note is called on this particular act. Defaults to "Motivo".
   *
   * Resolving a request is the case that needed it: what is being written there is an answer that
   * reaches the person who asked, and calling it "motivo" would describe the wrong end of it.
   */
  justificationLabel?: string | undefined
  /**
   * What is being decided on, shown above the note — the reason somebody gave, the table's name.
   *
   * **Whatever the answer is about has to be on screen while the answer is written**, not one dialog
   * back. A node rather than a string because the caller decides what that is.
   */
  context?: ReactNode
  /**
   * The explanation of what this act does, shown above the note.
   *
   * A node rather than a `HelpLink` raised here: `features/help` is another feature and a feature
   * never imports one (arquitectura.md §3.1.5). The screen composes the two.
   */
  help?: ReactNode
  /**
   * The refusal to show over the button, already translated, or null.
   *
   * **Inline and not a toast** (#197): the form is still open and the answer belongs where the reader
   * is looking. The races this dialog cannot prevent — a colleague resolved it, the reservation ran
   * out — are exactly what lands here.
   */
  errorMessage?: string | null
}

/**
 * Every transition that demands a justification (Request changes, Cancel, and since F3.3 resolving
 * from the shared admin tray) shares this shape - one screen, abstracted no further than it earns:
 * several real uses (arquitectura.md 2.4).
 */
export function JustifiedTableActionDialog({
  open,
  onOpenChange,
  title,
  description,
  submitLabel,
  destructive,
  isPending,
  onConfirm,
  justificationLabel,
  context,
  help,
  errorMessage,
}: JustifiedTableActionDialogProps) {
  const { t } = useTranslation('master')
  const form = useForm<ChangeTableStatusForm>({
    resolver: zodResolver(changeTableStatusSchema),
    defaultValues: { justification: '' },
  })

  function handleOpenChange(next: boolean) {
    if (!next) form.reset({ justification: '' })
    onOpenChange(next)
  }

  function onSubmit(values: ChangeTableStatusForm) {
    onConfirm(values.justification)
    form.reset()
  }

  return (
    <FormDialog open={open} onOpenChange={handleOpenChange} title={title} description={description} isDirty={form.formState.isDirty}>
      <Form {...form}>
        <form onSubmit={(event) => void form.handleSubmit(onSubmit)(event)} className="space-y-4">
          {context}
          {help}
          <FormField
            control={form.control}
            name="justification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{justificationLabel ?? t('status.justificationLabel')}</FormLabel>
                <FormControl>
                  <Textarea rows={3} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          {errorMessage !== null && errorMessage !== undefined && (
            <p role="alert" className="bg-state-canceled-bg text-state-canceled-fg rounded-md px-3 py-2 text-sm">
              {errorMessage}
            </p>
          )}
          <Button type="submit" variant={destructive ? 'destructive' : 'default'} disabled={isPending} className="w-full">
            {submitLabel}
          </Button>
        </form>
      </Form>
    </FormDialog>
  )
}
