"""Tower floor: elevators and stairs in the middle, a corridor all around them, and the work
areas on the facades. Rooms go to the corners when a facade row has room, otherwise next to the
core; kitchen and lounge sit beside the core, bathrooms under the elevators.

    C C C C C C C [SALA]       row 0   north facade
    ==================         row 1   corridor
    = [zona] [NUCLEO] [zona] = rows 2-3: left zone, core, right zone, closed by side corridors
    =        [baños ]        =
    ==================         row 4   corridor
    [SALA] C C C C C C         row 5   south facade
"""
from .modelo import APOYO, NUCLEO, PASILLO, Area, Espacio, NoEntra, Plano, Rect, Servicio, cubiculos, rellenos_para, salas

CODIGO = "NUCLEO_CENTRAL"
ETIQUETA = "Núcleo central"
COLUMNAS = 42
FILAS = 6
ANCHO_CUBICULO = 6
SALA_MINIMA = 10
ZONA = 12            # width of each zone of the core band; the core itself is as wide
LADO_PASILLO = 3     # side corridors that close the ring


def disponer(piso):
    codigos = cubiculos(piso.cubiculos)
    norte = min(7, (len(codigos) + 1) // 2)
    sur = len(codigos) - norte
    if sur > 7:
        raise NoEntra("más de 14 cubículos")

    plano = Plano(CODIGO, COLUMNAS, FILAS, ["NORTE", "SUR", "ESTE", "OESTE"] if piso.luz else ["NORTE", "SUR"],
                  acceso="el pasillo que rodea el núcleo")
    relleno = rellenos_para(piso)
    pendientes = salas(piso)
    # South first: the north facade keeps more room for cubicles, which want the light.
    _fachada(plano, codigos[norte:], 5, "sur", pendientes, relleno)
    _fachada(plano, codigos[:norte], 0, "norte", pendientes, relleno)

    plano.areas += [
        Area(PASILLO, "Pasillo", Rect(0, 1, COLUMNAS, 1)),
        Area(PASILLO, "Pasillo", Rect(0, 4, COLUMNAS, 1)),
        Area(PASILLO, "Pasillo", Rect(0, 2, LADO_PASILLO, 2)),
        Area(PASILLO, "Pasillo", Rect(COLUMNAS - LADO_PASILLO, 2, LADO_PASILLO, 2)),
    ]
    _nucleo(plano, piso, relleno)
    _zonas(plano, piso, pendientes, relleno)
    return plano


def _fachada(plano, codigos, fila, nombre, pendientes, relleno):
    """One facade row: the cubicles, and the rooms that fit in what they leave free."""
    libre = COLUMNAS - ANCHO_CUBICULO * len(codigos)
    cuantas = min(len(pendientes), libre // SALA_MINIMA, 2)
    if cuantas == 0:
        x = 0
        for codigo in codigos:
            plano.espacios.append(_cubiculo(codigo, x, fila, nombre))
            x += ANCHO_CUBICULO
        if libre:
            plano.areas.append(Area(APOYO, relleno.etiqueta("puestos"), Rect(x, fila, libre, 1)))
        return

    anchos = [libre // cuantas + (1 if i < libre % cuantas else 0) for i in range(cuantas)]
    lado_sala = ["este"] if cuantas == 1 else ["oeste", "este"]
    x = 0
    if cuantas == 2:
        plano.espacios.append(_sala(pendientes.pop(0), Rect(0, fila, anchos[0], 1), f"Esquina {nombre}{lado_sala[0]}"))
        x = anchos[0]
    for codigo in codigos:
        plano.espacios.append(_cubiculo(codigo, x, fila, nombre))
        x += ANCHO_CUBICULO
    plano.espacios.append(_sala(pendientes.pop(0), Rect(x, fila, anchos[-1], 1), f"Esquina {nombre}{lado_sala[-1]}"))


def _nucleo(plano, piso, relleno):
    x = LADO_PASILLO + ZONA
    banos = [s for s in piso.servicios if s[0] == "BANO"]
    if not banos:
        plano.areas.append(Area(NUCLEO, "Ascensores · escaleras", Rect(x, 2, ZONA, 2)))
        return
    plano.areas.append(Area(NUCLEO, "Ascensores · escaleras", Rect(x, 2, ZONA, 1)))
    mitad = ZONA // 2
    for i, (tipo, nombre, _) in enumerate(banos[:2]):
        plano.servicios.append(Servicio(tipo, nombre, Rect(x + i * mitad, 3, mitad, 1)))
    if len(banos) == 1:
        plano.areas.append(Area(APOYO, relleno.etiqueta("nucleo"), Rect(x + mitad, 3, mitad, 1)))
    if len(banos) > 2:
        raise NoEntra("más de dos baños")


def _zonas(plano, piso, pendientes, relleno):
    """Left and right of the core: leftover rooms take a whole zone, kitchen and lounge one row."""
    zonas = [(LADO_PASILLO, "oeste"), (LADO_PASILLO + 2 * ZONA, "este")]
    filas_libres = []
    for x, lado in zonas:
        if pendientes:
            plano.espacios.append(_sala(pendientes.pop(0), Rect(x, 2, ZONA, 2), f"Junto al núcleo, lado {lado}"))
        else:
            filas_libres += [(x, 2), (x, 3)]
    if pendientes:
        raise NoEntra("no entran todas las salas")

    otros = [s for s in piso.servicios if s[0] != "BANO"]
    if len(otros) > len(filas_libres):
        raise NoEntra("no entran los servicios junto al núcleo")
    for (tipo, nombre, _), (x, y) in zip(otros, filas_libres):
        plano.servicios.append(Servicio(tipo, nombre, Rect(x, y, ZONA, 1)))
    sobrantes = filas_libres[len(otros):]
    for x, y in sobrantes:
        if (x, y - 1) in sobrantes:
            continue                       # already covered by the zone-high block above
        alto = 2 if (x, y + 1) in sobrantes else 1
        plano.areas.append(Area(APOYO, relleno.etiqueta("isla" if alto == 2 else "fondo"), Rect(x, y, ZONA, alto)))


def _cubiculo(codigo, x, fila, fachada):
    return Espacio(codigo, "CUBICULO", 1, Rect(x, fila, ANCHO_CUBICULO, 1), f"Fachada {fachada}, frente al núcleo")


def _sala(sala, rect, ubicacion):
    codigo, tipo, capacidad = sala
    return Espacio(codigo, tipo, capacidad, rect, ubicacion)
