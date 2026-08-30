import { useState } from 'react'

/** Abrir/cerrar modales guardando el ítem que los abrió (arquitectura.md 3.3). */
export function useDisclosure<T = undefined>() {
  const [isOpen, setIsOpen] = useState(false)
  const [item, setItem] = useState<T | undefined>(undefined)

  function open(value?: T) {
    setItem(value)
    setIsOpen(true)
  }

  function close() {
    setIsOpen(false)
    setItem(undefined)
  }

  return { isOpen, item, open, close }
}
