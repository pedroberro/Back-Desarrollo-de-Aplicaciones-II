"""The wireframe floor: rows of seven cubicles, one corridor across, a row of rooms and a row of
services. Reuses the catalog generator so these floors stay exactly as V12 seeded them."""
import generar_catalogo as catalogo

from .modelo import PASILLO, Area, Espacio, Plano, Rect, Servicio, cubiculos, salas

CODIGO = "PASILLO_CENTRAL"
ETIQUETA = "Pasillo central"
COLUMNAS = catalogo.COLS


def disponer(piso):
    filas_cub = (piso.cubiculos + 6) // 7
    fila_pasillo = filas_cub
    fila_salas = fila_pasillo + 1
    ventanas = ",".join(piso.ventanas)

    espacios = []
    for i, codigo in enumerate(cubiculos(piso.cubiculos)):
        d = catalogo.cubiculo(codigo, i // 7, i % 7, filas_cub, ventanas, piso.nombre, piso.edificio)
        espacios.append(_espacio(d))
    lista = salas(piso)
    for i, (codigo, tipo, capacidad) in enumerate(lista):
        d = catalogo.sala(codigo, tipo, capacidad, i, len(lista), fila_salas, ventanas, piso.nombre, piso.edificio)
        espacios.append(_espacio(d))

    servicios, x = [], 0
    for tipo, nombre, ancho in piso.servicios:
        servicios.append(Servicio(tipo, nombre, Rect(x, fila_salas + 1, ancho, 1)))
        x += ancho

    return Plano(CODIGO, COLUMNAS, fila_salas + (2 if servicios else 1), list(piso.ventanas), espacios, servicios,
                 [Area(PASILLO, "Pasillo", Rect(0, fila_pasillo, COLUMNAS, 1))], acceso="el pasillo central")


def _espacio(d):
    return Espacio(d["codigo"], d["tipo"], d["capacidad"], Rect(d["x"], d["y"], d["w"], d["h"]), d["ubicacion"],
                   ventana=d["ventana"], orientacion=d["orientacion"], descripcion=d["descripcion"],
                   equipamiento=d["equipamiento"])
