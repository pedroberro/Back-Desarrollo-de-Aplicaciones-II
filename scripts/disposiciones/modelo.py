"""Plan geometry shared by every layout: rectangles on the floor grid, and the checks a plan must pass."""
from dataclasses import dataclass, field

# Kinds of structural area. They are drawn but never booked.
PASILLO = "PASILLO"      # where people walk
NUCLEO = "NUCLEO"        # elevators and stairs
APOYO = "APOYO"          # lockers, printers, building services: fills what spaces leave free
EXTERIOR = "EXTERIOR"    # terrace or patio: open air, its edges count as glazed walls


# What fills the grid cells a layout leaves free, by where they are. Each floor walks the lists
# from its own starting point, so neighbouring floors do not repeat the same thing.
RELLENOS = {
    "puestos": ["Cabina telefónica", "Impresión", "Jardín interior", "Lockers", "Biblioteca"],
    "fondo": ["Office · café", "Depósito", "Jardín interior"],
    "nucleo": ["Instalaciones", "Archivo"],
    "isla": ["Zona de descanso", "Jardín interior", "Biblioteca"],
}


class Rellenos:
    def __init__(self, semilla):
        self.semilla = semilla
        self.usados = {}

    def etiqueta(self, contexto):
        lista = RELLENOS[contexto]
        n = self.usados.get(contexto, 0)
        self.usados[contexto] = n + 1
        return lista[(self.semilla + n) % len(lista)]


def rellenos_para(piso):
    return Rellenos(piso.numero * 3 + len(piso.edificio))


class NoEntra(Exception):
    """The floor's spaces do not fit this layout; the generator tries another one."""


@dataclass(frozen=True)
class Rect:
    x: int
    y: int
    ancho: int
    alto: int

    @property
    def x2(self):
        return self.x + self.ancho

    @property
    def y2(self):
        return self.y + self.alto

    def se_superpone(self, otro):
        return self.x < otro.x2 and otro.x < self.x2 and self.y < otro.y2 and otro.y < self.y2

    def toca(self, otro):
        """Shares a stretch of wall (not just a corner) with the other rectangle."""
        en_y = min(self.y2, otro.y2) - max(self.y, otro.y) > 0
        en_x = min(self.x2, otro.x2) - max(self.x, otro.x) > 0
        return ((self.x2 == otro.x or otro.x2 == self.x) and en_y) or ((self.y2 == otro.y or otro.y2 == self.y) and en_x)


@dataclass
class PisoDatos:
    """What a floor holds, independent of how it is laid out."""
    edificio: str
    numero: int
    nombre: str
    cubiculos: int
    salas: list          # [(tipo, capacidad)], in code order SALA-A, SALA-B...
    servicios: list      # [(tipo, nombre, ancho en la plantilla original)]
    amenities: str       # letters of the original catalog: K, S, B, L
    ventanas: list       # walls with windows in the original catalog

    @property
    def luz(self):
        return "L" in self.amenities


@dataclass
class Espacio:
    codigo: str
    tipo: str
    capacidad: int
    rect: Rect
    ubicacion: str
    # Filled by the layout only when it must keep texts verbatim (the wireframe floors);
    # otherwise textos.py derives them from the geometry.
    ventana: bool = None
    orientacion: str = None
    descripcion: str = None
    equipamiento: list = None


@dataclass
class Servicio:
    tipo: str
    nombre: str
    rect: Rect


@dataclass
class Area:
    tipo: str
    etiqueta: str
    rect: Rect


@dataclass
class Plano:
    disposicion: str
    columnas: int
    filas: int
    ventanas: list                      # walls with windows: NORTE, SUR, ESTE, OESTE
    espacios: list = field(default_factory=list)
    servicios: list = field(default_factory=list)
    areas: list = field(default_factory=list)
    acceso: str = "el pasillo"          # how rooms are reached, for the descriptions

    def de_tipo(self, tipo):
        return [a for a in self.areas if a.tipo == tipo]


def muros(*conjuntos):
    """Union of walls with windows, in the usual order."""
    todos = set().union(*conjuntos)
    return [m for m in ("NORTE", "SUR", "ESTE", "OESTE") if m in todos]


