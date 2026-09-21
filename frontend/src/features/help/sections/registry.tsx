import type { ReactNode } from 'react'

import type { SearchField } from '@/lib/searchQuery'

import { AccountHelp, ContextsHelp, NotificationsHelp, RequestsHelp, SearchHelp, TableStatusHelp } from './basics'
import {
  AdminFilesHelp,
  AssignMastersHelp,
  BlockingHelp,
  CatalogsHelp,
  ClaimingHelp,
  OwnerHelp,
  PausingHelp,
  RequestsAdminHelp,
  ReviewingHelp,
  RolesHelp,
  SettingsHelp,
} from './admins'
import {
  BanningHelp,
  CandidatesHelp,
  CoMastersHelp,
  CreatingHelp,
  DashboardHelp,
  DeletingHelp,
  EditTableHelp,
  MasterFilesHelp,
  MasterTasksHelp,
  PauseRequestHelp,
  ProposeCatalogHelp,
  ReviewHelp,
  RunningHelp,
  ScheduleHelp,
  SessionsHelp,
} from './masters'
import {
  ApplicationStatusHelp,
  ApplyingHelp,
  MySessionsHelp,
  MyTablesHelp,
  PlayerFilesHelp,
  PlayerHistoryHelp,
  PlayerTasksHelp,
  ProfileHelp,
  ScheduleConflictsHelp,
} from './players'

/**
 * What the screen raising the help can tell it about itself.
 *
 * **Everything here is optional and most sections take none of it** (#240): the help is fixed text,
 * and a section that needs no context declares no props — a `() => ReactNode` still satisfies this.
 * What forced the first one is the search: its rules are the same everywhere, but its *examples* have
 * to be written with the commands of the box that was being used, or they teach commands that screen
 * does not have.
 */
export interface HelpSectionBodyProps {
  /** The commands the search box that opened this help accepts, in the order it offers them. */
  searchFields?: readonly SearchField[]
}

/** One entry of the help catalogue: what it is called and what it says. */
export interface HelpSectionDefinition {
  /** Key of the heading in the `help` namespace. It becomes the dialog's title. */
  titleKey: string
  /** The body, as a component so each section keeps its own translation hooks. */
  Body: (props: HelpSectionBodyProps) => ReactNode
}

/**
 * Every piece of help the application can show, addressed by a stable id.
 *
 * **The id is the contract** (#231, inherited from #168): a screen names the section it wants and
 * knows nothing else about it. The ids keep the `audience.ref` shape the old `/help/masters#schedule`
 * links used, so the mapping from what the URLs said to what the dialogs show stays legible.
 *
 * There is no audience gate on any of this. It is fixed text with nothing to protect (#103), and a
 * section is only reachable from the screen that links it - a screen the reader already got past
 * its own guard to be on. The `HelpAudienceGate` of #170(b) existed because `/help/admins` was a URL
 * anybody could type; it no longer is one.
 */
