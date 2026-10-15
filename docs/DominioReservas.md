# Planta Libre — Dominio de reservas

**Catálogo de espacios, motor de disponibilidad, reservas y bloqueos**
Complementa al [documento técnico de la entrega N.º 1](DocumentoTecnico.md), que cubre identidad, usuarios y medios de pago.

---

## 1. Qué agrega

Con esto el sistema hace lo que promete: una persona entra, recorre empresa → edificio → piso, ve el plano con el estado de cada espacio en la franja que eligió, abre un cubículo o una sala, marca la franja en un calendario semanal y la reserva. Un administrador ve lo mismo desde arriba: la agenda del día de un piso, el listado de todas las reservas, puede crear reservas a nombre de un cliente, moverlas, cancelarlas o borrarlas, y bloquear espacios o pisos enteros.

Las pantallas siguen los wireframes 3 a 14 y los estados E1 a E5 del canvas de diseño.

| Pantalla | Ruta |
|---|---|
| 3 · Empresas | `/empresas` |
| 4 · Edificios de la empresa | `/empresas/:id` |
| 5 · Pisos del edificio | `/edificios/:id` |
| 6, 6b, 7, 8 · Plano, lista, espacio y confirmación | `/pisos/:id` |
| 9 · Mis reservas | `/mis-reservas` |
| 11 · Agenda del día | `/admin/agenda` |
| 11b · Listado de reservas | `/admin/reservas` |
| 12 · Plano, vista administrador | `/admin/pisos/:id` |
| 13 · Formulario de reserva | `/admin/reservas/nueva`, `/admin/reservas/:id` |
| 14 · Bloqueos | `/admin/bloqueos`, `/admin/bloqueos/nuevo` |

---

## 2. Dos componentes nuevos

Se agregan en la misma arquitectura en capas y con las mismas reglas de dependencia que los tres anteriores.

| Componente | Presentación | Negocio | Datos |
|---|---|---|---|
| **Catálogo** | `CatalogoController`, `CatalogoDTOs` | `CatalogoService` con `CatalogoServiceImpl` | `EmpresaRepository`, `EdificioRepository`, `PisoRepository`, `EspacioRepository`, `ServicioPisoRepository` |
| **Reservas** | `ReservaController`, `AdminReservaController`, `BloqueoController`, `ReservaDTOs` | `ReservaService`, `AdminReservaService`, `BloqueoService`, `MotorDisponibilidad`, `ReglasReserva` | `ReservaRepository`, `BloqueoRepository`, entidades `Reserva`, `ReservaCambio`, `Bloqueo` |

**Catálogo** es solo lectura: la jerarquía de navegación, el plano de cada piso y el detalle de cada espacio, con la disponibilidad ya calculada para la franja pedida. **Reservas** es la parte que escribe: crear, cancelar, mover y borrar reservas, y crear y quitar bloqueos.

Los servicios del piso (cocina, sala de estar, baños) viven en su propia tabla y nunca en la de espacios: se dibujan en el plano porque son lo que hace que alguien prefiera un piso a otro, pero no se reservan y no tienen calendario.

---

## 3. El motor de disponibilidad

Toda la aplicación usa una sola definición de "libre", la de `MotorDisponibilidad`. Es una clase pura: recibe las ocupaciones de un espacio y responde preguntas sobre el tiempo, sin tocar la base. Eso permite probarla con tests unitarios sin levantar Spring y garantiza que el plano, el calendario, la agenda y la validación al reservar nunca se contradigan.

- **Ocupación.** Para el motor, una reserva activa (pendiente o confirmada) y un bloqueo son lo mismo: algo que toma el espacio durante un rango. El tipo solo decide el color y, si se superponen, gana el bloqueo al pintar el estado.
- **Superposición.** Los rangos son semiabiertos: una reserva que termina a las 11:00 no choca con otra que empieza a las 11:00.
- **Editar sin chocar consigo misma.** Al validar la edición de una reserva, se ignora su propia ocupación.
- **Huecos.** Los rangos libres máximos de un día dentro del horario del edificio, a partir del próximo bloque de 30 minutos. De ahí salen "Próximo horario libre", las alternativas que se ofrecen cuando una franja se ocupó mientras el cliente confirmaba, y la sugerencia "Probar 11:30 – 13:30" cuando un piso está lleno.

`Ocupaciones` carga lo que ocupa a un conjunto de espacios en dos consultas (reservas activas y bloqueos), ya agrupado por espacio. El listado de empresas, por ejemplo, calcula los espacios libres de los 522 espacios del catálogo con dos consultas y cálculo en memoria.

