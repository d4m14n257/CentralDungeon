import { useTranslation } from 'react-i18next'

import type { GameTableStatus } from '@/features/tables'

import { HelpList, HelpSteps, HelpTerms } from '../components/HelpBlocks'

/**
 * The help that serves anybody, whatever their role.
 *
 * Each export is the **body** of a section, without its heading: the heading is the dialog's title
 * and lives in the registry, so a section cannot end up announced twice (#231).
 */

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

/**
 * The rules of the search language, in the order they are read (#164).
 *
 * `naming` and `choices` sit right after `field` because they are what somebody needs the moment
 * they first press `/`: what the commands are called, and that some of them are picked from a list
 * instead of typed.
 */
const SEARCH_RULES = ['plain', 'field', 'naming', 'choices', 'commas', 'connectors', 'onlySlash', 'order', 'chips', 'debounce']
const CONTEXT_ROLES = ['player', 'master', 'admin', 'owner']

/** How the search query language works, with its rules and worked examples. */
export function SearchHelp() {
  const { t } = useTranslation('help')

  return (
    <>
      <p className="text-fg-muted text-sm">{t('basics.search.intro')}</p>
      <HelpList items={SEARCH_RULES.map((key) => t(`basics.search.${key}`))} />
      <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4, 5].map((n) => t(`basics.search.steps.step${n}`))} />
      <div className="space-y-2">
        <h3 className="text-sm font-medium">{t('basics.search.examplesTitle')}</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-fg-muted text-xs uppercase">
              <tr>
                <th scope="col" className="py-1 pr-4 font-medium">
                  {t('basics.search.exampleQuery')}
                </th>
                <th scope="col" className="py-1 font-medium">
                  {t('basics.search.exampleMeaning')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-border divide-y">
              {SEARCH_EXAMPLES.map((example) => (
                <tr key={example.query}>
                  <td className="py-2 pr-4 align-top">
                    <code className="bg-raised rounded px-1.5 py-0.5 text-xs whitespace-nowrap">{example.query}</code>
                  </td>
                  <td className="text-fg-muted py-2 align-top">{t(`basics.search.${example.meaning}`)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  )
}

/** What the four contexts are and how the switcher moves between them. */
export function ContextsHelp() {
  const { t } = useTranslation('help')

  return (
    <>
      <p className="text-fg-muted text-sm">{t('basics.contexts.intro')}</p>
      <HelpTerms
        termWidth="w-24"
        terms={CONTEXT_ROLES.map((role) => ({
          term: t(`basics.contexts.${role}Title`),
          description: t(`basics.contexts.${role}`),
        }))}
      />
      <HelpSteps title={t('stepsTitle')} items={[1, 2, 3].map((n) => t(`basics.contexts.steps.step${n}`))} />
    </>
  )
}

/** What each of the nine table statuses means. */
export function TableStatusHelp() {
  const { t } = useTranslation(['help', 'tables'])

  return (
    <>
      <p className="text-fg-muted text-sm">{t('help:basics.tableStatus.intro')}</p>
      <HelpTerms
        terms={TABLE_STATUSES.map((status) => ({
          term: t(`tables:status.${status}`),
          description: t(`help:basics.tableStatus.${status}`),
        }))}
      />
    </>
  )
}

/** Signing in, onboarding and the theme. */
export function AccountHelp() {
  const { t } = useTranslation('help')

  return <HelpList items={['login', 'onboarding', 'theme'].map((key) => t(`basics.account.${key}`))} />
}

/** What gets notified and where the history is. */
export function NotificationsHelp() {
  const { t } = useTranslation('help')

  return <HelpList items={['what', 'click', 'history'].map((key) => t(`basics.notifications.${key}`))} />
}
