# Planta Libre — Propuestas de IA y su versión convencional

**Tres ideas evaluadas, cómo se resolverían sin IA y cuánto esfuerzo ahorra cada camino**
Complementa al [dominio de reservas](DominioReservas.md). Las estimaciones son horas de desarrollo de una persona que ya conoce el código.

---

## 1. Resumen

Se evaluaron tres lugares donde una feature de IA podría aportar. En los tres, la versión convencional resuelve el mismo problema con menos esfuerzo, sin dependencias externas y con resultados deterministas que se prueban con JUnit.

| Idea | Con IA | Convencional | Ahorro |
|---|---|---|---|
| 1 · Búsqueda de espacios | 31–44 h, depende del cupo de una API externa | 5–8 h (filtros en el plano) o 14–21 h (con búsqueda en todos los edificios) | 50 % a 85 % |
| 2 · Reubicación por bloqueo | 32–45 h | 20–28 h | 35 % a 40 % |
| 3 · Demanda y ocupación | No estimable: no hay datos reales para entrenar ni validar | 8–12 h | — |

El motivo es el catálogo. Los 522 espacios usan un vocabulario chico y cerrado:

- 4 tipos de espacio (cubículo, sala de reuniones, sala de conferencias, sala de presentaciones).
- 2 niveles de ruido, determinados por el tipo: los cubículos son "Bajo" y las salas "Medio".
- 2 valores de climatización.
- 16 ítems de equipamiento (monitor, cámara, micrófono, proyector, pizarra, etc.).
- Las descripciones salen de plantillas: lo que más varía entre ellas es la ubicación ("fila 1 del piso 4 de Torre Callao").

Con datos así, todo lo que una IA "entendería" se traduce a un filtro o a un puntaje fijo.

---

## 2. Idea 1 · Búsqueda de espacios

### La versión con IA

El cliente escribe "mañana a la tarde, 3 h, videollamada con 2 personas, con monitor" y un LLM lo convierte en filtros, busca en todos los edificios y explica cada propuesta.

Costo: 31–44 h. Necesita un cliente REST con timeout, el prompt y el esquema de intención, un fallback para cuando la API responde 429 o tarda más de 5 s, WireMock en los tests y un juego de unos 30 pedidos para evaluar el prompt. Con un tier gratuito (Groq, Gemini) el dinero es cero, pero el cupo diario cambia sin aviso y puede cortarse el día de una demo.

### La versión convencional

Todo lo que el texto libre pedía tiene un equivalente fijo:

| Lo que pide el cliente | Filtro |
|---|---|
| "Videollamada" | Sala con Cámara y Micrófono |
| "Tranquilo" | El tipo ya lo define: el ruido depende de él |
| "Con monitor" | Ítem de equipamiento |
| "Con luz" | Ventana, y la orientación si se quiere precisar |
| "Para 3 personas" | `capacidadMinima`, que ya existe |

Se hace en dos pasos independientes:

**A. Filtro de equipamiento en el plano (5–8 h).** Hoy `cumpleFiltros` en `CatalogoServiceImpl` filtra en memoria por `tipo`, `capacidadMinima` y `ventana`. Se agrega una condición más sobre la lista `equipamiento`, comparando el nombre antes del " · " (el mismo corte que ya hace `Vistas.equipamientoCorto`). `FiltroPlano` suma un parámetro y el controlador lo lee de la URL. En el front, chips con los ítems del piso y el valor en la URL, como el resto de los filtros. El plano ya atenúa lo que no cumple, así que no hay que dibujar nada nuevo.

- Back: 1–2 h. Front: 3–4 h. Tests: 1–2 h.
- Detalle de datos: el seed tiene "Micrófono" y "Micrófonos" como ítems distintos. Se normalizan al armar el filtro, sin tocar las migraciones ya desplegadas.

**B. Búsqueda en todos los edificios (9–13 h).** Lo que la IA aportaba de verdad era no tener que recorrer empresa, edificio y piso. Un endpoint `GET /api/espacios/buscar` con los mismos filtros resuelve eso: carga las ocupaciones con `Ocupaciones` (dos consultas para todo el catálogo, como el listado de empresas), valida con `MotorDisponibilidad` y ordena. Cada resultado es un link a `/pisos/:id?fecha&desde&hasta&espacio=…`, que ya abre la pantalla de confirmación con el cobro y las validaciones de siempre.

- Back: 3–5 h. Front: 4–6 h. Tests: 2 h.

### Diferencia de esfuerzo

| | Con IA | Convencional |
|---|---|---|
| Horas | 31–44 | 5–8 (A) · 14–21 (A + B) |
| Dependencia externa | Sí, con cupo variable | No |
| Fallback y timeout | Obligatorios | No hacen falta |
| Tests | WireMock + evaluación del prompt | JUnit parametrizado |
| Resultado ante el mismo pedido | Puede variar | Siempre el mismo |

Si la materia pide una feature de IA, se agrega después como capa fina sobre B: el LLM traduce el texto a los mismos parámetros del endpoint y nada más. Sobre una búsqueda que ya existe, esa capa cuesta 17–23 h, y si la API falla la pantalla sigue funcionando con los filtros.

---

## 3. Idea 2 · Reubicación por bloqueo

### El problema

Hoy, cuando un bloqueo pisa reservas, `BloqueoServiceImpl` responde `409` con la lista de afectadas y, si el administrador confirma con `cancelarReservas`, las cancela con reintegro total. La propuesta es ofrecer a cada cliente un espacio equivalente en la misma franja en lugar de cancelarle la reserva.

### La versión con IA

Embeddings de los 522 espacios calculados offline, similitud en memoria y algoritmo húngaro para repartir los lugares libres. Costo: 32–45 h.