### Reglas de uso

`ReglasReserva` concentra las condiciones que cumple cualquier reserva, la cree quien la cree: bloques de 30 minutos, entre 30 minutos y 8 horas, dentro del horario del edificio, de lunes a viernes, que no haya empezado y con hasta 60 días de anticipación. Los mismos valores viajan al cliente en el detalle del espacio, así que el cuadro "Condiciones de uso" no puede quedar desactualizado respecto de lo que se valida.

---

## 4. Concurrencia: dos personas, la misma franja

El caso crítico es que dos clientes confirmen la misma franja casi al mismo tiempo. Se resuelve en tres capas:

1. **Bloqueo de fila.** Toda escritura que toma una franja pasa por `TomaDeFranja`, que primero bloquea la fila del espacio (`SELECT … FOR UPDATE`). Dos pedidos sobre el mismo espacio se ejecutan uno detrás del otro, y el segundo ve la reserva del primero.
2. **Revalidación con datos frescos.** Con el espacio bloqueado, se consultan las ocupaciones de nuevo y se valida contra el motor. Lo que el cliente vio al elegir no se da por bueno.
3. **Restricción en la base.** `reservas` y `bloqueos` tienen una restricción de exclusión de PostgreSQL (`EXCLUDE USING gist` sobre espacio y rango de tiempo). Si algo lograra pasar las dos capas anteriores, la base lo rechaza y la API responde lo mismo que en el caso normal.

La respuesta a una franja tomada es un `409` con código `FRANJA_OCUPADA` y los huecos libres más cercanos del mismo espacio y día. El front muestra esas alternativas sin que el cliente pierda el espacio elegido (estado E4).

Se probó con diez clientes distintos reservando la misma franja al mismo tiempo: una reserva se crea y las otras nueve reciben `409`.

---

## 5. Reglas del administrador

El administrador puede pisar una reserva ajena, pero nunca en silencio.

- **Motivo obligatorio.** Editar o cancelar una reserva de otra persona exige un motivo de al menos 10 caracteres.
- **Bitácora.** Cada cambio queda en `reserva_cambios` con autor, motivo y el detalle de qué cambió ("Horario: lun 28 sep 09:00–12:00 → lun 28 sep 10:00–13:00; Estado: Pendiente → Confirmada"). El cliente la ve en el detalle de su reserva.
- **Aviso por mail.** El cliente recibe un mail cuando le crean, modifican, cancelan o borran una reserva, o cuando un bloqueo se la lleva puesta. Los mails de cancelación salen después del commit y dicen cuánto se devuelve.
- **Bloqueos sobre reservas.** "Piso entero" crea un bloqueo por espacio con un mismo grupo, todo o nada. Si en ese horario hay reservas, la API responde 409 con la lista; el administrador confirma y se repite el pedido con `cancelarReservas`: cada reserva se cancela con reintegro total, queda en su bitácora con el motivo del bloqueo y el cliente recibe un mail. Un bloqueo nunca pisa otro bloqueo.
- **Borrar o cancelar.** Borrar elimina la reserva y su bitácora; para liberar la franja sin perder el registro está el estado Cancelada.

Los permisos nuevos, `RESERVAS_GESTIONAR` y `BLOQUEOS_GESTIONAR`, se agregan al rol ADMIN y se exigen de forma declarativa con `@PreAuthorize` a nivel de controlador. El cliente reserva con `RESERVAS_CREAR`, que el administrador no tiene: el administrador reserva siempre a nombre de un cliente.

### Política de cancelación y cobro

La reserva se cobra al hacerla (el cobro asincrónico de [Integracion.md](Integracion.md) la confirma en segundos). Lo que se devuelve al cancelar depende de quién cancela y con cuánta anticipación:

| Quién cancela | Cuándo | Reintegro |
|---|---|---|
| El cliente | 24 h o más antes del inicio | 100 % |
| El cliente | Entre 24 h y 2 h antes | 50 % |
| El cliente | Menos de 2 h antes | 0 %: puede cancelar para liberar el lugar |
| Un administrador (cancelar, borrar o bloquear) | Siempre | 100 % |

Una reserva ya empezada no se cancela. Una pendiente (todavía sin cobrar) se cancela sin costo, y el cobro que estaba en la cola ya no se hace. Los valores viven en `PoliticaCancelacion` y llegan al front en `condiciones`: el paso de confirmación muestra el total y la política, y el diálogo de cancelar dice cuánto se devuelve en ese momento.