def unir_apoyos(plano):
    """Joins support areas that sit side by side in the same row into one, keeping the first label:
    two free slots read as one printing corner, not as two boxes."""
    cambio = True
    while cambio:
        cambio = False
        apoyos = sorted((a for a in plano.areas if a.tipo == APOYO), key=lambda a: (a.rect.y, a.rect.x))
        for a, b in zip(apoyos, apoyos[1:]):
            if a.rect.y == b.rect.y and a.rect.alto == b.rect.alto and a.rect.x2 == b.rect.x:
                plano.areas.remove(b)
                a.rect = Rect(a.rect.x, a.rect.y, a.rect.ancho + b.rect.ancho, a.rect.alto)
                cambio = True
                break


def cubiculos(cantidad):
    return [f"C-{i + 1:02d}" for i in range(cantidad)]


def salas(piso):
    return [(f"SALA-{'ABC'[i]}", tipo, capacidad) for i, (tipo, capacidad) in enumerate(piso.salas)]


LADOS = ("Norte", "Oeste", "Este", "Sur")


def lados_con_ventana(rect, plano):
    """Sides of a space with a window: a glazed outer wall, or an edge on a terrace or patio.
    Returns [(lado, exterior)] in the order Norte, Oeste, Este, Sur, where exterior is the label
    of the terrace or patio the window opens onto, or None for an outer wall."""
    lados = {}
    if rect.y == 0 and "NORTE" in plano.ventanas:
        lados["Norte"] = None
    if rect.x == 0 and "OESTE" in plano.ventanas:
        lados["Oeste"] = None
    if rect.x2 == plano.columnas and "ESTE" in plano.ventanas:
        lados["Este"] = None
    if rect.y2 == plano.filas and "SUR" in plano.ventanas:
        lados["Sur"] = None
    for area in plano.de_tipo(EXTERIOR):
        if not rect.toca(area.rect):
            continue
        lado = ("Este" if rect.x2 == area.rect.x else "Oeste" if rect.x == area.rect.x2
                else "Sur" if rect.y2 == area.rect.y else "Norte")
        lados[lado] = area.etiqueta
    return [(lado, lados[lado]) for lado in LADOS if lado in lados]


def validar(plano, piso):
    """Raises AssertionError with the first problem found. A plan that passes can be drawn as is."""
    donde = f"{piso.edificio} · {piso.nombre} ({plano.disposicion})"
    todos = ([("espacio " + e.codigo, e.rect) for e in plano.espacios]
             + [("servicio " + s.nombre, s.rect) for s in plano.servicios]
             + [(f"{a.tipo.lower()} {a.etiqueta}", a.rect) for a in plano.areas])

    for nombre, r in todos:
        assert r.ancho > 0 and r.alto > 0, f"{donde}: {nombre} sin tamaño"
        assert r.x >= 0 and r.y >= 0 and r.x2 <= plano.columnas and r.y2 <= plano.filas, \
            f"{donde}: {nombre} fuera de la grilla {plano.columnas}x{plano.filas}: {r}"
    for i, (a, ra) in enumerate(todos):
        for b, rb in todos[i + 1:]:
            assert not ra.se_superpone(rb), f"{donde}: {a} se superpone con {b}"

    codigos = [e.codigo for e in plano.espacios]
    esperados = cubiculos(piso.cubiculos) + [c for c, _, _ in salas(piso)]
    assert sorted(codigos) == sorted(esperados), f"{donde}: espacios {sorted(codigos)} en lugar de {sorted(esperados)}"
    assert sorted(s.nombre for s in plano.servicios) == sorted(n for _, n, _ in piso.servicios), \
        f"{donde}: faltan o sobran servicios"
    assert plano.de_tipo(PASILLO), f"{donde}: no tiene pasillo"

    # Every bookable space must be reachable walking: through corridors and through the open
    # cubicle area (the panels are 1.60 m), never through a closed room.
    transitables = [a.rect for a in plano.de_tipo(PASILLO)]
    abiertos = [e.rect for e in plano.espacios if e.tipo == "CUBICULO"]
    alcanzados = list(transitables)
    pendientes = list(abiertos)
    cambio = True
    while cambio:
        cambio = False
        for r in list(pendientes):
            if any(r.toca(a) for a in alcanzados):
                alcanzados.append(r)
                pendientes.remove(r)
                cambio = True
    for e in plano.espacios:
        assert e.rect in alcanzados or any(e.rect.toca(a) for a in alcanzados), \
            f"{donde}: no se llega caminando a {e.codigo}"
    for a in plano.de_tipo(EXTERIOR):
        assert any(a.rect.toca(p) for p in transitables), f"{donde}: no se llega a {a.etiqueta}"
