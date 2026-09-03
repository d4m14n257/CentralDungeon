import { PlusIcon, XIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { WEEKDAYS, formatSlot, localSlotToUtc, utcSlotToLocal, weekdayName, type Weekday } from '@/lib/date'

import type { TableScheduleEntry } from '../types'

/** What the schedule editor needs to know. */
export interface ScheduleEditorProps {
  /** The agenda as it will travel: **UTC** (#22). The editor converts for display and back on change. */
  value: TableScheduleEntry[]
  /** Called with the new agenda, again in UTC. */
  onChange: (schedule: TableScheduleEntry[]) => void
  /** The IANA zone to show and read the times in — from the profile, or the browser's (#111). */
  timeZone: string
  /** How long one session lasts, `HH:mm`, so each row can show when it ends. Optional. */
  duration?: string | null | undefined
}

/**
 * Builds a table's weekly agenda: a weekday and a time, as many rows as the table plays.
 *
 * **The person types local time; what travels is UTC** (#22). Both are on screen at once, because
 * the day can move — a Tuesday-night table in Buenos Aires is stored as Wednesday — and a master
 * who sees only one of the two has no way to tell that is expected rather than a bug.
 *
 * Rows are added through a small form of their own rather than appearing empty and editable: a
 * blank row is neither a slot nor not-a-slot, and the agenda would have to carry half-written
 * entries the clash check cannot measure.
 *
 * @param props.value    the agenda, in UTC
 * @param props.onChange called with the new agenda, in UTC
 * @param props.timeZone the zone to show and read local times in
 * @param props.duration how long one session lasts, to close each range
 */
export function ScheduleEditor({ value, onChange, timeZone, duration }: ScheduleEditorProps) {
  const { t, i18n } = useTranslation('master')
  const [weekday, setWeekday] = useState<Weekday>('Friday')
  const [hourtime, setHourtime] = useState('20:00')

  const rows = value.map((entry) => ({
    utc: entry,
    local: utcSlotToLocal({ weekday: entry.weekday, hourtime: entry.hourtime }, timeZone),
  }))

  function addSlot() {
    if (!hourtime) {
      return
    }
    const utc = localSlotToUtc({ weekday, hourtime }, timeZone)
    // The primary key is (table, weekday, hourtime), so the same slot twice is one slot. Catching
    // it here keeps the person from sending an agenda the server would quietly collapse.
    const alreadyThere = value.some((entry) => entry.weekday === utc.weekday && entry.hourtime.slice(0, 5) === utc.hourtime)
    if (alreadyThere) {
      return
    }
    onChange([...value, { weekday: utc.weekday, hourtime: utc.hourtime }])
  }

  function removeSlot(index: number) {
    onChange(value.filter((_, position) => position !== index))
  }

  return (
    <div className="space-y-3">
      {rows.length > 0 && (
        <ul className="divide-border divide-y rounded-md border">
          {rows.map((row, index) => (
            <li key={`${row.utc.weekday}-${row.utc.hourtime}`} className="flex items-center justify-between gap-3 px-3 py-2">
              <div>
                <p className="text-sm font-medium">{formatSlot(row.local, i18n.language, duration)}</p>
                {/* #22 made visible: the master sees what is stored, so a shifted day reads as expected. */}
                <p className="text-fg-subtle text-xs">{t('schedule.utcEquivalent', { slot: formatSlot(row.utc, i18n.language) })}</p>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                aria-label={t('schedule.remove', { slot: formatSlot(row.local, i18n.language) })}
                onClick={() => removeSlot(index)}
              >
                <XIcon className="size-4" aria-hidden="true" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-40 flex-1 space-y-1">
          <label className="text-fg-muted text-xs" htmlFor="schedule-weekday">
            {t('schedule.weekdayLabel')}
          </label>
          <Select value={weekday} onValueChange={(next) => setWeekday(next as Weekday)}>
            <SelectTrigger id="schedule-weekday" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {WEEKDAYS.map((day) => (
                <SelectItem key={day} value={day}>
                  {weekdayName(day, i18n.language)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="w-32 space-y-1">
          <label className="text-fg-muted text-xs" htmlFor="schedule-hourtime">
            {t('schedule.hourLabel')}
          </label>
          <Input id="schedule-hourtime" type="time" value={hourtime} onChange={(event) => setHourtime(event.target.value)} />
        </div>
        <Button type="button" variant="outline" onClick={addSlot}>
          <PlusIcon className="mr-1 size-4" aria-hidden="true" />
          {t('schedule.add')}
        </Button>
      </div>

      <p className="text-fg-subtle text-xs">{t('schedule.timeZoneHint', { timeZone })}</p>
    </div>
  )
}