export const HELP_SECTIONS = {
  'basics.search': { titleKey: 'basics.search.title', Body: SearchHelp },
  'basics.contexts': { titleKey: 'basics.contexts.title', Body: ContextsHelp },
  'basics.table-status': { titleKey: 'basics.tableStatus.title', Body: TableStatusHelp },
  'basics.account': { titleKey: 'basics.account.title', Body: AccountHelp },
  'basics.notifications': { titleKey: 'basics.notifications.title', Body: NotificationsHelp },
  // Under `basics` and not under `players`, because the mechanism is one and the three screens that
  // raise a request are not all a player's (#42): the same section is read from the profile, from
  // the explorer and from the support screen.
  'basics.requests': { titleKey: 'basics.requests.title', Body: RequestsHelp },

  'players.applying': { titleKey: 'players.applying.title', Body: ApplyingHelp },
  'players.application-status': { titleKey: 'players.applicationStatus.title', Body: ApplicationStatusHelp },
  'players.schedule-conflicts': { titleKey: 'players.scheduleConflict.title', Body: ScheduleConflictsHelp },
  'players.my-tables': { titleKey: 'players.myTables.title', Body: MyTablesHelp },
  'players.my-sessions': { titleKey: 'players.mySessions.title', Body: MySessionsHelp },
  'players.tasks': { titleKey: 'players.tasks.title', Body: PlayerTasksHelp },
  'players.files': { titleKey: 'players.files.title', Body: PlayerFilesHelp },
  'players.profile': { titleKey: 'players.profile.title', Body: ProfileHelp },
  'players.history': { titleKey: 'players.history.title', Body: PlayerHistoryHelp },

  'masters.creating': { titleKey: 'masters.creating.title', Body: CreatingHelp },
  'masters.schedule': { titleKey: 'masters.schedule.title', Body: ScheduleHelp },
  'masters.sessions': { titleKey: 'masters.sessions.title', Body: SessionsHelp },
  'masters.tasks': { titleKey: 'masters.tasks.title', Body: MasterTasksHelp },
  'masters.files': { titleKey: 'masters.files.title', Body: MasterFilesHelp },
  'masters.propose-catalog': { titleKey: 'masters.proposeCatalog.title', Body: ProposeCatalogHelp },
  'masters.review': { titleKey: 'masters.review.title', Body: ReviewHelp },
  'masters.candidates': { titleKey: 'masters.candidates.title', Body: CandidatesHelp },
  'masters.running': { titleKey: 'masters.running.title', Body: RunningHelp },
  'masters.deleting': { titleKey: 'masters.deleting.title', Body: DeletingHelp },
  'masters.co-masters': { titleKey: 'masters.coMasters.title', Body: CoMastersHelp },
  'masters.dashboard': { titleKey: 'masters.dashboard.title', Body: DashboardHelp },
  'masters.edit-table': { titleKey: 'masters.editTable.title', Body: EditTableHelp },
  // Asking for a pause (#32, F3.4). Its own section and not a line inside `running`: the thing that
  // has to be learned is that asking is not pausing, and a bullet inside another section is where
  // that gets skimmed past.
  'masters.pause': { titleKey: 'masters.pause.title', Body: PauseRequestHelp },
  // The veto (#29, #39). Deliberately not near `admins.blocking`, which is about closing an account:
  // the two share a verb in Spanish and nothing else, and a master looking for one must not land on
  // the other.
  'masters.banning': { titleKey: 'masters.banning.title', Body: BanningHelp },

  // What reviewing a table is and where it happens. Its text moved with the buttons when approving
  // and requesting changes left `/admin/tables` for the tray (#176, F3.3).
  'admins.reviewing': { titleKey: 'admins.reviewing.title', Body: ReviewingHelp },
  // The reservation of the shared tray (#100, F3.3): a rule that is invisible until it refuses
  // something, which is the worst way to learn one.
  'admins.claiming': { titleKey: 'admins.claiming.title', Body: ClaimingHelp },
  'admins.assign-masters': { titleKey: 'admins.assignMasters.title', Body: AssignMastersHelp },
  'admins.catalogs': { titleKey: 'admins.catalogs.title', Body: CatalogsHelp },
  'admins.files': { titleKey: 'admins.files.title', Body: AdminFilesHelp },
  'admins.owner': { titleKey: 'admins.owner.title', Body: OwnerHelp },
  'admins.roles': { titleKey: 'admins.roles.title', Body: RolesHelp },
  'admins.blocking': { titleKey: 'admins.blocking.title', Body: BlockingHelp },
  // The two buttons #163 left without a screen for two phases, and the clash that can refuse one.
  'admins.pausing': { titleKey: 'admins.pausing.title', Body: PausingHelp },
  // The other end of `basics.requests`: what the tray is, and that approving is not one act but
  // three different ones depending on what was asked for (F3.2).
  'admins.requests': { titleKey: 'admins.requests.title', Body: RequestsAdminHelp },
  // The editable configuration of #141 (F3.5). Its own section: what has to be learned is not who
  // may edit a setting but that two of them are not cosmetic adjustments.
  'admins.settings': { titleKey: 'admins.settings.title', Body: SettingsHelp },
} as const satisfies Record<string, HelpSectionDefinition>

/**
 * The address of a piece of help.
 *
 * Derived from the registry rather than written out beside it (regla dura 6): adding a section is
 * one entry, and naming one that does not exist stops compiling.
 */
export type HelpSectionId = keyof typeof HELP_SECTIONS
