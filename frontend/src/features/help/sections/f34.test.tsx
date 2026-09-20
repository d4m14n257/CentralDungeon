import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'

import { PausingHelp } from './admins'
import { BanningHelp, PauseRequestHelp } from './masters'
import { HELP_SECTIONS } from './registry'

/**
 * The help F3.4 owes (plan-desarrollo.md §6 point 8, #231).
 *
 * These assert on the **substance** of each section rather than on whole sentences: the wording is
 * free to improve, but a section that stopped saying that asking is not pausing, or that a veto is
 * reversible, has lost the reason it was written.
 */
describe('PauseRequestHelp', () => {
  /**
   * The one thing this section exists for. `PauseRequested` reads like a pause; a master who takes
   * it for one stops turning up while the calendar is still promising dates to their players.
   */
  it('says that asking is not pausing, and that the table keeps running meanwhile', () => {
    render(<PauseRequestHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/Pedir no es pausar/i)
    expect(body).toMatch(/sigue «En curso»/i)
  })

  /** #32: an admin decides it, and the reason is what they decide on. */
  it('says an admin decides it and that the reason is required', () => {
    render(<PauseRequestHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/lo decide un admin/i)
    expect(body).toMatch(/motivo es obligatorio/i)
  })

  /** What happens to the dates on both sides of the pause — the half nobody would guess (#33). */
  it('says the sessions are hidden and rescheduled on the way back, losing none', () => {
    render(<PauseRequestHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/se ocultan/i)
    expect(body).toMatch(/No se pierde ninguna/i)
  })

  /** #170: it teaches how to do it, not only what exists. */
  it('walks through asking for one, step by step', () => {
    const { container } = render(<PauseRequestHelp />)

    expect(container.querySelectorAll('ol li')).toHaveLength(4)
  })
})

describe('BanningHelp', () => {
  /**
   * "Vetar" reads as a platform ban and it is not one (#29). Somebody who believes it is will either
   * never use it or use it as a last resort on a person they would rather have warned.
   */
  it('says the veto is per table and costs the person nothing else', () => {
    render(<BanningHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/por mesa/i)
    expect(body).toMatch(/No pierde la cuenta/i)
  })

  /** #39, #71: the Primary applies it and a co-master asks — and who answers that request. */
  it('says a co-master asks, and that the table’s master answers, not an admin', () => {
    render(<BanningHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/Un co-master no veta/i)
    expect(body).toMatch(/no un admin/i)
  })

  /** The two halves of #39: it is reversible, and the row that makes it reversible stays on screen. */
  it('says it can be lifted, and that the vetoed row stays visible to do it from', () => {
    render(<BanningHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/Se puede levantar/i)
    expect(body).toMatch(/se sigue viendo en Jugadores/i)
  })

  /** The half that surprises the master rather than the player: nobody is notified (#29). */
  it('warns that the vetoed person is never told', () => {
    render(<BanningHelp />)

    expect(document.body.textContent).toMatch(/no se le avisa/i)
  })
})

describe('PausingHelp', () => {
  /** #163: two buttons that arrived with no screen around them and nothing explaining either. */
  it('says what pausing does and that it is not cancelling', () => {
    render(<PausingHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/sin cerrarla/i)
    expect(body).toMatch(/Pausar no es cancelar/i)
  })

  /** #32: the master asks and approving the request is what actually pauses the table. */
  it('says a master’s request is what the tray approves', () => {
    render(<PausingHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/El master no pausa su mesa/i)
    expect(body).toMatch(/bandeja/i)
  })

  /**
   * #193: the one act on that screen that can genuinely be refused, and the least likely to look it.
   * An admin who does not expect it reads the refusal as a broken button.
   */
  it('warns that resuming can clash with another table of the master’s', () => {
    render(<PausingHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/Reanudar puede fallar/i)
    expect(body).toMatch(/con qué mesa choca/i)
  })
})

describe('the F3.4 sections in the registry', () => {
  /**
   * The id is the contract (#231): a screen names the section it wants and knows nothing else. These
   * three are the ones F3.4's screens link, and a rename here would silently orphan a `HelpLink`.
   */
  it('registers the three sections the slice’s screens link to', () => {
    expect(HELP_SECTIONS['masters.pause']).toBeDefined()
    expect(HELP_SECTIONS['masters.banning']).toBeDefined()
    expect(HELP_SECTIONS['admins.pausing']).toBeDefined()
  })

  /**
   * And `masters.banning` is not `admins.blocking`. The two share a verb in Spanish and nothing
   * else: one removes somebody from a table, the other closes an account. A master who landed on
   * the second would read that the person can never log in again.
   */
  it('keeps the veto and the account closure as two different sections', () => {
    expect(HELP_SECTIONS['masters.banning'].titleKey).not.toBe(HELP_SECTIONS['admins.blocking'].titleKey)
  })
})
