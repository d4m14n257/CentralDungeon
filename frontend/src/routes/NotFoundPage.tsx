import { Link } from 'react-router'

import { Button } from '@/components/ui/button'
import { paths } from '@/config/paths'

export function NotFoundPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 text-center">
      <h1 className="font-serif text-3xl font-semibold">404</h1>
      <Button asChild>
        <Link to={paths.home}>Volver al inicio</Link>
      </Button>
    </div>
  )
}

export { NotFoundPage as Component }
