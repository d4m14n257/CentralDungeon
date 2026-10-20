---
adr: 112
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0112: Organización de carpetas del frontend

## Decisión

**Se agrupa por dominio, nunca por tipo de archivo.** `features/<dominio>/components/` es **plano y con sufijos** (`Form`, `Dialog`, `Card`, `Badge`, `Page`…). Una feature no importa de otra: una pieza sube a la raíz **cuando una segunda feature ya la necesita**, y al subir pierde el dominio. Sin barrels; los tests van junto al archivo que prueban

## Por qué

El frontend viejo agrupaba por tipo en la raíz: `forms/`, `contexts/`, `api/`, `constants/`, `normalize/`, `styles/`, `helper/`. El costo es que tocar una mesa significaba abrir siete carpetas, y que `forms/` mezclaba cinco dominios sin relación. El umbral de dos features para subir a la raíz es más bajo que las tres repeticiones que pide §2.4 en el backend, y la razón es concreta: acá la alternativa a subir no es duplicar un poco, es un import entre features que está prohibido. El plano con sufijos se eligió sobre subcarpetas por tipo (`modals/`, `actions/`) porque el legacy ya mostró el resultado: carpetas de un solo archivo, como `components/tables/status/`. Detalle completo en skill `arquitectura` §3.1.1–§3.1.4.
