import { useTranslation } from 'react-i18next'

import type { GameTableStatus } from '@/features/tables'

import { HelpList, HelpSection, HelpTerms } from './HelpSection'

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
  { query: 'damian', meaning: 'example1' },
  { query: '/user_name damian', meaning: 'example2' },
  { query: '/user_name damian,carlos,daniel', meaning: 'example3' },
  { query: '/user_name damian /or /discord_name dami', meaning: 'example4' },
  { query: '/user_name damian /and /discord_name dami', meaning: 'example5' },
] as const

const SEARCH_RULES = ['plain', 'field', 'commas', 'connectors', 'onlySlash', 'order', 'chips', 'debounce']
const CONTEXT_ROLES = ['player', 'master', 'admin', 'owner']

/** Lo que sirve a cualquiera, sin importar el rol. Es el índice de /help (decisiones.md #168). */
export function HelpBasicsTab() {
  const { t } = useTranslation(['help', 'tables'])

  return (
    <div className="space-y-6">
      <HelpSection id="search" title={t('help:basics.search.title')}>
        <p className="text-fg-muted text-sm">{t('help:basics.search.intro')}</p>
        <HelpList items={SEARCH_RULES.map((key) => t(`help:basics.search.${key}`))} />
        <div className="space-y-2">
          <h3 className="text-sm font-medium">{t('help:basics.search.examplesTitle')}</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-fg-muted text-xs uppercase">
                <tr>
                  <th scope="col" className="py-1 pr-4 font-medium">
                    {t('help:basics.search.exampleQuery')}
                  </th>
                  <th scope="col" className="py-1 font-medium">
                    {t('help:basics.search.exampleMeaning')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-border divide-y">
                {SEARCH_EXAMPLES.map((example) => (
                  <tr key={example.query}>
                    <td className="py-2 pr-4 align-top">
                      <code className="bg-raised rounded px-1.5 py-0.5 text-xs whitespace-nowrap">{example.query}</code>
                    </td>
                    <td className="text-fg-muted py-2 align-top">{t(`help:basics.search.${example.meaning}`)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </HelpSection>

      <HelpSection id="contexts" title={t('help:basics.contexts.title')}>
        <p className="text-fg-muted text-sm">{t('help:basics.contexts.intro')}</p>
        <HelpTerms
          termWidth="w-24"
          terms={CONTEXT_ROLES.map((role) => ({
            term: t(`help:basics.contexts.${role}Title`),
            description: t(`help:basics.contexts.${role}`),
          }))}
        />
      </HelpSection>

      <HelpSection id="table-status" title={t('help:basics.tableStatus.title')}>
        <p className="text-fg-muted text-sm">{t('help:basics.tableStatus.intro')}</p>
        <HelpTerms
          terms={TABLE_STATUSES.map((status) => ({
            term: t(`tables:status.${status}`),
            description: t(`help:basics.tableStatus.${status}`),
          }))}
        />
      </HelpSection>

      <HelpSection id="account" title={t('help:basics.account.title')}>
        <HelpList items={['login', 'onboarding', 'theme'].map((key) => t(`help:basics.account.${key}`))} />
      </HelpSection>

      <HelpSection id="notifications" title={t('help:basics.notifications.title')}>
        <HelpList items={['what', 'click', 'history'].map((key) => t(`help:basics.notifications.${key}`))} />
      </HelpSection>
    </div>
  )
}

export { HelpBasicsTab as Component }
