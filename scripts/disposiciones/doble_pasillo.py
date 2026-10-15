"""Two parallel corridors: workstations on both facades, and a closed band in the middle with
the services and the rooms, reached from either corridor.

    C C C C C C C          row 0: north facade
    ==============         row 1: corridor
    [coc][baño][SALA-A]    row 2: middle band, no windows
    ==============         row 3: corridor
    C C C C C C [SALA-B]   row 4: south facade; rooms that do not fit in the middle go to its end
"""
from .modelo import APOYO, PASILLO, Area, Espacio, NoEntra, Plano, Rect, Servicio, cubiculos, muros, rellenos_para, salas

CODIGO = "DOBLE_PASILLO"
ETIQUETA = "Doble pasillo"
COLUMNAS = 42
ANCHO_CUBICULO = 6
ANCHO_SERVICIO = {"COCINA": 9, "SALA_ESTAR": 9, "BANO": 5}
SALA_MINIMA_CENTRO = 8
SALA_MINIMA_FACHADA = 10


def disponer(piso):
    codigos = cubiculos(piso.cubiculos)
    norte = (len(codigos) + 1) // 2
    if norte > 7:
        raise NoEntra("más de 14 cubículos")
    ventanas = muros(piso.ventanas, {"NORTE", "SUR"} if piso.luz else set())
    plano = Plano(CODIGO, COLUMNAS, 5, ventanas, acceso="cualquiera de los dos pasillos")
    relleno = rellenos_para(piso)
    plano.areas += [Area(PASILLO, "Pasillo", Rect(0, 1, COLUMNAS, 1)), Area(PASILLO, "Pasillo", Rect(0, 3, COLUMNAS, 1))]

    x = 0
    for tipo, nombre, _ in piso.servicios:
        ancho = ANCHO_SERVICIO[tipo]
        plano.servicios.append(Servicio(tipo, nombre, Rect(x, 2, ancho, 1)))
        x += ancho

    pendientes = salas(piso)
    resto = COLUMNAS - x
    en_centro = min(len(pendientes), resto // SALA_MINIMA_CENTRO)
    for i in range(en_centro):
        ancho = resto // en_centro + (1 if i < resto % en_centro else 0)
        codigo, tipo, capacidad = pendientes.pop(0)
        plano.espacios.append(Espacio(codigo, tipo, capacidad, Rect(x, 2, ancho, 1), "Banda central, entre los dos pasillos"))
        x += ancho
    if x < COLUMNAS:
        plano.areas.append(Area(APOYO, relleno.etiqueta("fondo"), Rect(x, 2, COLUMNAS - x, 1)))

    for fila, nombre, grupo in ((0, "norte", codigos[:norte]), (4, "sur", codigos[norte:])):
        x = 0
        for codigo in grupo:
            plano.espacios.append(Espacio(codigo, "CUBICULO", 1, Rect(x, fila, ANCHO_CUBICULO, 1), f"Fachada {nombre}, frente al pasillo"))
            x += ANCHO_CUBICULO
        libre = COLUMNAS - x
        if pendientes and libre >= SALA_MINIMA_FACHADA:
            codigo, tipo, capacidad = pendientes.pop(0)
            plano.espacios.append(Espacio(codigo, tipo, capacidad, Rect(x, fila, libre, 1), f"Fachada {nombre}, en el extremo este"))
        elif libre:
            plano.areas.append(Area(APOYO, relleno.etiqueta("puestos"), Rect(x, fila, libre, 1)))
    if pendientes:
        raise NoEntra("no entran todas las salas")
    return plano
