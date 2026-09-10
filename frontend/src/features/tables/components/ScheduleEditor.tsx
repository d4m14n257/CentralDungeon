import { XIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { IconAction } from '@/components/IconAction'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatSlot, minutesOfDay, utcSlotToLocal } from '@/lib/date'

import type { TableScheduleEntry } from '../types'

/**
 * The lengths a session can be offered as. Not a free text field: a session is chosen from what
 * people actually play, and letting somebody type 03:47 asks a question the table cannot answer.
 */
const DURATIONS = ['01:00', '01:30', '02:00', '02:30', '03:00', '03:30', '04:00', '05:00', '06:00', '08:00'] as const

/** The default a slot is born with, which is what this community's tables run. */
export const DEFAULT_SLOT_DURATION = '03:00'

/** What the schedule editor needs to know. */
export interface ScheduleEditorProps {
  /** The agenda as it will travel: **UTC** (#22). The editor converts for display only. */
  value: TableScheduleEntry[]
  /** Called with the new agenda, again in UTC. */
  onChange: (schedule: TableScheduleEntry[]) => void
  /** The IANA zone to show the times in — from the profile, or the browser's (#111). */
  timeZone: string
}

/**
 * The slots a table has claimed, each with how long it runs and a way to drop it.
 *
 * **It no longer adds anything**: slots come from clicking the week (#228). Having a day-and-hour
 * form here *as well as* the grid meant two ways to do one thing, and the master had to work out
 * which of the two the table was actually going to believe. Now the grid says *when* and this says
 * *how long*, which are two different questions.
 *
 * The length is per slot: a table can legitimately run three hours midweek and six on a Saturday,
 * and the single table-wide duration this replaces forced the master to lie about one of them.
 *
 * @param props.value    the agenda, in UTC
 * @param props.onChange called with the new agenda, in UTC
 * @param props.timeZone the zone to show local times in
 */
export function ScheduleEditor({ value, onChange, timeZone }: ScheduleEditorProps) {
  const { t, i18n } = useTranslation('master')

  /** "3 h" and "3 h 30 min" — a length, which is not the same shape as a time of day. */
  function durationLabel(duration: string): string {
    const minutes = minutesOfDay(duration)
    const hours = Math.floor(minutes / 60)
    const rest = minutes % 60
    return rest === 0 ? t('schedule.hours', { count: hours }) : t('schedule.hoursAndMinutes', { hours, minutes: rest })
  }

  function setDuration(index: number, duration: string) {
    onChange(value.map((entry, position) => (position === index ? { ...entry, duration } : entry)))
  }

  function remove(index: number) {
    onChange(value.filter((_, position) => position !== index))
  }

  if (value.length === 0) {
    return <p className="text-fg-muted text-sm">{t('schedule.emptyHint')}</p>
  }

  return (
    <ul className="divide-border divide-y rounded-lg border">
      {value.map((entry, index) => {
        const local = utcSlotToLocal({ weekday: entry.weekday, hourtime: entry.hourtime }, timeZone)
        return (
          <li key={`${entry.weekday}-${entry.hourtime}`} className="flex flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2">
            <span className="min-w-0 flex-1">
              <span className="block text-sm">{formatSlot(local, i18n.language, entry.duration)}</span>
              {/* Both times on screen at once (#22): the day moves between the reader's zone and UTC,
                  and a master who sees only one has no way to tell that is expected. */}
              <span className="text-fg-subtle block text-xs">
                {t('schedule.utcEquivalent', { slot: formatSlot({ weekday: entry.weekday, hourtime: entry.hourtime }, i18n.language) })}
              </span>
            </span>
            <Select value={entry.duration ?? DEFAULT_SLOT_DURATION} onValueChange={(next) => setDuration(index, next)}>
              <SelectTrigger className="w-36" aria-label={t('schedule.durationOf', { slot: formatSlot(local, i18n.language) })}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {DURATIONS.map((duration) => (
                  <SelectItem key={duration} value={duration}>
                    {durationLabel(duration)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <IconAction
              icon={<XIcon />}
              label={t('schedule.remove', { slot: formatSlot(local, i18n.language) })}
              onClick={() => remove(index)}
            />
          </li>
        )
      })}
    </ul>
  )
}