Todas las cancelaciones pasan por un mismo componente, `Cancelaciones`, dentro de la transacción de quien cancela y con la fila de la reserva bloqueada. Calcula el porcentaje, pide el reintegro a la pasarela, marca la reserva y publica `ReservaCancelada` para el mail. **Si la pasarela rechaza el reintegro, la transacción hace rollback y la reserva sigue activa**: nunca queda una reserva cancelada con la plata sin devolver. Cada cobro se reintegra una sola vez (`cobros.monto_reintegrado`, migración V17).

---

## 6. Decisiones de diseño

- **Estado derivado.** `FINALIZADA` no se guarda: una reserva activa cuyo fin ya pasó se informa como finalizada. No hace falta un proceso programado que actualice filas, y no hay forma de que el estado quede desfasado.
- **Hora local del edificio.** Las reservas se guardan en hora local y el "ahora" sale de un `Clock` con zona `America/Argentina/Buenos_Aires`, porque Render corre en UTC. Sin esto, a las 21 h de Buenos Aires el servidor creería que ya es el día siguiente.
- **"Libres hoy" que no miente de noche.** Los listados cuentan los espacios libres de la jornada de cada edificio: hoy mientras quede lugar para una reserva mínima, y el siguiente día hábil cuando ya cerró. La etiqueta dice a qué día se refiere.
- **Filtros en la URL.** Fecha, franja, tipo, capacidad, ventana, vista y espacio abierto viven en la URL: una franja se comparte o se recarga tal cual, y el botón atrás deshace un filtro.
- **Plano como grilla, no como imagen.** Cada espacio y servicio tiene una posición en una grilla (42 columnas en casi todos los pisos). El plano se dibuja con esa grilla, así que pintar estados, atenuar lo que no cumple un filtro y calcular distancias ("Baño A · 14 m") sale de los mismos datos. La estructura también es dato: ver la sección 7.
- **Estados legibles sin color.** Libre, ocupado, bloqueado, seleccionado y servicio combinan color, trama y un símbolo distinto, como pide el sistema de diseño.
- **Datos de demostración vivos.** El catálogo se carga con una migración (`V12`, generada por `scripts/generar_catalogo.py`). Las reservas no: un catálogo sin reservas muestra todo libre y no prueba nada. `DatosDemoInicializador` llena al arrancar los días hábiles de las dos semanas alrededor de hoy, rodeando las reservas reales, y en cada reinicio solo agrega los días nuevos. Se desactiva con `DEMO_HABILITADO=false`.

---

## 7. Disposiciones de planta

No todos los pisos son iguales. Cada piso tiene una de seis disposiciones:

| Disposición | Cómo es | Dónde |
|---|---|---|
| **Pasillo central** | Filas de cubículos, un pasillo de lado a lado, una fila de salas y otra de servicios. Es la planta de los wireframes. | Torre Callao y algún piso suelto |
| **Núcleo central** | Ascensores y escaleras en el medio, un pasillo que los rodea y los puestos sobre las fachadas norte y sur. Las salas van a las esquinas o junto al núcleo, y los baños debajo de los ascensores. | Distrito Norte, Estación Retiro, Ribera Puerto Madero, Delta Centro |
| **Peine** | Salas en la cabecera, un pasillo transversal y un pasillo central que baja entre dos alas de cubículos. Los servicios cierran el fondo a los dos lados. | Nodo Palermo, Polo Tecnológico y otros |
| **Open space con islas** | Cubículos agrupados en islas de cuatro, con pasillos alrededor de cada isla. Las salas son islas cerradas entre las abiertas. | Nodo Palermo, Polo Tecnológico, Sede Avellaneda, Casa Lumen |
| **Doble pasillo** | Puestos sobre las dos fachadas y, entre dos pasillos paralelos, una banda cerrada con los servicios y las salas. | Sede Constitución y un piso de Distrito Norte, Ribera Puerto Madero y Delta Centro |
| **En L con terraza** | El ala de cubículos con su pasillo sobre el lado oeste; el pasillo dobla abajo y pasa bajo la terraza, que da a él. En planta baja la terraza es un patio. Los puestos que dan a la terraza tienen ventanal. | Casa Barracas, Estudio Almagro, Ribera Tigre, Casa Lumen |

**La disposición es un dato, no código.** El plano se arma con rectángulos en la grilla: los espacios y los servicios, como antes, y ahora también las áreas estructurales de la tabla `areas_plano`: `PASILLO`, `NUCLEO` (ascensores y escaleras), `APOYO` (lo que los espacios dejan libre: cabinas telefónicas, impresión, jardín interior, office, depósito, archivo) y `EXTERIOR` (terraza o patio). La API manda esos rectángulos y el front los dibuja sin saber de qué disposición se trata. `pisos.disposicion` es solamente el nombre que se muestra en el encabezado del plano y en la lista de pisos.

