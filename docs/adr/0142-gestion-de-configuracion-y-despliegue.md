---
adr: 142
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0142: Gestión de configuración y despliegue

## Decisión

**En dev, secretos por `backend/.env` (gitignored), cargado por `springboot4-dotenv` sobre los mismos placeholders `${VAR:default}` de `application*.yml`. En producción, una VM Linux genérica — EC2 u Oracle Compute, da igual cuál — con el jar corriendo por `systemd` y las variables reales inyectadas por `EnvironmentFile`, nunca por un `.env` del repo. Un solo `backend/.env.example`, mismas variables en los dos casos** — `DB_URL` incluida, con default local en dev y sin default en prod — **nunca dos archivos con nombres distintos**

## Por qué

`export` en el perfil de shell (como sí se usa para los MCP servers, `entorno-local` §6) no se documenta en ningún lado del repo: cada clon nuevo, o un cliente probando con sus propias credenciales de Discord, tiene que pedir la lista en vez de encontrarla en `.env.example`. Spring no lee `.env` nativo como Vite, de ahí la librería. Para producción, la prueba corre en una instancia EC2 propia pero el cliente muy probablemente despliega en Oracle Cloud — así que el perfil `prod` (`application-prod.yml`) toma la URL de MySQL completa por `${DB_URL}` sin default, sin asumir dónde vive la base, y nada de esto usa un servicio propietario de ningún proveedor. Se empezó con dos archivos (`.env.example` y `.env.prod.example`) con variables distintas entre sí — `DB_URL` solo existía en el de prod, mientras dev traía la URL harcodeada en `application-dev.yml` — y se corrigió a uno solo: dos listas que documentan lo mismo con nombres distintos envejecen mal, divergen sin que nadie lo note. El archivo de secretos reales de producción vive en el servidor (`/etc/centraldungeon/prod.env` o similar), nunca en este repo. Detalle completo en `arquitectura` §8.1 y §6.2.
