import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Button } from '@/components/ui/button'
import { paths } from '@/config/paths'

/**
 * The catch-all route: the URL matches nothing. Offers the way back rather than a dead end.
 *
 * It renders inside a layout, never alone: the shell at the root, and the context's own layout under a
 * context prefix, so the header (and the context's navigation) stay on screen. It is not one of
 * `RootLayout`'s public paths, so somebody without a session who types a broken URL lands on /login
 * and never sees it.
 */
export function NotFoundPage() {
  const { t } = useTranslation('common')

  return (
    <div className="flex flex-col items-center gap-4 py-16 text-center">
      {/* The digits are not a translatable string: they read the same in every language. */}
      <h1 className="page-title">404</h1>
      <Button asChild>
        <Link to={paths.root}>{t('notFound.backHome')}</Link>
      </Button>
    </div>
  )
}

export { NotFoundPage as Component }
