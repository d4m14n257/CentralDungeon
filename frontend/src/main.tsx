import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { ThemeCheck } from '@/ThemeCheck'
import '@/styles/globals.css'

const root = document.getElementById('root')
if (!root) throw new Error('#root is missing from index.html')

// Scaffold only. E1 replaces this with providers + RouterProvider (arquitectura.md 3.1).
createRoot(root).render(
  <StrictMode>
    <ThemeCheck />
  </StrictMode>,
)
