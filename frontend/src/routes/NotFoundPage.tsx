import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

import { Button } from '@/components/ui/button'
import { paths } from '@/config/paths'

/**
 * The catch-all route: the URL matches nothing. Offers the way back rather than a dead end.
 *
 * It renders inside `RootLayout` and is not one of its public paths, so somebody without a session
 * who types a broken URL lands on /login and never sees this screen.
 */
export function NotFoundPage() {
  const { t } = useTranslation('common')

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 text-center">
      {/* The digits are not a translatable string: they read the same in every language. */}
      <h1 className="font-serif text-3xl font-semibold">404</h1>
      <Button asChild>
        <Link to={paths.home}>{t('notFound.backHome')}</Link>
      </Button>
    </div>
  )
}

export { NotFoundPage as Component }
