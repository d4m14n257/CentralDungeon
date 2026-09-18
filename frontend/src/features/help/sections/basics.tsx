import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'

import type { GameTableStatus } from '@/features/tables'
import type { SearchField } from '@/lib/searchQuery'

import { HelpList, HelpSteps, HelpTerms } from '../components/HelpBlocks'
import type { HelpSectionBodyProps } from './registry'

/**
 * The help that serves anybody, whatever their role.
 *
 * Each export is the **body** of a section, without its heading: the heading is the dialog's title
 * and lives in the registry, so a section cannot end up announced twice (#231).
 */

const TABLE_STATUSES: GameTableStatus[] = [
  'Draft',
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

/**
 * The rules of the search language, in the order they are read (#164).
 *
 * `enter`, `naming` and `choices` sit right after `field` because they are what somebody needs the
 * moment they first press `/`: what closes a criterion (#240), what the commands are called, and that
 * some of them are picked from a list instead of typed.
 */
const SEARCH_RULES = ['plain', 'field', 'enter', 'naming', 'choices', 'commas', 'connectors', 'onlySlash', 'order', 'chips', 'debounce']
const CONTEXT_ROLES = ['player', 'master', 'admin', 'owner']

/**
 * The three things somebody can ask an admin for (#42, #90).
 *
 * Written out here rather than imported from `features/approvals`: a feature never imports another
 * one (arquitectura.md §3.1.5), and the help is text about the mechanism rather than a second
 * consumer of it. The ids are the ones the API spells, so the section and the badge cannot end up
 * naming different things.
 */
const REQUEST_KINDS = ['MasterGrant', 'TableOpen', 'General']

/** One worked query and what it finds. */
interface SearchExample {
  query: string
  meaning: string
}

/**
 * Sample values worth typing after a command.
 *
 * A command with fixed choices supplies its own — they are the real ones, and showing them is how the
 * help says what there is to pick from. A free-text one has whatever the screen declared, because
 * only the screen knows what a plausible value looks like.
 */
function samplesOf(field: SearchField): readonly string[] {
  return field.values?.map((choice) => choice.label) ?? field.examples ?? []
}

/** Whether this is the kind of command that is picked from a list — an empty list is not one. */
function hasChoices(field: SearchField): boolean {
  return field.values !== undefined && field.values.length > 0
}

/**
 * The worked examples of **this** box, built from the commands it accepts (#240).
 *
 * They used to be five fixed rows about `/user_name` and `/discord_name`, shown to everybody — so on
 * `/my/files`, where neither command exists, the help taught two things that do not work and none of
 * the three that do. Every command gets a row of its own, and the rules that need two of them —
 * commas, `/or`, `/and` — are shown with the box's own.
 */
function searchExamples(fields: readonly SearchField[], t: TFunction): SearchExample[] {
  const sample = (field: SearchField) => samplesOf(field)[0] ?? t('basics.search.sampleValue')
  const [first, second] = fields
  if (first === undefined) return []

  const examples: SearchExample[] = [{ query: sample(first), meaning: t('basics.search.examplePlain') }]
  for (const field of fields) {
    examples.push({
      query: `/${field.name} ${sample(field)}`,
      meaning: t(hasChoices(field) ? 'basics.search.exampleChoice' : 'basics.search.exampleField', { field: field.label }),
    })
  }
  // The commas need a command with more than one plausible value; whichever has them will do.
  const alternatives = fields.find((field) => samplesOf(field).length > 1)
  if (alternatives !== undefined) {
    examples.push({
      query: `/${alternatives.name} ${samplesOf(alternatives).slice(0, 3).join(',')}`,
      meaning: t('basics.search.exampleCommas', { field: alternatives.label }),
    })
  }
  if (second !== undefined) {
    const pair = `/${first.name} ${sample(first)} %s /${second.name} ${sample(second)}`
    examples.push({ query: pair.replace('%s', '/or'), meaning: t('basics.search.exampleOr') })
    examples.push({ query: pair.replace('%s', '/and'), meaning: t('basics.search.exampleAnd') })
  }
  return examples
}

/**
 * How the search query language works: its rules, which are the same everywhere, and its commands and
 * examples, which are those of the box that raised the question (#240).
 */
export function SearchHelp({ searchFields = [] }: HelpSectionBodyProps) {
  const { t } = useTranslation('help')
  const examples = searchExamples(searchFields, t)

  return (
    <>
      <p className="text-fg-muted text-sm">{t('basics.search.intro')}</p>
      <HelpList items={SEARCH_RULES.map((key) => t(`basics.search.${key}`))} />
      <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4, 5].map((n) => t(`basics.search.steps.step${n}`))} />
      {/* **The commands of this buscador, named** (#239, #240). Nobody guesses that `/file_categories`
          exists, and a list of somebody else's commands is a list of things that do not work here. */}
      {searchFields.length > 0 && (
        <div className="space-y-2">
          <h3 className="text-sm font-medium">{t('basics.search.commandsTitle')}</h3>
          <HelpTerms
            termWidth="w-44"
            terms={searchFields.map((field) => ({
              term: `/${field.name}`,
              description: hasChoices(field)
                ? `${field.label} — ${t('basics.search.commandChoices', { values: samplesOf(field).join(', ') })}`
                : field.label,
            }))}
          />
        </div>
      )}
      {examples.length > 0 && (
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
                {examples.map((example) => (
                  <tr key={example.query}>
                    <td className="py-2 pr-4 align-top">
                      <code className="bg-raised rounded px-1.5 py-0.5 text-xs whitespace-nowrap">{example.query}</code>
                    </td>
                    <td className="text-fg-muted py-2 align-top">{example.meaning}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
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

/**
 * What a request is, from the side of whoever makes one (#42, F3.2).
 *
 * **It exists because the mechanism is invisible from the outside.** Somebody presses "ask for the
 * master role" and then nothing happens on their screen: no queue they can watch, no place the
 * request went. What this says is the part they cannot see — an admin reads it, answers with a
 * reason of their own, and the answer arrives as a notification — plus the two rules that otherwise
 * surface only as a refusal: the reason is required, and one request of each kind at a time.
 */
export function RequestsHelp() {
  const { t } = useTranslation('help')

  return (
    <>
      <p className="text-fg-muted text-sm">{t('basics.requests.intro')}</p>
      <HelpTerms
        termWidth="w-36"
        terms={REQUEST_KINDS.map((kind) => ({
          term: t(`basics.requests.kindTitle.${kind}`),
          description: t(`basics.requests.kind.${kind}`),
        }))}
      />
      <HelpList items={['reason', 'onePending', 'answer', 'notified', 'noQueue'].map((key) => t(`basics.requests.${key}`))} />
      <HelpSteps title={t('stepsTitle')} items={[1, 2, 3, 4].map((n) => t(`basics.requests.steps.step${n}`))} />
    </>
  )
}

/** What gets notified and where the history is. */
export function NotificationsHelp() {
  const { t } = useTranslation('help')

  return <HelpList items={['what', 'click', 'history'].map((key) => t(`basics.notifications.${key}`))} />
}
