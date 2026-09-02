import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Card, CardContent, CardHeader } from '@/components/ui/card'
import type { GameTableStatus } from '@/features/tables'

const TABLE_STATUSES: GameTableStatus[] = [
  'Unassigned',
  'Preparation',
  'ChangesRequested',
  'Opened',
  'InProgress',
  'PauseRequested',
  'Pause',
  'Finished',
  'Canceled',
]

const SEARCH_EXAMPLES = [
  { query: 'damian', meaning: 'search.example1' },
  { query: '/user_name damian', meaning: 'search.example2' },
  { query: '/user_name damian,carlos,daniel', meaning: 'search.example3' },
  { query: '/user_name damian /or /discord_name dami', meaning: 'search.example4' },
  { query: '/user_name damian /and /discord_name dami', meaning: 'search.example5' },
] as const

/**
 * La pantalla que explica el sitio a quien lo usa (decisiones.md #167). Nace por el lenguaje de
 * búsqueda —que es potente y no se adivina— pero no es una página del buscador: cubre lo que hay
 * que entender para moverse, y crece con cada etapa.
 *
 * Sin queries: es contenido, y todo su texto sale de locales/es/help.json (#117).
 */
export function HelpPage() {
  const { t } = useTranslation(['help', 'tables'])

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="font-serif text-2xl font-semibold">{t('help:title')}</h1>
        <p className="text-fg-muted text-sm">{t('help:intro')}</p>
      </div>

      <HelpSection title={t('help:search.title')}>
        <p className="text-fg-muted text-sm">{t('help:search.intro')}</p>
        <ul className="space-y-2 text-sm">
          {['plain', 'field', 'commas', 'connectors', 'onlySlash', 'order', 'chips', 'debounce'].map((key) => (
            <li key={key} className="flex gap-2">
              <span className="text-fg-subtle" aria-hidden>
                ·
              </span>
              <span>{t(`help:search.${key}`)}</span>
            </li>
          ))}
        </ul>
        <div className="space-y-2">
          <h3 className="text-sm font-medium">{t('help:search.examplesTitle')}</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-fg-muted text-xs uppercase">
                <tr>
                  <th scope="col" className="py-1 pr-4 font-medium">
                    {t('help:search.exampleQuery')}
                  </th>
                  <th scope="col" className="py-1 font-medium">
                    {t('help:search.exampleMeaning')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-border divide-y">
                {SEARCH_EXAMPLES.map((example) => (
                  <tr key={example.query}>
                    <td className="py-2 pr-4 align-top">
                      <code className="bg-raised rounded px-1.5 py-0.5 text-xs whitespace-nowrap">{example.query}</code>
                    </td>
                    <td className="text-fg-muted py-2 align-top">{t(`help:${example.meaning}`)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </HelpSection>

      <HelpSection title={t('help:contexts.title')}>
        <p className="text-fg-muted text-sm">{t('help:contexts.intro')}</p>
        <dl className="space-y-2 text-sm">
          {['player', 'master', 'admin', 'owner'].map((role) => (
            <div key={role} className="flex flex-col gap-0.5 sm:flex-row sm:gap-3">
              <dt className="w-24 shrink-0 font-medium">{t(`help:contexts.${role}Title`)}</dt>
              <dd className="text-fg-muted">{t(`help:contexts.${role}`)}</dd>
            </div>
          ))}
        </dl>
      </HelpSection>

      <HelpSection title={t('help:tableStatus.title')}>
        <p className="text-fg-muted text-sm">{t('help:tableStatus.intro')}</p>
        <dl className="space-y-2 text-sm">
          {TABLE_STATUSES.map((status) => (
            <div key={status} className="flex flex-col gap-0.5 sm:flex-row sm:gap-3">
              <dt className="w-40 shrink-0 font-medium">{t(`tables:status.${status}`)}</dt>
              <dd className="text-fg-muted">{t(`help:tableStatus.${status}`)}</dd>
            </div>
          ))}
        </dl>
      </HelpSection>

      <HelpSection title={t('help:registrations.title')}>
        <ul className="text-fg-muted space-y-2 text-sm">
          {['apply', 'queue', 'decision', 'full'].map((key) => (
            <li key={key}>{t(`help:registrations.${key}`)}</li>
          ))}
        </ul>
      </HelpSection>

      <HelpSection title={t('help:masters.title')}>
        <ul className="text-fg-muted space-y-2 text-sm">
          {['one', 'primary', 'assign'].map((key) => (
            <li key={key}>{t(`help:masters.${key}`)}</li>
          ))}
        </ul>
      </HelpSection>
    </div>
  )
}

/** El título va en un `h2` real y no en el `div` de `CardTitle`: es una página para leer de arriba abajo. */
function HelpSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <h2 className="font-serif text-lg leading-none font-semibold">{title}</h2>
      </CardHeader>
      <CardContent className="space-y-4">{children}</CardContent>
    </Card>
  )
}

export { HelpPage as Component }
