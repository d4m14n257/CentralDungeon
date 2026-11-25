import { Link } from 'react-router'

/** What {@link SubjectLine} takes. */
export interface SubjectLineProps {
  /** Whom or what the item is about, already translated ("About: Diego"). Absent when it is about no one. */
  subject?: string | null
  /** The link to the thing the item points at, with its translated text. Absent when it points at nothing with a screen. */
  link?: { to: string; label: string } | null
  /** Says the subject in the weight of a value instead of a caption: for a detail panel, where it is what the reader decides on. */
  emphasized?: boolean
}

/**
 * The «About: X» line of a request, with the link to the table it concerns underneath.
 *
 * **A named pattern because three screens wrote it by hand** - the admin tray, the admin requests list
 * and the request detail panel - and a veto is exactly the case where the subject and the table are
 * neither of them whoever asked, so the three had to say the same thing the same way. It takes text and
 * a destination and knows no domain, so it lives with the shared components and any feature can use it
 * through a screen. It draws nothing when it has neither a subject nor a link.
 *
 * @param props.subject    the translated «About: X» sentence, or nothing
 * @param props.link       where the item points, with its text, or nothing
 * @param props.emphasized whether the subject reads as a value (detail) and not as a caption (list row)
 */
export function SubjectLine({ subject, link, emphasized = false }: SubjectLineProps) {
  if (!subject && !link) return null
  return (
    <div className="flex flex-col items-start gap-0.5">
      {subject && <span className={emphasized ? 'font-medium' : 'text-fg-muted text-xs'}>{subject}</span>}
      {link && (
        <Link to={link.to} className="text-xs underline-offset-2 hover:underline">
          {link.label}
        </Link>
      )}
    </div>
  )
}
