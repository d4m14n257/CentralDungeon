import type { ReactNode } from 'react'

import { AccountHelp, ContextsHelp, NotificationsHelp, SearchHelp, TableStatusHelp } from './basics'
import { AdminFilesHelp, AssignMastersHelp, CatalogsHelp, OwnerHelp, ReviewingHelp } from './admins'
import {
  CandidatesHelp,
  CoMastersHelp,
  CreatingHelp,
  DashboardHelp,
  DeletingHelp,
  EditTableHelp,
  MasterFilesHelp,
  MasterTasksHelp,
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
  PlayerTasksHelp,
  ScheduleConflictsHelp,
} from './players'

/** One entry of the help catalogue: what it is called and what it says. */
export interface HelpSectionDefinition {
  /** Key of the heading in the `help` namespace. It becomes the dialog's title. */
  titleKey: string
  /** The body, as a component so each section keeps its own translation hooks. */
  Body: () => ReactNode
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

  'players.applying': { titleKey: 'players.applying.title', Body: ApplyingHelp },
  'players.application-status': { titleKey: 'players.applicationStatus.title', Body: ApplicationStatusHelp },
  'players.schedule-conflicts': { titleKey: 'players.scheduleConflict.title', Body: ScheduleConflictsHelp },
  'players.my-tables': { titleKey: 'players.myTables.title', Body: MyTablesHelp },
  'players.my-sessions': { titleKey: 'players.mySessions.title', Body: MySessionsHelp },
  'players.tasks': { titleKey: 'players.tasks.title', Body: PlayerTasksHelp },
  'players.files': { titleKey: 'players.files.title', Body: PlayerFilesHelp },

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

  'admins.reviewing': { titleKey: 'admins.reviewing.title', Body: ReviewingHelp },
  'admins.assign-masters': { titleKey: 'admins.assignMasters.title', Body: AssignMastersHelp },
  'admins.catalogs': { titleKey: 'admins.catalogs.title', Body: CatalogsHelp },
  'admins.files': { titleKey: 'admins.files.title', Body: AdminFilesHelp },
  'admins.owner': { titleKey: 'admins.owner.title', Body: OwnerHelp },
} as const satisfies Record<string, HelpSectionDefinition>

/**
 * The address of a piece of help.
 *
 * Derived from the registry rather than written out beside it (regla dura 6): adding a section is
 * one entry, and naming one that does not exist stops compiling.
 */
export type HelpSectionId = keyof typeof HELP_SECTIONS
