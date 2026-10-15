"""Texts of a space that depend on where it ended up: window, orientation, description, equipment.
Same wording as the catalog generator, so old and re-laid-out floors read alike."""
import generar_catalogo as catalogo

from .modelo import lados_con_ventana

LUZ = {
    "Norte": "Recibe luz natural directa durante toda la mañana",
    "Oeste": "Recibe el sol de la tarde por el muro oeste",
    "Este": "Recibe el sol de la mañana por el muro este",
    "Sur": "Recibe luz natural pareja, sin sol directo, por la fachada sur",
    "Interior": "No tiene ventana, pero la iluminación general es pareja y no hay reflejos en la pantalla",
}


def _hacia(exterior):
    """'a la terraza', 'al patio'."""
    nombre = exterior.lower()
    return f"a la {nombre}" if nombre.endswith("a") else f"al {nombre}"


def completar(espacio, plano, piso):
    """Fills ventana, orientacion, descripcion and equipamiento unless the layout fixed them."""
    if espacio.descripcion is not None:
        return
    lados = lados_con_ventana(espacio.rect, plano)
    espacio.ventana = bool(lados)
    espacio.orientacion = lados[0][0] if lados else "Interior"
    exterior = lados[0][1] if lados else None
    ubicacion = espacio.ubicacion[0].lower() + espacio.ubicacion[1:]
    piso_nombre = "la planta baja" if piso.numero == 0 else "el " + piso.nombre.lower()

    if espacio.tipo == "CUBICULO":
        luz = f"Tiene ventanal {_hacia(exterior)}, con luz natural todo el día" if exterior else LUZ[espacio.orientacion]
        espacio.descripcion = (
            f"Cubículo individual en {piso_nombre} de {piso.edificio}, {ubicacion}. {luz}. "
            "El panel divisorio llega a 1,60 m: aísla del pasillo estando sentado, pero no es un espacio cerrado, "
            "así que para llamadas largas conviene reservar una sala."
        )
        equipamiento = list(catalogo.EQUIPAMIENTO["CUBICULO"])
        equipamiento.insert(2, "Luz natural · más lámpara de escritorio" if espacio.ventana
                            else "Lámpara · de escritorio regulable")
        espacio.equipamiento = equipamiento
        return

    if exterior:
        ventana = f"Da {_hacia(exterior)} por un ventanal con cortina blackout, útil para proyectar de día. "
    elif espacio.ventana:
        ventana = f"Tiene ventana al {espacio.orientacion.lower()} con cortina blackout, útil para proyectar de día. "
    else:
        ventana = "Es un ambiente interior, sin ventana, con muros acústicos. "
    espacio.descripcion = (
        f"{catalogo.NOMBRE_TIPO[espacio.tipo].capitalize()} para {espacio.capacidad} personas en {piso_nombre} "
        f"de {piso.edificio}. {ventana}Se accede desde {plano.acceso}."
    )
    espacio.equipamiento = list(catalogo.EQUIPAMIENTO[espacio.tipo])
