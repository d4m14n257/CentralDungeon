/**
 * Tamaño de página por tipo de listado (decisiones.md #173). Vive acá y no en cada hook para que
 * "cuánto trae una página" sea una decisión y no un número suelto repetido.
 */
export const pageSize = {
  /** Grilla de fichas a tres columnas: cuatro filas completas. */
  explorer: 12,
  /** Listados de lectura en una columna: mis mesas, mis postulaciones, notificaciones. */
  list: 20,
  /** Listas de trabajo de admin: más densas, y con el total a la vista. */
  adminQueue: 25,
  /** Resultados dentro de un diálogo, donde el espacio es el que hay. */
  picker: 8,
} as const
