"""Generates V12__seed_catalogo.sql: the demo catalog (companies, buildings, floors, spaces, services).

Run from the back folder: python scripts/generar_catalogo.py

Floors are laid out on a 42-column grid like the wireframe: rows of 7 cubicles (6 columns each),
the corridor, a row of rooms and a row of floor services.
"""
import json
from pathlib import Path

COLS = 42

EMPRESAS = [
    # nombre, rubro, tono, edificios
    ("Nexo Corporativo", "Servicios financieros", "indigo", [
        ("Torre Callao", "Av. Callao 1234", "CABA", "08:00", "20:00", "indigo", "torre"),
        ("Nodo Palermo", "Gorriti 4502", "CABA", "08:00", "22:00", "sky", [(4, 3, "CKB"), (2, 2, "KBL"), (1, 2, "B")]),
        ("Distrito Norte", "Av. del Libertador 6320", "Vicente López", "07:00", "19:00", "indigo", [(3, 3, "KBL"), (2, 3, "KSBL"), (1, 3, "B")]),
        ("Casa Barracas", "Olavarría 890", "CABA", "09:00", "18:00", "slate", [(2, 2, "KB"), (1, 2, "BS")]),
        ("Polo Tecnológico", "Pedro Chutro 1150", "CABA", "08:00", "20:00", "sky", [(3, 3, "KSBL"), (2, 3, "KBB"), (1, 3, "BL")]),
        ("Estación Retiro", "Av. Ramos Mejía 1430", "CABA", "06:00", "23:00", "indigo", [(2, 2, "KB"), (1, 2, "SB")]),
    ]),
    ("Almagro Estudio", "Arquitectura y diseño", "sky", [
        ("Estudio Almagro", "Bulnes 820", "CABA", "08:00", "20:00", "sky", [(2, 2, "KSBL"), (1, 3, "BB"), (0, 2, "KB")]),
    ]),
    ("Cooperativa Sur", "Desarrollo de software", "slate", [
        ("Sede Constitución", "Lima 1540", "CABA", "09:00", "19:00", "slate", [(2, 2, "KB"), (1, 3, "BL")]),
        ("Sede Avellaneda", "Av. Mitre 750", "Avellaneda", "08:00", "18:00", "slate", [(1, 2, "KBB"), (0, 3, "SB")]),
    ]),
    ("Grupo Ribera", "Logística", "indigo", [
        ("Ribera Puerto Madero", "Juana Manso 1200", "CABA", "08:00", "20:00", "indigo", [(3, 3, "KSBL"), (2, 3, "KB"), (1, 2, "BL")]),
        ("Ribera Tigre", "Paseo Victorica 410", "Tigre", "09:00", "18:00", "sky", [(1, 2, "KSBL"), (0, 2, "B")]),
        ("Ribera Barracas", "Av. Montes de Oca 1800", "CABA", "08:00", "19:00", "indigo", [(2, 2, "KB"), (1, 2, "SBL")]),
    ]),
    ("Fundación Lumen", "Educación", "sky", [
        ("Casa Lumen", "Av. Centenario 245", "San Isidro", "08:00", "21:00", "sky", [(2, 3, "KSBL"), (1, 2, "BB"), (0, 3, "KB")]),
    ]),
    ("Delta Salud", "Salud ocupacional", "indigo", [
        ("Delta Centro", "Av. Corrientes 2050", "CABA", "07:00", "20:00", "indigo", [(3, 3, "KBL"), (2, 2, "SBB"), (1, 3, "KB")]),
        ("Delta Quilmes", "Rivadavia 330", "Quilmes", "08:00", "18:00", "sky", [(1, 2, "KSB"), (0, 2, "BL")]),
    ]),
]

# Torre Callao reproduces the wireframe floor by floor: number, cubicles, rooms, amenities.
# Amenity letters: K cocina, S sala de estar, B one bathroom each, L luz natural.
TORRE_CALLAO = [(4, 11, 2, "BL"), (3, 14, 3, "KSBBL"), (2, 12, 3, "KB"), (1, 10, 2, "BS"), (0, 6, 3, "KB")]

SALAS = {
    1: [("SALA_REUNIONES", 6)],
    2: [("SALA_REUNIONES", 6), ("SALA_CONFERENCIAS", 20)],
    3: [("SALA_REUNIONES", 6), ("SALA_PRESENTACIONES", 16), ("SALA_CONFERENCIAS", 24)],
}

