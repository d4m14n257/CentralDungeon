import { render, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import '@/providers/i18n'
import { BlockingHelp, RequestsAdminHelp, RolesHelp } from './admins'

/**
 * The help F3.1 owes (plan-desarrollo.md §6 point 8, #231).
 *
 * These assert on the **substance** of each section rather than on whole sentences: the wording is
 * free to improve, but a section that stopped saying who may grant what, or stopped warning that a
 * block keeps the person's data, has lost the reason it was written.
 */
describe('RolesHelp', () => {
  /**
   * The question the section exists to answer. An admin opens the role dialog and finds two of the
   * four roles absent; until something says so, an absence and a bug look identical.
   */
  it('says an admin moves Player and Master and an owner moves the other two', () => {
    render(<RolesHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/admin mueve Jugador y Master/i)
    expect(body).toMatch(/solo un owner/i)
    // And that the absence is the rule working, not the screen failing.
    expect(body).toMatch(/no es un error/i)
  })

  /** #169: promoting an admin to owner makes their Admin chip disappear, and that needs explaining. */
  it('explains that Admin and Owner are exclusive', () => {
    render(<RolesHelp />)

    expect(document.body.textContent).toMatch(/excluyentes/i)
  })

  /** The invariante global of the phase, with the why in one sentence. */
  it('explains why the platform can never be left without an owner', () => {
    render(<RolesHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/nunca se queda sin owner/i)
    expect(body).toMatch(/recuperarse desde adentro/i)
  })

  it('says the reason is required and that the history can be read back', () => {
    render(<RolesHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/motivo.*obligatorio/i)
    expect(body).toMatch(/Historial/)
  })

  /**
   * #170: the help teaches how to do it, not only what exists. The numbered steps are the shape
   * somebody can follow with the screen open behind the dialog, so a section that lost them would
   * still read fine and stop being usable.
   */
  it('walks through changing a role, step by step', () => {
    const { container } = render(<RolesHelp />)

    const steps = container.querySelector('ol')
    expect(steps).not.toBeNull()
    expect(within(steps as HTMLElement).getAllByRole('listitem')).toHaveLength(6)
    // Named with the words the screen uses, so the step and the button match.
    expect(document.body.textContent).toMatch(/Cambiar roles/)
  })
})

describe('BlockingHelp', () => {
  /**
   * #84, and the pair of facts that has to arrive together: "block" is a word people read as
   * "delete", so learning the second half afterwards is learning it too late.
   */
  it('says the person cannot get back in and that their data is kept', () => {
    render(<BlockingHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/no vuelve a entrar/i)
    expect(body).toMatch(/datos se conservan/i)
    expect(body).toMatch(/no es borrar/i)
  })

  it('says it applies at once', () => {
    render(<BlockingHelp />)

    expect(document.body.textContent).toMatch(/en el acto/i)
  })

  /** Between peers there is no authority — and the reason, which is what makes the rule stick. */
  it('explains that nobody blocks an Admin or an Owner, and why', () => {
    render(<BlockingHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/Admin u Owner/)
    expect(body).toMatch(/Entre pares no hay autoridad/i)
    expect(body).toMatch(/no puede entrar a pedir que lo desbloqueen/i)
  })

  it('says both acts need a reason and that unblocking is offered from the same row', () => {
    render(<BlockingHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/motivo obligatorio/i)
    expect(body).toMatch(/Desbloquear/)
  })

  /** And, like the roles section, it teaches the doing and not only the rule (#170). */
  it('walks through blocking, step by step', () => {
    const { container } = render(<BlockingHelp />)

    const steps = container.querySelector('ol')
    expect(steps).not.toBeNull()
    expect(within(steps as HTMLElement).getAllByRole('listitem')).toHaveLength(6)
    // Step 2 answers the question the missing button raises, at the moment it is missing.
    expect(document.body.textContent).toMatch(/Si ese botón no está/i)
  })
})

/** The other half of F3.2's help: what the tray is, and what approving each kind actually does. */
describe('RequestsAdminHelp', () => {
  /**
   * The reason this section exists. The two approvals look identical on screen and do completely
   * different things — an admin who assumes a `TableOpen` builds the table will approve it, tell
   * nobody, and leave whoever asked waiting for a table that is never coming.
   */
  it('says a master grant hands out the role and a table request creates nothing', () => {
    render(<RequestsAdminHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/le da el rol por el mismo camino que «Usuarios»/i)
    expect(body).toMatch(/no crea la mesa/i)
    expect(body).toMatch(/no hay una segunda forma de otorgarlo/i)
  })

  /** #42: the note is required on both acts, and on a rejection it is all whoever asked receives. */
  it('says both acts need a note and that it reaches whoever asked', () => {
    render(<RequestsAdminHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/nota obligatoria/i)
    expect(body).toMatch(/lo único que va a recibir/i)
  })

  /** #136: what the tray opens showing, and how to see the rest. */
  it('explains that it opens filtered by what is pending, and how to see the rest', () => {
    render(<RequestsAdminHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/abre filtrada por «Pendiente»/i)
    expect(body).toMatch(/Sacá ese filtro/i)
  })

  /** The two refusals a screen cannot prevent on its own, explained before they are met. */
  it('explains that a request is resolved once and that a dead reference cannot be acted on', () => {
    render(<RequestsAdminHelp />)

    const body = document.body.textContent ?? ''
    expect(body).toMatch(/se resuelve una sola vez/i)
    expect(body).toMatch(/fantasma/i)
  })

  /** #170: the doing, not only the rule - including the step approving a table request leaves behind. */
  it('walks through resolving, step by step, ending with the table that still has to be created', () => {
    const { container } = render(<RequestsAdminHelp />)

    const steps = container.querySelector('ol')
    expect(steps).not.toBeNull()
    expect(within(steps as HTMLElement).getAllByRole('listitem')).toHaveLength(5)
    expect(document.body.textContent).toMatch(/acordate del paso que falta/i)
  })
})
