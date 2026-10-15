"""Open space: cubicles grouped in islands of four (two by two), with corridors around every
island. Rooms are closed islands among the open ones; services close the floor at the back.

    ==[C C][==][C C][==][C C]     rows 0-1: a band of three islands, corridors in between
    ==[C C][==][C C][==][C C]
    ==========================     corridor across
    ==[C C][==][SALA][==][ * ]     next band: last open island, rooms, rest areas
    ==[C  ][==][    ][==][   ]
    ==========================
    [cocina][baño][ office ]       services
"""
from .modelo import APOYO, PASILLO, Area, Espacio, Plano, Rect, Servicio, cubiculos, muros, rellenos_para, salas

CODIGO = "ISLAS"
ETIQUETA = "Open space con islas"
COLUMNAS = 42
PASO = 2                    # width of the corridors between islands
ISLA = 12                   # two cubicles across
X_ISLAS = [PASO, 2 * PASO + ISLA, 3 * PASO + 2 * ISLA]
POR_BANDA = len(X_ISLAS)
ANCHO_SERVICIO = {"COCINA": 14, "SALA_ESTAR": 14, "BANO": 7}


def disponer(piso):
    codigos = cubiculos(piso.cubiculos)
    grupos = [codigos[i:i + 4] for i in range(0, len(codigos), 4)]
    lista = salas(piso)
    bandas = (len(grupos) + len(lista) + POR_BANDA - 1) // POR_BANDA
    fila_servicios = 3 * bandas
    filas = fila_servicios + (1 if piso.servicios else 0)
    ventanas = muros(piso.ventanas, {"NORTE", "ESTE"} if piso.luz else set())
    plano = Plano(CODIGO, COLUMNAS, filas, ventanas, acceso="los pasillos que rodean las islas")
    relleno = rellenos_para(piso)

    for banda in range(bandas):
        y = 3 * banda
        for x in range(0, COLUMNAS - ISLA, PASO + ISLA):
            plano.areas.append(Area(PASILLO, "Pasillo", Rect(x, y, PASO, 2)))
        plano.areas.append(Area(PASILLO, "Pasillo", Rect(0, y + 2, COLUMNAS, 1)))

    slots = [(x, 3 * (i // POR_BANDA)) for i, x in enumerate(X_ISLAS * bandas)]
    for numero, (grupo, (x, y)) in enumerate(zip(grupos, slots), start=1):
        celdas = [(x, y), (x + 6, y), (x, y + 1), (x + 6, y + 1)]
        for codigo, (cx, cy) in zip(grupo, celdas):
            plano.espacios.append(Espacio(codigo, "CUBICULO", 1, Rect(cx, cy, 6, 1), _ubicacion(numero, cx)))
        for cx, cy in celdas[len(grupo):]:
            plano.areas.append(Area(APOYO, relleno.etiqueta("puestos"), Rect(cx, cy, 6, 1)))

    libres = slots[len(grupos):]
    for (codigo, tipo, capacidad), (x, y) in zip(lista, libres):
        plano.espacios.append(Espacio(codigo, tipo, capacidad, Rect(x, y, ISLA, 2), "Isla cerrada, entre los puestos"))
    for x, y in libres[len(lista):]:
        plano.areas.append(Area(APOYO, relleno.etiqueta("isla"), Rect(x, y, ISLA, 2)))

    x = 0
    for tipo, nombre, _ in piso.servicios:
        ancho = ANCHO_SERVICIO[tipo]
        plano.servicios.append(Servicio(tipo, nombre, Rect(x, fila_servicios, ancho, 1)))
        x += ancho
    if piso.servicios and x < COLUMNAS:
        plano.areas.append(Area(APOYO, relleno.etiqueta("fondo"), Rect(x, fila_servicios, COLUMNAS - x, 1)))
    return plano


def _ubicacion(isla, x):
    if x + 6 == COLUMNAS:
        return f"Isla {isla}, del lado de la ventana"
    if x in X_ISLAS:
        return f"Isla {isla}, junto al pasillo"
    return f"Isla {isla}, en el centro de la isla"
