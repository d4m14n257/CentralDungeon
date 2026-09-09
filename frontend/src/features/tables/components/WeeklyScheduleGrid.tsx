import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'
import {
  WEEKDAYS,
  formatMinutes,
  localWeekMinuteToUtc,
  splitBlockByDay,
  utcWeekMinuteToLocal,
  weekdayName,
  type WeekBlock,
} from '@/lib/date'

import type { WeeklyCommitment } from '../types'

/** How tall one hour is. The whole day fits without scrolling inside the grid's own container. */
const PX_PER_HOUR = 26

/** Hours in a day, which is how many rows the grid has and how many cells a day column offers. */
const HOURS = Array.from({ length: 24 }, (_, hour) => hour)

/** One drawn rectangle: a piece of a commitment that falls on one day. */
interface Piece {
  key: string
  dayIndex: number
  top: number
  height: number
  label: string
  role: WeeklyCommitment['role'] | 'new'
  title: string
}

/** What the grid needs to draw a week, and optionally to let somebody claim an hour of it. */
export interface WeeklyScheduleGridProps {
  /** Everything the reader is already committed to, as the API sent it: UTC minutes from Monday 00:00. */
  commitments: WeeklyCommitment[]
  /** The IANA zone to draw it in — from the profile, or the browser's (#111). */
  timeZone: string
  /**
   * Blocks belonging to the table being built right now, drawn in the accent so they read as
   * *yours, being added* rather than as something already committed. In UTC, like the rest.
   */
  pending?: WeekBlock[]
  /**
   * Called with the UTC minute of an hour the reader clicked, when the grid is interactive.
   *
   * Omit it and the grid is read-only — which is what `/my/schedule` wants and what the wizard does
   * not: finding the gap and taking it should be the same gesture.
   */
  onPickHour?: (utcStartMinute: number) => void
}

/**
 * The reader's week as a grid: seven day columns, twenty-four hour rows, and a rectangle for every
 * stretch they have committed (#227).
 *
 * **It draws what the rule measures.** The blocks are the same intervals #178 compares to refuse a
 * clash, so an hour that looks free here is an hour the server will accept — which is the whole
 * reason the screen exists. A master building a table can see the evenings they have already given
 * away instead of finding out by being refused.
 *
 * **Everything is converted once, here, from UTC** (#22). A Tuesday-night table in America is
 * Wednesday in UTC, and a block that crosses midnight is drawn as two rectangles, on the two days it
 * actually touches — the same wrap the backend does when it compares.
 *
 * When `onPickHour` is given every free hour is a real button, one per cell, rather than a click
 * handler doing arithmetic on where the pointer landed: an hour of the week is a thing you should be
 * able to reach with a keyboard and hear named by a screen reader.
 *
 * @param props.commitments what the reader already has on
 * @param props.timeZone    the zone to draw it in
 * @param props.pending     blocks of the table being built, drawn apart from the rest
 * @param props.onPickHour  what to do with an hour the reader claims. Omit for a read-only grid
 */
