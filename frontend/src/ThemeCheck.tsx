import { useState } from 'react'

/**
 * Scaffold-only page. It renders token identifiers and values, never product copy,
 * so it does not need i18next and does not break #117 — E1 deletes it and puts the
 * real router here. Its job is to prove the @theme transcription actually resolves.
 *
 * Class names are written out in full on purpose: Tailwind scans source statically,
 * so `bg-brand-${step}` would never be generated.
 */

const BRAND = [
  ['300', 'bg-brand-300'],
  ['400', 'bg-brand-400'],
  ['500', 'bg-brand-500'],
  ['600', 'bg-brand-600'],
  ['700', 'bg-brand-700'],
  ['800', 'bg-brand-800'],
] as const

const STATES = [
  ['draft', 'bg-state-draft-bg text-state-draft-fg', 'bg-state-draft-dot'],
  ['pending', 'bg-state-pending-bg text-state-pending-fg', 'bg-state-pending-dot'],
  ['warning', 'bg-state-warning-bg text-state-warning-fg', 'bg-state-warning-dot'],
  ['open', 'bg-state-open-bg text-state-open-fg', 'bg-state-open-dot'],
  ['active', 'bg-state-active-bg text-state-active-fg', 'bg-state-active-dot'],
  ['paused', 'bg-state-paused-bg text-state-paused-fg', 'bg-state-paused-dot'],
  ['done', 'bg-state-done-bg text-state-done-fg', 'bg-state-done-dot'],
  ['canceled', 'bg-state-canceled-bg text-state-canceled-fg', 'bg-state-canceled-dot'],
  ['blocked', 'bg-state-blocked-bg text-state-blocked-fg', 'bg-state-blocked-dot'],
] as const

export function ThemeCheck() {
  const [light, setLight] = useState(false)

  function toggle() {
    const next = !light
    setLight(next)
    document.documentElement.dataset['theme'] = next ? 'light' : 'dark'
  }

  return (
    <main className="min-h-screen bg-canvas p-8 font-sans text-fg">
      <header className="mb-8 flex items-center gap-4">
        <h1 className="font-serif text-3xl font-semibold">
          Central<span className="text-brand-400">Dungeon</span>
        </h1>
        <code className="text-xs text-fg-subtle">@theme</code>
        <button
          type="button"
          onClick={toggle}
          className="ml-auto rounded-lg border border-border-strong px-4 py-2 text-sm font-semibold"
        >
          data-theme={light ? 'light' : 'dark'}
        </button>
      </header>

      <section className="mb-8">
        <h2 className="mb-3 font-mono text-xs uppercase tracking-wide text-fg-subtle">
          --color-brand-*
        </h2>
        <div className="grid grid-cols-6 gap-3">
          {BRAND.map(([step, bg]) => (
            <div key={step}>
              <div className={`h-14 rounded-lg ${bg}`} />
              <code className="mt-1 block text-xs text-fg-subtle">{step}</code>
            </div>
          ))}
        </div>
      </section>

      <section className="mb-8">
        <h2 className="mb-3 font-mono text-xs uppercase tracking-wide text-fg-subtle">
          --color-state-* &middot; 9 tokens, 14 estados
        </h2>
        <div className="flex flex-wrap gap-2">
          {STATES.map(([name, pill, dot]) => (
            <span
              key={name}
              className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-medium ${pill}`}
            >
              <span className={`size-2 rounded-full ${dot}`} />
              {name}
            </span>
          ))}
        </div>
      </section>

      <section className="flex gap-3">
        <div className="flex-1 rounded-xl border border-border-strong bg-surface p-4">
          <code className="text-xs text-fg-subtle">--color-surface</code>
        </div>
        <div className="flex-1 rounded-xl border border-border-strong bg-raised p-4">
          <code className="text-xs text-fg-subtle">--color-raised</code>
        </div>
      </section>
    </main>
  )
}