**Ventanas.** Cada espacio guarda de qué lado tiene la ventana (`orientacion`). El front dibuja la marca azul sobre el muro exterior o, si da a una terraza o patio, sobre el borde que la mira.

**Cómo se generan.** `scripts/generar_disposiciones.py` toma el contenido de cada piso del catálogo (cuántos cubículos, qué salas, qué servicios) y lo reparte con la disposición asignada a su edificio. Cada disposición es un módulo en `scripts/disposiciones/`. Antes de escribir nada, el generador valida cada piso:

- que nada se superponga y que todo quede dentro de la grilla;
- que estén todos los espacios y servicios del piso;
- que se llegue caminando a cada espacio reservable, por pasillos o por la zona abierta de cubículos, nunca a través de una sala cerrada;
- que las terrazas den a un pasillo.

Los huecos se llenan según dónde caen: entre los puestos, una cabina telefónica, impresión, un jardín o lockers; en el fondo, un office o un depósito; junto al núcleo, archivo o instalaciones. Cada piso recorre las listas desde un punto distinto, y dos huecos pegados se unen en uno.

Si un piso no entra en su disposición (por ejemplo, 14 cubículos y 3 salas no caben en un núcleo central), el generador prueba con la siguiente y lo informa. El resultado es una migración (`V15`, después `V16`) que actualiza posiciones, ventanas y textos de todos los pisos **sin cambiar ids ni códigos**: las reservas y los bloqueos existentes siguen apuntando a los mismos espacios. Una migración ya aplicada no se vuelve a escribir, porque Flyway rechaza el cambio: cada cambio de disposiciones va a la versión siguiente. Por eso la `V12` y la `V15` quedan como están.

```bash
python3 scripts/generar_disposiciones.py V17 --ascii
```

escribe la próxima migración y dibuja cada piso en texto, para revisarlo sin levantar la aplicación.

**Para agregar una disposición:** crear un módulo en `scripts/disposiciones/` con `CODIGO`, `ETIQUETA` y `disponer(piso)`, registrarlo en `disposiciones/__init__.py`, sumar el código y su nombre en `Disposicion.java`, y asignarlo a los edificios en `ASIGNACION`. El back y el front no cambian.

---

## 8. API

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/empresas`, `/api/empresas/{id}`, `/api/edificios`, `/api/edificios/{id}` | `ESPACIOS_VER` |
| GET | `/api/pisos/{id}/plano?fecha&desde&hasta&tipo&capacidadMinima&ventana` | `ESPACIOS_VER` |
| GET | `/api/pisos/{id}/espacios`, `/api/espacios/{id}` | `ESPACIOS_VER` |
| GET | `/api/espacios/{id}/agenda?desde&hasta`, `/api/espacios/{id}/franjas-libres` | `ESPACIOS_VER` |
| POST | `/api/reservas` | `RESERVAS_CREAR` |
| GET | `/api/reservas/mias`, `/api/reservas/{id}` | `RESERVAS_VER_PROPIAS` |
| PATCH | `/api/reservas/{id}/cancelar` | `RESERVAS_CREAR` |
| GET, POST, PUT, DELETE | `/api/admin/reservas`, `/api/admin/agenda`, `/api/admin/clientes` | `RESERVAS_GESTIONAR` |
| GET, POST, DELETE | `/api/admin/bloqueos` | `BLOQUEOS_GESTIONAR` |

---

## 9. Verificación

- **Tests unitarios** del motor y de las reglas (`MotorDisponibilidadTest`, `ReglasReservaTest`): contigüidad, envolvimiento, edición sin autochoque, prioridad del bloqueo, huecos, fin de semana, alternativas y sugerencias. Junto con los anteriores, 25 tests en verde.
- **Prueba de concurrencia** de diez reservas simultáneas sobre la misma franja: una se crea, nueve reciben `409`.
- **Recorrido de punta a punta** en el navegador: elegir franja arrastrando en el calendario, confirmar, ver el aviso en Mis reservas; franja tomada mientras se confirmaba; piso sin lugares; filtros sin resultados; mover una reserva arrastrándola como administrador.

---

## 10. Límites conocidos

- La liberación automática por no presentarse (no-show) necesita un check-in, que no existe.
- No hay reservas recurrentes ni invitados.
- El refresco de sesión sigue pendiente: al vencer el token, el usuario vuelve a iniciar sesión.
