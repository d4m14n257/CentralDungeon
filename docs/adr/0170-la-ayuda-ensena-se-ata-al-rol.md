---
adr: 170
estado: Aceptada
fecha: 2026-09-02
modificada-por: [231]
---

# ADR-0170: La ayuda enseña, se ata al rol de quien lee, y resalta la sección que se vino a leer — (b) y (c) caen con #231

## Decisión

Tres cosas sobre `/help` (#167, #168). **(a) Enseña, no describe**: cada bloque de una tarea lleva sus **pasos numerados** —cómo se crea una mesa, cómo se acepta un candidato, cómo se asignan masters, cómo se usa el buscador— además de lo que hay que saber. **(b) Cada audiencia se ve solo con su rol**: la pestaña no aparece si no lo tenés, y entrar por URL corta con `ForbiddenState`. `Owner` es la única excepción y ve todo, porque es un admin con más alcance (#169); el contexto Master se abre además con una fila viva en `masters`, igual que el `ContextSwitcher` (#135). **(c) La sección que el `#ref` nombra queda resaltada** y marcada con `aria-current="location"`, mientras la URL la nombre

## Por qué

(a) Una ayuda que solo define términos contesta "qué es" cuando la pregunta real es "cómo se hace": el paso numerado es la forma que se sigue con el sitio abierto al lado. (b) La ayuda de un rol que no se tiene no es un secreto —es texto fijo, y esto **no es seguridad** (#103)— pero sí es ruido: leer cómo se aprueba una mesa no ayuda a nadie a postularse, y mezclarlo hace más difícil encontrar lo propio. (c) Quien llega desde un `#ref` cae en medio de una página larga y sin una marca no sabe cuál de los bloques vino a leer; el resaltado se apaga al navegar a otra sección y no con un temporizador que obligue a leer rápido
