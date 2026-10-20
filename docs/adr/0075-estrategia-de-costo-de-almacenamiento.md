---
adr: 75
estado: Aceptada
fecha: 2026-08-29
modifica: [61]
---

# ADR-0075: Estrategia de costo de almacenamiento

## Decisión

**No hay cuota por usuario.** En su lugar: reutilizar lo ya subido mostrando el historial al momento de adjuntar, comprimir los archivos al guardarlos, y purgar periódicamente los que llevan ~3 meses sin usarse

## Por qué

**Deroga #61.** Una cuota de 20 MB por usuario son 20 GB con mil jugadores, que es exactamente el costo que se quería evitar: la cuota limita al usuario sin reducir el total. Las tres palancas de acá atacan el volumen real — la reutilización evita duplicados, la compresión reduce cada archivo, y la purga por desuso libera lo que ya no le sirve a nadie. Se conserva un tope de tamaño por archivo. **Requiere registrar el último uso de cada archivo**, que hoy no existe.
