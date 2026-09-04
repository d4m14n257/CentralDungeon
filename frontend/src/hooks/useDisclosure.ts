import { useState } from 'react'

/** Opening and closing dialogs while remembering the item that opened them (arquitectura.md 3.3). */
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
