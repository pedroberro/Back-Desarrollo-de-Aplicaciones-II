"""L-shaped floor around a terrace (a patio on the ground floor). The cubicle wing runs along the
west side with a corridor through it; the corridor turns at the bottom and runs under the terrace,
which opens onto it. Rooms and services take the long bottom wing.

    C C || C C [ TERRAZA ]      rows 0..t-1: wing with spine | terrace
    C C || C C [         ]
    ========================    row t: corridor, under the terrace
    [ SALA-A ][ SALA-B ]        row t+1: rooms
    [cocina][baño]              row t+2: services
"""
from .modelo import APOYO, EXTERIOR, PASILLO, Area, Espacio, Plano, Rect, Servicio, cubiculos, rellenos_para, salas

CODIGO = "EN_L"
ETIQUETA = "En L con terraza"
COLUMNAS = 42
ANCHO_CUBICULO = 6
X_ESPINA = 12
ESPINA = 3
X_TERRAZA = 27
# Nearest to the spine first; the terrace slot (21) is reached through 15.
SLOTS = [(6, "Ala oeste, junto al pasillo"), (15, "Junto al pasillo, del lado {del_exterior}"),
         (21, "Frente {al_exterior}"), (0, "Ala oeste, sobre la fachada")]
ANCHO_SERVICIO = {"COCINA": 14, "SALA_ESTAR": 14, "BANO": 7}


def disponer(piso):
    codigos = cubiculos(piso.cubiculos)
    filas_ala = (len(codigos) + len(SLOTS) - 1) // len(SLOTS)
    fila_pasillo = filas_ala
    fila_salas = fila_pasillo + 1
    filas = fila_salas + (2 if piso.servicios else 1)
    exterior = "Patio" if piso.numero == 0 else "Terraza"
    articulos = {"del_exterior": "del patio", "al_exterior": "al patio"} if exterior == "Patio" \
        else {"del_exterior": "de la terraza", "al_exterior": "a la terraza"}
    plano = Plano(CODIGO, COLUMNAS, filas, list(piso.ventanas), acceso="el pasillo que da " + articulos["al_exterior"])
    relleno = rellenos_para(piso)

    plano.areas += [
        Area(EXTERIOR, exterior, Rect(X_TERRAZA, 0, COLUMNAS - X_TERRAZA, filas_ala)),
        Area(PASILLO, "Pasillo", Rect(X_ESPINA, 0, ESPINA, filas_ala)),
        Area(PASILLO, "Pasillo", Rect(0, fila_pasillo, COLUMNAS, 1)),
    ]
    for i, codigo in enumerate(codigos):
        x, ubicacion = SLOTS[i % len(SLOTS)]
        plano.espacios.append(Espacio(codigo, "CUBICULO", 1, Rect(x, i // len(SLOTS), ANCHO_CUBICULO, 1),
                                      ubicacion.format(**articulos)))
    for x, _ in SLOTS[len(codigos) % len(SLOTS) or len(SLOTS):]:
        plano.areas.append(Area(APOYO, relleno.etiqueta("puestos"), Rect(x, filas_ala - 1, ANCHO_CUBICULO, 1)))

    lista = salas(piso)
    x = 0
    for i, (codigo, tipo, capacidad) in enumerate(lista):
        ancho = COLUMNAS // len(lista) + (1 if i < COLUMNAS % len(lista) else 0)
        plano.espacios.append(Espacio(codigo, tipo, capacidad, Rect(x, fila_salas, ancho, 1), "Ala sur, frente al pasillo"))
        x += ancho

    x = 0
    for tipo, nombre, _ in piso.servicios:
        ancho = ANCHO_SERVICIO[tipo]
        plano.servicios.append(Servicio(tipo, nombre, Rect(x, fila_salas + 1, ancho, 1)))
        x += ancho
    if piso.servicios and x < COLUMNAS:
        plano.areas.append(Area(APOYO, relleno.etiqueta("fondo"), Rect(x, fila_salas + 1, COLUMNAS - x, 1)))
    return plano
