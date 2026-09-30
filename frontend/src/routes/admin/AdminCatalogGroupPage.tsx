import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Navigate, useParams, useSearchParams } from 'react-router'

import { GraphTray } from '@/components/graph/GraphTray'
import { IconAction } from '@/components/IconAction'
import { PageHeader } from '@/components/PageHeader'
import { adminCatalogsPath } from '@/config/paths'
import { CanonicalPicker, CatalogGraph, useCatalogGroup, type CatalogKind } from '@/features/catalogs'

/** The three catalogs a URL can name. */
const KINDS: CatalogKind[] = ['systems', 'tags', 'platforms']

/**
 * Narrows the `:kind` segment to a catalog, or says it is not one.
 *
 * @param value the raw path segment
 * @returns the catalog, or null for anything else
 */
function toKind(value: string | undefined): CatalogKind | null {
  return KINDS.find((kind) => kind === value) ?? null
}

/**
 * Reads the other groups open next to the first one from `?with=`, in order and without repeats.
 *
 * @param raw the parameter as it came
 * @param id  the group the route opened, which is never listed twice
 * @returns the extra groups' ids
 */
function withIdsFrom(raw: string | null, id: string): string[] {
  const ids = (raw ?? '').split(',').filter((value) => value !== '' && value !== id)
  return [...new Set(ids)]
}

/**
 * /admin/catalogs/:kind/:id - one synonym group as a canvas of nodes (#275).
 *
 * The group the admin opened from the table is the first star; the proposals nobody classified yet
 * float to its left until somebody connects them to a group. Moving an alias or merging needs a
 * second group on the canvas, and the tray brings one: **the groups open are in the URL**
 * (`?with=`), like the rest of the admin screens' state (#185), so a canvas is something one admin
 * can send another.
 *
 * A value that becomes a group of its own - accepted as new, or split out - is added to `?with=`
 * too, so it lands on the canvas as a star instead of vanishing from the screen.
 *
 * The screen composes and owns nothing: the canvas, its data and its mutations are the catalogs
 * feature's, and this page hands it ids (arquitectura §3.1.5).
 */
export function AdminCatalogGroupPage() {
  const { t } = useTranslation('catalogs')
  const params = useParams()
  const [searchParams, setSearchParams] = useSearchParams()

  const kind = toKind(params.kind)
  const id = params.id ?? ''
  const withIds = withIdsFrom(searchParams.get('with'), id)
  const { data: group } = useCatalogGroup(kind ?? 'systems', kind ? id : null)

  if (!kind || id === '') {
    return <Navigate to={adminCatalogsPath()} replace />
  }

  /** Writes the extra groups back to the URL; an empty list leaves it clean. */
  function setWithIds(next: string[]) {
    const params = new URLSearchParams(searchParams)
    if (next.length === 0) params.delete('with')
    else params.set('with', next.join(','))
    setSearchParams(params, { replace: true })
  }

  function addGroup(groupId: string) {
    if (groupId === id || withIds.includes(groupId)) return
    setWithIds([...withIds, groupId])
  }

  const head = group?.[0]

  return (
    <div className="space-y-4">
      <PageHeader
        title={head ? t('admin.groupOf', { name: head.name }) : t('admin.groupTitle')}
        description={t('admin.groupDescription')}
        back={{ to: `${adminCatalogsPath()}?kind=${kind}`, label: t('admin.backToCatalogs') }}
        help="admins.catalogs"
      />

      <div className="grid gap-4 lg:grid-cols-4">
        <div className="min-w-0 lg:col-span-3">
          <CatalogGraph kind={kind} groupIds={[id, ...withIds]} onBecameGroup={addGroup} />
        </div>

        <GraphTray title={t('admin.trayTitle')} description={t('admin.trayDescription')}>
          <CanonicalPicker kind={kind} selectedId={null} onSelect={(picked) => picked && addGroup(picked.id)} excludeId={id} />

          {withIds.length > 0 && (
            <div className="space-y-2">
              <h3 className="section-label">{t('admin.trayOpenGroups')}</h3>
              <ul className="list-divided">
                {withIds.map((groupId) => (
                  <OpenGroupRow
                    key={groupId}
                    kind={kind}
                    id={groupId}
                    onClose={() => setWithIds(withIds.filter((other) => other !== groupId))}
                  />
                ))}
              </ul>
            </div>
          )}

          {/* Below md there is no canvas to drag on, only the list and its menus. */}
          <div className="hidden space-y-2 md:block">
            <h3 className="section-label">{t('admin.trayGesturesTitle')}</h3>
            <ul className="text-fg-muted list-disc space-y-1 pl-4 text-sm">
              <li>{t('admin.gestureAccept')}</li>
              <li>{t('admin.gestureReassign')}</li>
              <li>{t('admin.gestureMerge')}</li>
              <li>{t('admin.gestureSplit')}</li>
              <li>{t('admin.gestureMenu')}</li>
            </ul>
          </div>
        </GraphTray>
      </div>
    </div>
  )
}

/**
 * One extra group open on the canvas, with the action that takes it off.
 *
 * @param props.kind    which catalog
 * @param props.id      the group
 * @param props.onClose takes it off the canvas - it changes nothing in the catalog
 */
function OpenGroupRow({ kind, id, onClose }: { kind: CatalogKind; id: string; onClose: () => void }) {
  const { t } = useTranslation('catalogs')
  const { data: group } = useCatalogGroup(kind, id)
  const name = group?.[0]?.name ?? '…'

  return (
    <li className="flex items-center gap-2 px-3 py-1">
      <span className="min-w-0 flex-1 truncate text-sm">{name}</span>
      <div className="row-actions">
        <IconAction icon={<X className="size-4" />} label={t('admin.trayCloseGroup', { name })} onClick={onClose} />
      </div>
    </li>
  )
}

export { AdminCatalogGroupPage as Component }