Los embeddings no ayudan acá: compararían textos que se diferencian sobre todo por el nombre del edificio y la fila, y no por lo que importa (equipamiento, ventana, capacidad). Un puntaje explícito es más preciso.

### La versión convencional

1. **Candidatos.** Espacios del mismo edificio, del **mismo tipo** y con capacidad mayor o igual, libres en la franja según `MotorDisponibilidad`. Se excluyen los espacios del bloqueo.
2. **Puntaje.** Suma fija: mismo piso, misma condición de ventana, menor diferencia de capacidad, más equipamiento en común. Se explica solo: "mismo piso, con ventana, mismo monitor".
3. **Asignación greedy.** Las reservas se recorren por orden de creación y cada una toma el mejor candidato que siga libre. Es justo ("primero reservó, primero elige") y fácil de explicar a un cliente. El húngaro da el óptimo global, pero puede darle a alguien un lugar peor sin una razón que se pueda contar, y con las pocas reservas que afecta un bloqueo la diferencia no se nota.
4. **Aplicar.** En la misma transacción del bloqueo, con la fila del candidato bloqueada por `TomaDeFranja` y también en orden de id, para no abrir la puerta a deadlocks entre dos administradores. Se cambia el espacio, se registra en la bitácora con el motivo del bloqueo y se avisa por mail. Las que no tengan candidato siguen el camino actual: cancelación con reintegro total.

Restringir al mismo tipo evita un problema entero: las tarifas son por tipo de espacio (`app.tarifas.por-hora` en `CobroConfig`), así que el cobro queda igual y no hay diferencia que cobrar ni reintegrar.

| Tarea | Horas |
|---|---|
| Candidatos, puntaje y asignación | 4–6 |
| Integración con el bloqueo: propuestas en el `409`, aplicar, bitácora, mail nuevo | 7–10 |
| Front: en el diálogo del bloqueo, la propuesta de cada reserva | 5–7 |
| Tests (puntaje parametrizado, integración con Testcontainers) | 4–5 |
| **Total** | **20–28** |

### Diferencia de esfuerzo

La diferencia acá es menor que en la idea 1, porque la mayor parte del trabajo es el flujo (transacción, bitácora, mail, pantalla) y ese es igual en los dos caminos. Lo que se ahorra son 12–17 h de script de embeddings, del algoritmo húngaro y de lidiar con resultados que no se pueden explicar.

---

## 4. Idea 3 · Demanda y ocupación

### La versión con IA

Predecir la demanda por franja o detectar a quién no se presenta (no-show). No es viable:

- Las reservas de demostración son sintéticas (`DatosDemoInicializador`): un modelo aprendería el patrón del generador.
- Detectar no-shows necesita un check-in, que no existe (sección 10 del dominio de reservas).

No se puede estimar el esfuerzo porque no hay con qué entrenar ni con qué validar.

### La versión convencional

Un reporte de ocupación para el administrador: qué porcentaje de cada piso estuvo ocupado por día de la semana y bloque de 30 minutos en las últimas semanas. Es la información que un pronóstico simple usaría, mostrada tal cual.

- **Consulta.** Una sola consulta en PostgreSQL con `generate_series` para los bloques y la superposición con las reservas activas, agrupada por piso, día y bloque. Se calcula al pedirla: no hace falta una vista materializada ni un proceso programado, en la misma línea del estado derivado que ya usa el sistema.
- **Pantalla.** Una grilla de días por bloques con la intensidad en el color de cada celda, hecha con Tailwind. No hace falta sumar una librería de gráficos al front.

| Tarea | Horas |
|---|---|
| Consulta y endpoint | 3–4 |
| Pantalla del administrador | 4–6 |
| Tests | 1–2 |
| **Total** | **8–12** |

Con los datos de demostración el reporte muestra el patrón del generador, pero la feature es real: con reservas reales muestra la ocupación real.

---

## 5. Cómo se evita la sobreingeniería

- **Una sola definición de "libre".** Todo pasa por `MotorDisponibilidad`. Nada se reescribe en SQL, para que el plano, la búsqueda y la reubicación no se contradigan.
- **Filtrar en memoria, como ya se hace.** El catálogo entra entero en memoria y `Ocupaciones` lo carga en dos consultas. No hacen falta índices nuevos, pgvector ni búsqueda de texto completo.
- **Sin infraestructura nueva.** Ni base vectorial, ni colas, ni procesos programados, ni APIs externas.
- **El algoritmo más simple que se pueda explicar.** Greedy en lugar de húngaro. Puntaje con pesos fijos en lugar de un modelo.
- **Recortar el alcance donde el dominio lo permite.** Reubicar solo al mismo tipo elimina toda la lógica de diferencias de precio.
- **Reutilizar las pantallas.** La búsqueda termina en un link a la pantalla de confirmación que ya existe. El plano ya sabe atenuar lo que no cumple un filtro.

---

## 6. Orden sugerido

| Orden | Qué | Horas | Por qué |
|---|---|---|---|
| 1 | Idea 1A · Filtro de equipamiento | 5–8 | El mayor valor por hora: cubre la mayoría de los pedidos que motivaban la IA |
| 2 | Idea 2 · Reubicación convencional | 20–28 | Cambia un resultado malo para el cliente (perder la reserva) por uno bueno |
| 3 | Idea 1B · Búsqueda en todos los edificios | 9–13 | Evita recorrer la jerarquía cuando no importa el edificio |
| 4 | Idea 3 · Reporte de ocupación | 8–12 | Útil para el administrador, sin urgencia |

Cualquiera que entre en el alcance tiene que figurar en la entrega del diseño del 03/11, y sus horas compiten con las de la prueba de testing del 10/11.
