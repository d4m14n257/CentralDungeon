import type { ReactNode } from 'react'
import { useLocation } from 'react-router'

import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { useScrollOnHash } from '@/hooks/useScrollOnHash'
import { cn } from '@/lib/utils'

interface HelpSectionProps {
  /** The `#ref` the rest of the application links to (decisiones.md #168). */
  id: string
  title: string
  children: ReactNode
}

/**
 * One block of the help. The `id` is its address: `/help#search`, `/help/admins#assign-masters`.
 * Changing it breaks the links that already exist, so it is treated as part of the screen's contract
 * and not as a layout detail.
 *
 * **The section the URL names is highlighted** (#170): somebody arriving from a link lands in the
 * middle of a long page, and without a mark they cannot tell which block they came to read. The
 * highlight lasts as long as the `#ref` names it — it goes out on navigating elsewhere, not on a
 * timer that would force people to read fast.
 *
 * The title is a real `h2` and not `CardTitle`'s `div`: this is a page meant to be read.
 */
export function HelpSection({ id, title, children }: HelpSectionProps) {
  const ref = useScrollOnHash<HTMLElement>(id)
  const { hash } = useLocation()
  const isTargeted = hash.slice(1) === id

  return (
    <section id={id} ref={ref} className="scroll-mt-20" aria-current={isTargeted ? 'location' : undefined}>
      <Card className={cn(isTargeted && 'border-primary ring-primary/30 ring-2')}>
        <CardHeader>
          <h2 className="font-serif text-lg leading-none font-semibold">{title}</h2>
        </CardHeader>
        <CardContent className="space-y-4">{children}</CardContent>
      </Card>
    </section>
  )
}

/** A block's bullet list: what there is to know. */
export function HelpList({ items }: { items: string[] }) {
  return (
    <ul className="space-y-2 text-sm">
      {items.map((item) => (
        <li key={item} className="flex gap-2">
          <span className="text-fg-subtle" aria-hidden>
            ·
          </span>
          <span>{item}</span>
        </li>
      ))}
    </ul>
  )
}

/**
 * The steps of a task: how it is done, in order. The help does not merely describe what exists, it
 * teaches how to use it (#170), and for that a numbered step is the shape somebody can follow with
 * the site open beside it.
 */
export function HelpSteps({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium">{title}</h3>
      <ol className="space-y-2 text-sm">
        {items.map((item, index) => (
          <li key={item} className="flex gap-3">
            <span className="bg-primary/15 text-fg flex size-5 shrink-0 items-center justify-center rounded-full text-xs font-medium">
              {index + 1}
            </span>
            <span className="pt-0.5">{item}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}

/** Term and explanation pairs: statuses, roles, anything with a name of its own. */
export function HelpTerms({ terms, termWidth = 'w-40' }: { terms: { term: string; description: string }[]; termWidth?: string }) {
  return (
    <dl className="space-y-2 text-sm">
      {terms.map(({ term, description }) => (
        <div key={term} className="flex flex-col gap-0.5 sm:flex-row sm:gap-3">
          <dt className={`${termWidth} shrink-0 font-medium`}>{term}</dt>
          <dd className="text-fg-muted">{description}</dd>
        </div>
      ))}
    </dl>
  )
}
