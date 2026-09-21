import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { ForbiddenState } from '@/components/ForbiddenState'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { HelpLink } from '@/features/help'
import { SettingHistory, SettingValueDialog, useSystemSettings, type SettingCategory, type SystemSetting } from '@/features/settings'
import { useDisclosure } from '@/hooks/useDisclosure'
import { browserTimeZone, formatDateTime } from '@/lib/date'
import { ApiError } from '@/types/api'

/**
 * The order the categories are read in — the same one the backend sends, written down so the screen
 * does not depend on the array's order to group correctly.
 *
 * `Business` first because it is the half that changes what the platform does *to people*, and
 * `Limits` second because caps and quotas are the operational half. Two groups and not three: the
 * `Texts` of #141 does not exist, and `SettingCategory` says why.
 */
const CATEGORY_ORDER: readonly SettingCategory[] = ['Business', 'Limits']

/**
 * `/admin/settings` — the values the community changes without a deploy (#141).
 *
 * **Grouped by category, with the current value and the default side by side**, which is what the
 * slice was asked for. The default beside the value is not decoration: an admin looking at `15` has
 * no other way to tell whether somebody set it there or whether it has always been that, and no way
 * to put it back without finding the number in the source.
 *
 * **Cards and not a `DataTable`.** The wide-table pattern of frontend-diseno.md §5.b is for listings
 * that are read a row at a time and searched; this is four rows that are *read whole* — each one is a
 * label, an explanation, a number, its default, its range and who last touched it. A table would put
 * the explanation in a column nobody sizes correctly and turn into cards below `md` anyway.
 *
 * **No search box and no pagination**, deliberately: there are four settings and adding one is a line
 * of an enum in the backend. A box over four rows solves a problem this screen does not have
 * (`/admin/tables` is the screen that searches).
 *
 * **No role guard in front of it** (#103), like every other admin screen: somebody who forces the
 * route without the rank gets a `403` from the backend and lands on `ForbiddenState`, which is an
 * explanation rather than a blank page. Editing settings is a row of the matrix where `Admin` and
 * `Owner` are the same (fase-3-admin-owner.md §3), so there is no capability question here at all —
 * which is why, unlike `/admin/users`, no button is ever conditionally absent.
 */
export function AdminSettingsPage() {
  const { t, i18n } = useTranslation('admin')
  const timeZone = browserTimeZone()

  const { data, isPending, isLoadingError, error, refetch } = useSystemSettings()

  const editDialog = useDisclosure<SystemSetting>()
  const historyDialog = useDisclosure<SystemSetting>()

  if (error instanceof ApiError && error.status === 403) {
    return <ForbiddenState />
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-serif text-2xl font-semibold">{t('settings.title')}</h1>
        <HelpLink section="admins.settings" className="text-sm">
          {t('settings.helpLink')}
        </HelpLink>
      </div>
      <p className="text-fg-muted text-sm">{t('settings.description')}</p>

      {isPending && <Skeleton className="h-64 w-full" />}
      {/* isLoadingError, not isError: a failed background refetch must not blank a screen that is
          already showing the values (#150). */}
      {isLoadingError && <ErrorState onRetry={() => void refetch()} />}
      {/* Only reachable if the backend ever published an empty catalogue, which would mean every
          setting had been removed - a real thing to say rather than a blank page. */}
      {data && data.length === 0 && <EmptyState title={t('settings.emptyTitle')} description={t('settings.emptyDescription')} />}

      {data &&
        data.length > 0 &&
        CATEGORY_ORDER.map((category) => {
          const rows = data.filter((setting) => setting.category === category)
          if (rows.length === 0) return null
          return (
            <section key={category} className="space-y-3">
              <h2 className="text-fg-muted text-sm font-medium tracking-wide uppercase">{t(`settings.categories.${category}`)}</h2>
              <ul className="space-y-3">
                {rows.map((setting) => (
                  <li key={setting.key} className="border-border bg-raised space-y-2 rounded-lg border p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="space-y-1">
                        <p className="font-medium">{t(`settings.keys.${setting.key}.label`)}</p>
                        <p className="text-fg-muted text-sm">{t(`settings.keys.${setting.key}.description`)}</p>
                      </div>
                      <div className="flex shrink-0 gap-2">
                        <Button size="sm" variant="outline" onClick={() => editDialog.open(setting)}>
                          {t('settings.change')}
                        </Button>
                        {/* Reading the record is not a change, so it is offered on every row. */}
                        <Button size="sm" variant="ghost" onClick={() => historyDialog.open(setting)}>
                          {t('settings.history')}
                        </Button>
                      </div>
                    </div>

                    <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1 text-sm">
                      <span className="text-fg text-lg font-semibold">
                        {t('settings.valueWithUnit', { value: setting.value, unit: t(`settings.keys.${setting.key}.unit`) })}
                      </span>
                      {/* The default, always - including when it is what is in force. "Same as the
                          default" is an answer somebody came for, not a blank. */}
                      <span className="text-fg-muted">{t('settings.defaultHint', { value: setting.defaultValue })}</span>
                      <span className="text-fg-subtle text-xs">
                        {t('settings.rangeHint', { min: setting.minValue, max: setting.maxValue })}
                      </span>
                    </div>

                    {/* `overridden` and not `value !== defaultValue`: somebody may set a value to
                        exactly its default, and that is a decision they made and signed. */}
                    <p className="text-fg-subtle text-xs">
                      {setting.overridden && setting.updatedByName !== null && setting.updatedAt !== null
                        ? t('settings.setBy', {
                            name: setting.updatedByName,
                            when: formatDateTime(setting.updatedAt, i18n.language, timeZone),
                          })
                        : t('settings.neverChanged')}
                    </p>

                    {/* On the card and not only inside the dialog: somebody scanning this screen for
                        something to adjust should see which one is not a cosmetic adjustment before
                        they decide to open it (#44, #141). */}
                    {setting.retroactive && (
                      <p className="bg-state-paused-bg text-state-paused-fg rounded-md px-3 py-2 text-xs">
                        {t(`settings.keys.${setting.key}.retroactiveWarning`)}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          )
        })}

      {/* The dialog gets its help as a node rather than raising it itself: `HelpLink` lives in
          `features/help`, and a feature never imports another one (§3.1.5). */}
      <SettingValueDialog
        setting={editDialog.item ?? null}
        open={editDialog.isOpen}
        onOpenChange={(open) => !open && editDialog.close()}
        help={
          <p className="text-fg-subtle text-xs">
            {t('settings.changeHelpHint')} <HelpLink section="admins.settings">{t('settings.changeHelpLink')}</HelpLink>
          </p>
        }
      />

      <Dialog open={historyDialog.isOpen} onOpenChange={(open) => !open && historyDialog.close()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t('settings.historyDialogTitle', {
                name: historyDialog.item ? t(`settings.keys.${historyDialog.item.key}.label`) : '',
              })}
            </DialogTitle>
            <DialogDescription>{t('settings.historyDialogDescription')}</DialogDescription>
          </DialogHeader>
          {/* A key and not the row (§3.1.5): the panel asks for its own data, so what it draws is the
              record as it is now rather than as the page last saw it. */}
          {historyDialog.item && <SettingHistory settingKey={historyDialog.item.key} />}
        </DialogContent>
      </Dialog>
    </div>
  )
}

export { AdminSettingsPage as Component }
