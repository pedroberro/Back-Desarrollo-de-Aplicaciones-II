"""Comb floor: the rooms across the head of the floor, a corridor under them, and a spine that
runs down the middle with cubicles on both sides; the services close the far end.

    [SALA-A ][SALA-B ][SALA-C]     row 0   head
    ==========================     row 1   corridor across
    C C C   ||||   C C C           rows 2..: cubicles on both sides of the spine
    C C C   ||||   C C C
    [cocina][baño] |||| [estar][baño]   last row: services, on both sides of the spine
"""
from .modelo import APOYO, PASILLO, Area, Espacio, NoEntra, Plano, Rect, Servicio, cubiculos, salas

CODIGO = "PEINE"
ETIQUETA = "Peine"
COLUMNAS = 40
ANCHO_CUBICULO = 6
ESPINA = 4
ALA = (COLUMNAS - ESPINA) // 2           # 18: three cubicles per side
X_ESPINA = ALA
# Slots of a cubicle row, nearest to the spine first, so a short last row stays reachable.
SLOTS = [(X_ESPINA - 6, "oeste"), (X_ESPINA + ESPINA, "este"), (X_ESPINA - 12, "oeste"),
         (X_ESPINA + ESPINA + 6, "este"), (0, "oeste"), (COLUMNAS - 6, "este")]
ANCHO_SERVICIO = {"COCINA": 12, "SALA_ESTAR": 12, "BANO": 6}


def disponer(piso):
    codigos = cubiculos(piso.cubiculos)
    filas_cub = (len(codigos) + len(SLOTS) - 1) // len(SLOTS)
    ultima = 2 + filas_cub                      # services row
    plano = Plano(CODIGO, COLUMNAS, ultima + 1, list(piso.ventanas), acceso="el pasillo de la cabecera")

    lista = salas(piso)
    x = 0
    for i, (codigo, tipo, capacidad) in enumerate(lista):
        ancho = COLUMNAS // len(lista) + (1 if i < COLUMNAS % len(lista) else 0)
        plano.espacios.append(Espacio(codigo, tipo, capacidad, Rect(x, 0, ancho, 1), "Cabecera del piso"))
        x += ancho

    plano.areas.append(Area(PASILLO, "Pasillo", Rect(0, 1, COLUMNAS, 1)))
    plano.areas.append(Area(PASILLO, "Pasillo", Rect(X_ESPINA, 2, ESPINA, filas_cub + 1)))

    for i, codigo in enumerate(codigos):
        fila = 2 + i // len(SLOTS)
        x, lado = SLOTS[i % len(SLOTS)]
        plano.espacios.append(Espacio(codigo, "CUBICULO", 1, Rect(x, fila, ANCHO_CUBICULO, 1), _ubicacion(x, lado)))
    for x, _ in SLOTS[len(codigos) % len(SLOTS) or len(SLOTS):]:
        plano.areas.append(Area(APOYO, "Lockers", Rect(x, 1 + filas_cub, ANCHO_CUBICULO, 1)))

    _servicios(plano, piso, ultima)
    return plano


def _ubicacion(x, lado):
    if x == 0 or x + ANCHO_CUBICULO == COLUMNAS:
        return f"Ala {lado}, sobre la fachada"
    if x + ANCHO_CUBICULO == X_ESPINA or x == X_ESPINA + ESPINA:
        return f"Ala {lado}, junto al pasillo central"
    return f"Ala {lado}, en el centro del ala"


def _servicios(plano, piso, fila):
    """Fills each side of the spine from the spine outwards, so every service opens onto it."""
    lados = [[X_ESPINA, -1], [X_ESPINA + ESPINA, 1]]     # [edge next to the spine, direction]
    libres = [ALA, ALA]
    for i, (tipo, nombre, _) in enumerate(piso.servicios):
        ancho = ANCHO_SERVICIO[tipo]
        lado = 0 if libres[0] >= libres[1] else 1
        if libres[lado] < ancho:
            raise NoEntra("no entran los servicios")
        borde, sentido = lados[lado]
        x = borde - ancho if sentido < 0 else borde
        plano.servicios.append(Servicio(tipo, nombre, Rect(x, fila, ancho, 1)))
        lados[lado][0] = x if sentido < 0 else x + ancho
        libres[lado] -= ancho
    for lado, (borde, sentido) in enumerate(lados):
        if libres[lado]:
            x = 0 if sentido < 0 else borde
            plano.areas.append(Area(APOYO, "Lockers", Rect(x, fila, libres[lado], 1)))
