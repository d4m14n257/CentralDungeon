/**
 * The shapes a help section is built out of. They carry no layout of their own beyond their own
 * rhythm, so the same body reads the same wherever it is rendered.
 */

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
 * the screen open behind the dialog.
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
