---
adr: 262
estado: Aceptada
fecha: 2026-09-20
---

# ADR-0262: Se borra docker-compose.prod.yml: la topología de producción no está decidida, y un archivo la decidía

> Acota #142

## Decisión

Docker se usa para **una sola cosa: la MySQL de desarrollo**. Queda un único `backend/docker-compose.yml`, y no hay `Dockerfile` de la aplicación — el jar corre nativo. Lo que sobrevive es lo que **no** presupone entorno: el perfil `prod` con `${DB_URL}` **sin default**, los mismos nombres de variable para dev y prod, y la regla de que los secretos reales llegan como variables del sistema y nunca como un archivo del repo. `arquitectura` §8.2 pasa de describir un despliegue a enunciar lo que la aplicación exige de cualquiera, y el ejemplo de `systemd` queda marcado como ejemplo

## Por qué

Nadie pidió separar entornos todavía, y **aún no se sabe si producción será una instancia tipo EC2, ECS, Kubernetes u otra cosa**. Un compose de producción no es una duplicación inofensiva del de dev: **decide la topología por adelantado** — afirma que MySQL corre colocada en el mismo host y levantada por compose —, y si el destino termina siendo ECS o un servicio administrado, el archivo no sirve para nada y las tres referencias que lo nombraban mienten. El de `dev` tampoco podía usarse tal cual: lleva `centraldungeon`/`centraldungeon` escrito en un archivo versionado y publica el 3306 en todas las interfaces. Es el **mismo error que #142 ya había corregido una vez**: se empezó con `.env.example` y `.env.prod.example`, se vio que dos archivos que documentan lo mismo divergen sin que nadie lo note, y se dejó uno solo — la corrección se aplicó a los `.env` y no a los compose, que quedaron con la forma que la propia decisión rechaza. `MYSQL_ROOT_PASSWORD` sale de `.env.example` en el mismo movimiento: su único lector era el archivo borrado. **La decisión de fondo queda abierta a propósito** y se toma cuando haya un destino real, no antes
