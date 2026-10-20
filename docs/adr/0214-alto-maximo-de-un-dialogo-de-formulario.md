---
adr: 214
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0214: Alto máximo de un diálogo de formulario

## Decisión

**`FormDialog` acota su alto y hace scroll adentro**

## Por qué

Encontrado por el e2e de F1.5, y es un bug de producto y no del test: `DialogContent` de shadcn se centra con `top-50%` + `translate-y-[-50%]` y **no acota ni el alto ni el overflow**, así que un formulario más alto que la ventana se derrama por arriba y por abajo — y la mitad de arriba no se puede alcanzar, porque lo que scrollea es la página de atrás. El diálogo de entrega de F1.5, con editor y selector de archivos, es el primero lo bastante largo como para chocarlo. Se arregla en `FormDialog`, que es nuestro, y no en `components/ui/`, que lo genera el CLI de shadcn y no se edita a mano
