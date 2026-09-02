import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Crown, X } from 'lucide-react'

import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useAssignMasters } from '@/features/tables'
import { UserPicker, type UserSummary } from '@/features/users'

interface AssignMastersDialogProps {
  tableId: string
  tableName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

/**
 * Vive en `routes/` y no en `features/tables/` porque compone dos dominios —el buscador de personas
 * es de `users`, la asignación es de `tables`— y una feature nunca importa de otra (arquitectura.md
 * 3.1.5). Es la misma razón por la que `MasterTableStatusTab` está acá al lado.
 *
 * **El orden es el rol**: el primero que se agrega es el Primary y el resto Secondary, y tocar un
 * chip lo asciende (decisiones.md #165). Una mesa tiene un solo Primary (modelo-datos.md #73), así
 * que ascender a uno degrada al que estaba — que es exactamente lo que hace mover el chip al frente.
 * Sin formulario ni zod: no hay ningún campo que validar, solo una lista con orden.
 */
export function AssignMastersDialog({ tableId, tableName, open, onOpenChange }: AssignMastersDialogProps) {
  const { t } = useTranslation('admin')
  const assignMasters = useAssignMasters()
  const [selected, setSelected] = useState<UserSummary[]>([])

  const [primary, ...secondaries] = selected

  function handleOpenChange(next: boolean) {
    if (!next) setSelected([])
    onOpenChange(next)
  }

  function add(user: UserSummary) {
    setSelected((current) => (current.some((candidate) => candidate.id === user.id) ? current : [...current, user]))
  }

  function remove(userId: string) {
    setSelected((current) => current.filter((user) => user.id !== userId))
  }

  function promote(userId: string) {
    setSelected((current) => {
      const promoted = current.find((user) => user.id === userId)
      return promoted ? [promoted, ...current.filter((user) => user.id !== userId)] : current
    })
  }

  function submit() {
    if (!primary) return
    assignMasters.mutate(
      { tableId, request: { primaryUserId: primary.id, secondaryUserIds: secondaries.map((user) => user.id) } },
      {
        onSuccess: () => {
          toast.success(t('tables.assignMastersSuccess'))
          setSelected([])
          onOpenChange(false)
        },
      },
    )
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={handleOpenChange}
      title={t('tables.assignMastersDialogTitle', { name: tableName })}
      description={t('tables.assignMastersDialogDescription')}
    >
      <div className="space-y-4">
        <div className="space-y-2">
          <p className="text-sm font-medium">{t('tables.selectedMastersLabel')}</p>
          {selected.length === 0 ? (
            <p className="text-fg-subtle text-sm">{t('tables.noMastersSelected')}</p>
          ) : (
            <ul className="flex flex-wrap gap-2">
              {selected.map((user, index) => (
                <li key={user.id}>
                  <MasterChip
                    user={user}
                    isPrimary={index === 0}
                    primaryLabel={t('tables.primaryBadge')}
                    promoteLabel={t('tables.makePrimary', { name: user.discordUsername })}
                    removeLabel={t('tables.removeMaster', { name: user.discordUsername })}
                    onPromote={() => promote(user.id)}
                    onRemove={() => remove(user.id)}
                  />
                </li>
              ))}
            </ul>
          )}
          <p className="text-fg-subtle text-xs">{t('tables.mastersOrderHint')}</p>
        </div>

        <UserPicker onSelect={add} excludedIds={selected.map((user) => user.id)} />

        <Button type="button" onClick={submit} disabled={!primary || assignMasters.isPending} className="w-full">
          {t('tables.assignMasters')}
        </Button>
      </div>
    </FormDialog>
  )
}

interface MasterChipProps {
  user: UserSummary
  isPrimary: boolean
  primaryLabel: string
  promoteLabel: string
  removeLabel: string
  onPromote: () => void
  onRemove: () => void
}

function MasterChip({ user, isPrimary, primaryLabel, promoteLabel, removeLabel, onPromote, onRemove }: MasterChipProps) {
  return (
    <span
      className={cn(
        'flex items-center gap-1 rounded-full py-1 pr-1 pl-2 text-xs',
        isPrimary ? 'bg-state-active-bg text-state-active-fg' : 'bg-raised text-fg',
      )}
    >
      {isPrimary && <Crown className="size-3" aria-label={primaryLabel} />}
      {isPrimary ? (
        <span className="font-medium">{user.discordUsername}</span>
      ) : (
        <button type="button" onClick={onPromote} title={promoteLabel} aria-label={promoteLabel} className="hover:underline">
          {user.discordUsername}
        </button>
      )}
      <button type="button" onClick={onRemove} aria-label={removeLabel} className="hover:text-fg-muted rounded-full">
        <X className="size-3" />
      </button>
    </span>
  )
}