EQUIPAMIENTO = {
    "CUBICULO": ['Monitor 24" · HDMI y USB-C', "Wi-Fi 6 · 300 Mbps", "Silla ergonómica regulable",
                 "Cajonera con llave", "Tomas · 2 de 220V y 2 USB-A"],
    "SALA_REUNIONES": ['Pantalla 55" · HDMI y USB-C', "Wi-Fi 6 · 300 Mbps", "Pizarra · blanca con marcadores",
                       "Cámara · videollamadas con micrófono"],
    "SALA_PRESENTACIONES": ["Proyector · 4K con telón", "Wi-Fi 6 · 300 Mbps", "Sonido · parlantes de techo",
                            "Micrófono · inalámbrico de mano"],
    "SALA_CONFERENCIAS": ['Pantalla doble 75" · para videoconferencia', "Wi-Fi 6 · 300 Mbps",
                          "Micrófonos · de mesa, uno cada dos lugares", "Mesa en U · para todo el grupo"],
}

NOMBRE_TIPO = {
    "SALA_REUNIONES": "sala de reuniones",
    "SALA_PRESENTACIONES": "sala de presentaciones",
    "SALA_CONFERENCIAS": "sala de conferencias",
}


def q(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def nombre_piso(numero):
    return "Planta baja" if numero == 0 else f"Piso {numero}"


def ventanas_de(numero, amenities):
    # Floors with natural light face north and west; the rest only get the east wall.
    return "NORTE,OESTE" if "L" in amenities else ("ESTE" if numero % 2 else "")


def servicios_de(amenities):
    servicios = []
    if "K" in amenities:
        servicios.append(("COCINA", "Cocina", 14))
    if "S" in amenities:
        servicios.append(("SALA_ESTAR", "Sala de estar", 14))
    banos = amenities.count("B")
    for i in range(banos):
        servicios.append(("BANO", f"Baño {'AB'[i]}" if banos > 1 else "Baño", 7))
    return servicios


def cubiculo(codigo, fila, col, filas_cub, ventanas, piso_nombre, edificio):
    ventanas = ventanas.split(",")
    x = col * 6
    norte = "NORTE" in ventanas and fila == 0 and col < 4
    oeste = "OESTE" in ventanas and col == 0
    este = "ESTE" in ventanas and col == 6
    ventana = norte or oeste or este
    orientacion = "Norte" if norte else "Oeste" if oeste else "Este" if este else "Interior"
    junto = "junto al pasillo" if fila == filas_cub - 1 else "sobre la fachada" if fila == 0 else "en el centro del piso"
    luz = {
        "Norte": "Recibe luz natural directa durante toda la mañana",
        "Oeste": "Recibe el sol de la tarde por el muro oeste",
        "Este": "Recibe el sol de la mañana por el muro este",
        "Interior": "No tiene ventana, pero la iluminación general es pareja y no hay reflejos en la pantalla",
    }[orientacion]
    descripcion = (
        f"Cubículo individual en la fila {fila + 1} del {piso_nombre.lower()} de {edificio}, {junto}. {luz}. "
        "El panel divisorio llega a 1,60 m: aísla del pasillo estando sentado, pero no es un espacio cerrado, "
        "así que para llamadas largas conviene reservar una sala."
    )
    equip = list(EQUIPAMIENTO["CUBICULO"])
    equip.insert(2, "Luz natural · más lámpara de escritorio" if ventana else "Lámpara · de escritorio regulable")
    return dict(codigo=codigo, tipo="CUBICULO", capacidad=1, ventana=ventana, descripcion=descripcion,
                superficie=4.2, orientacion=orientacion, ubicacion=f"Fila {fila + 1}, {junto}",
                ruido="Bajo — zona de trabajo silencioso", climatizacion="Central, con difusor propio",
                equipamiento=equip, x=x, y=fila, w=6, h=1)


def sala(codigo, tipo, capacidad, indice, cantidad, fila, ventanas, piso_nombre, edificio):
    ventanas = ventanas.split(",")
    ancho = COLS // cantidad
    x = indice * ancho
    oeste = "OESTE" in ventanas and indice == 0
    este = "ESTE" in ventanas and indice == cantidad - 1
    ventana = oeste or este
    orientacion = "Oeste" if oeste else "Este" if este else "Interior"
    superficie = {"SALA_REUNIONES": 18.0, "SALA_PRESENTACIONES": 36.0, "SALA_CONFERENCIAS": 52.0}[tipo]
    descripcion = (
        f"{NOMBRE_TIPO[tipo].capitalize()} para {capacidad} personas en el {piso_nombre.lower()} de {edificio}. "
        + ("Tiene ventana al " + orientacion.lower() + " con cortina blackout, útil para proyectar de día. "
           if ventana else "Es un ambiente interior, sin ventana, con muros acústicos. ")
        + "Se accede desde el pasillo central y queda cerca de los servicios del piso."
    )
    return dict(codigo=codigo, tipo=tipo, capacidad=capacidad, ventana=ventana, descripcion=descripcion,
                superficie=superficie, orientacion=orientacion, ubicacion="Frente al pasillo central",
                ruido="Medio — sala cerrada con aislación", climatizacion="Split frío-calor individual",
                equipamiento=EQUIPAMIENTO[tipo], x=x, y=fila, w=ancho, h=1)


def piso_sql(edificio_ref, edificio_nombre, numero, cubiculos, salas, amenities, out):
    ventanas = ventanas_de(numero, amenities)
    nombre = nombre_piso(numero)
    filas_cub = (cubiculos + 6) // 7
    fila_pasillo = filas_cub
    out.append(
        "INSERT INTO pisos (edificio_id, numero, nombre, luz_natural, ventanas, plano_columnas, fila_pasillo) "
        f"VALUES ({edificio_ref}, {numero}, {q(nombre)}, {q('L' in amenities)}, {q(ventanas)}, {COLS}, {fila_pasillo});"
    )
    piso_ref = f"(SELECT id FROM pisos WHERE edificio_id = {edificio_ref} AND numero = {numero})"
    espacios = [cubiculo(f"C-{i + 1:02d}", i // 7, i % 7, filas_cub, ventanas, nombre, edificio_nombre)
                for i in range(cubiculos)]
    fila_salas = fila_pasillo + 1
    for i, (tipo, capacidad) in enumerate(SALAS[salas]):
        espacios.append(sala(f"SALA-{'ABC'[i]}", tipo, capacidad, i, salas, fila_salas, ventanas, nombre, edificio_nombre))
    for e in espacios:
        out.append(
            "INSERT INTO espacios (piso_id, codigo, tipo, capacidad, ventana, descripcion, superficie, orientacion, "
            "ubicacion, ruido, climatizacion, equipamiento, plano_x, plano_y, plano_ancho, plano_alto) VALUES ("
            f"{piso_ref}, {q(e['codigo'])}, {q(e['tipo'])}, {e['capacidad']}, {q(e['ventana'])}, {q(e['descripcion'])}, "
            f"{e['superficie']}, {q(e['orientacion'])}, {q(e['ubicacion'])}, {q(e['ruido'])}, {q(e['climatizacion'])}, "
            f"{q(json.dumps(e['equipamiento'], ensure_ascii=False))}::jsonb, {e['x']}, {e['y']}, {e['w']}, {e['h']});"
        )
    x = 0
    for tipo, nombre_servicio, ancho in servicios_de(amenities):
        out.append(
            "INSERT INTO servicios_piso (piso_id, tipo, nombre, plano_x, plano_y, plano_ancho, plano_alto) VALUES ("
            f"{piso_ref}, {q(tipo)}, {q(nombre_servicio)}, {x}, {fila_salas + 1}, {ancho}, 1);"
        )
        x += ancho


def main():
    out = [
        "-- Demo catalog generated from the wireframes (see docs). Torre Callao matches them floor by floor.",
        "-- Bookings and blocks are not seeded here: DatosDemoInicializador creates them relative to today.",
        "",
    ]
    for nombre, rubro, tono, edificios in EMPRESAS:
        out.append(f"INSERT INTO empresas (nombre, rubro, tono) VALUES ({q(nombre)}, {q(rubro)}, {q(tono)});")
        empresa_ref = f"(SELECT id FROM empresas WHERE nombre = {q(nombre)})"
        for ed_nombre, direccion, localidad, abre, cierra, ed_tono, pisos in edificios:
            out.append(
                "INSERT INTO edificios (empresa_id, nombre, direccion, localidad, hora_apertura, hora_cierre, tono) "
                f"VALUES ({empresa_ref}, {q(ed_nombre)}, {q(direccion)}, {q(localidad)}, {q(abre)}, {q(cierra)}, {q(ed_tono)});"
            )
            edificio_ref = f"(SELECT id FROM edificios WHERE nombre = {q(ed_nombre)})"
            if pisos == "torre":
                for numero, cub, salas, amen in TORRE_CALLAO:
                    piso_sql(edificio_ref, ed_nombre, numero, cub, salas, amen, out)
            else:
                for numero, salas, amen in pisos:
                    cub = 7 + (numero * 5 + len(ed_nombre)) % 8
                    piso_sql(edificio_ref, ed_nombre, numero, cub, salas, amen, out)
        out.append("")
    path = Path(__file__).resolve().parent.parent / "src/main/resources/db/migration/V12__seed_catalogo.sql"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    print(len(out), "lines")


main()