export function WeeklyScheduleGrid({ commitments, timeZone, pending = [], onPickHour }: WeeklyScheduleGridProps) {
  const { t, i18n } = useTranslation('tables')

  const pieces: Piece[] = []
  for (const commitment of commitments) {
    for (const block of commitment.blocks) {
      const localStart = utcWeekMinuteToLocal(block.startMinute, timeZone)
      for (const [index, part] of splitBlockByDay({ ...block, startMinute: localStart }).entries()) {
        pieces.push({
          key: `${commitment.tableId}-${block.startMinute}-${index}`,
          dayIndex: part.dayIndex,
          top: (part.startMinuteOfDay / 60) * PX_PER_HOUR,
          height: (part.durationMinutes / 60) * PX_PER_HOUR,
          label: commitment.tableName,
          role: commitment.role,
          title: `${commitment.tableName} — ${t(`schedule.role.${commitment.role}`)}`,
        })
      }
    }
  }
  for (const [blockIndex, block] of pending.entries()) {
    const localStart = utcWeekMinuteToLocal(block.startMinute, timeZone)
    for (const [index, part] of splitBlockByDay({ ...block, startMinute: localStart }).entries()) {
      pieces.push({
        key: `pending-${blockIndex}-${index}`,
        dayIndex: part.dayIndex,
        top: (part.startMinuteOfDay / 60) * PX_PER_HOUR,
        height: (part.durationMinutes / 60) * PX_PER_HOUR,
        label: t('schedule.thisTable'),
        role: 'new',
        title: t('schedule.thisTable'),
      })
    }
  }

  /** Which hours already have something on them, so a free cell can be told from a taken one. */
  const taken = new Set<string>()
  for (const piece of pieces) {
    const firstHour = Math.floor(piece.top / PX_PER_HOUR)
    const lastHour = Math.ceil((piece.top + piece.height) / PX_PER_HOUR) - 1
    for (let hour = firstHour; hour <= lastHour; hour++) {
      taken.add(`${piece.dayIndex}-${hour}`)
    }
  }

  // The grid keeps its own scroll and opens on the hours that have something on them. A week that
  // starts at 00:00 buries the evening this community actually plays at under seven empty rows, and
  // the first thing anybody would do is scroll past them.
  const viewport = useRef<HTMLDivElement>(null)
  const firstBusyHour = pieces.length > 0 ? Math.min(...pieces.map((piece) => Math.floor(piece.top / PX_PER_HOUR))) : 18
  useEffect(() => {
    viewport.current?.scrollTo({ top: Math.max(0, firstBusyHour - 1) * PX_PER_HOUR, behavior: 'instant' })
  }, [firstBusyHour])

  return (
    <div ref={viewport} className="border-border max-h-[70vh] overflow-auto rounded-lg border">
      <div className="grid min-w-[42rem] grid-cols-[3.5rem_repeat(7,1fr)]">
        <span aria-hidden className="border-border bg-raised sticky top-0 z-10 border-r border-b" />
        {WEEKDAYS.map((day) => (
          <span
            key={day}
            className="border-border bg-raised text-fg-muted sticky top-0 z-10 border-b px-1 py-1.5 text-center text-xs font-medium"
          >
            {weekdayName(day, i18n.language, 'short')}
          </span>
        ))}

        <div className="border-border border-r">
          {HOURS.map((hour) => (
            <div
              key={hour}
              className="text-fg-subtle border-border flex items-start justify-end border-b pr-1.5 text-[10px] tabular-nums"
              style={{ height: PX_PER_HOUR }}
            >
              {formatMinutes(hour * 60)}
            </div>
          ))}
        </div>

        {WEEKDAYS.map((day, dayIndex) => (
          <div key={day} className="border-border relative border-r last:border-r-0">
            {HOURS.map((hour) => {
              const isTaken = taken.has(`${dayIndex}-${hour}`)
              const label = `${weekdayName(day, i18n.language)} ${formatMinutes(hour * 60)}`
              return onPickHour && !isTaken ? (
                <button
                  key={hour}
                  type="button"
                  aria-label={t('schedule.pickHour', { slot: label })}
                  onClick={() => onPickHour(localWeekMinuteToUtc(dayIndex * 24 * 60 + hour * 60, timeZone))}
                  className="border-border hover:bg-accent block w-full border-b"
                  style={{ height: PX_PER_HOUR }}
                />
              ) : (
                <div key={hour} className="border-border border-b" style={{ height: PX_PER_HOUR }} />
              )
            })}

            {pieces
              .filter((piece) => piece.dayIndex === dayIndex)
              .map((piece) => (
                <span
                  key={piece.key}
                  title={piece.title}
                  className={cn(
                    'pointer-events-none absolute inset-x-0.5 overflow-hidden rounded-sm px-1 py-0.5 text-[10px] leading-tight',
                    piece.role === 'Master' && 'bg-brand-500/85 text-white',
                    piece.role === 'Player' && 'bg-state-active-bg text-state-active-fg',
                    piece.role === 'new' && 'bg-state-open-bg text-state-open-fg ring-state-open-dot ring-1',
                  )}
                  style={{ top: piece.top, height: piece.height }}
                >
                  {piece.label}
                </span>
              ))}
          </div>
        ))}
      </div>
    </div>
  )
}
