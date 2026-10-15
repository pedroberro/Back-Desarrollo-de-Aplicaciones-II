"""Floor plan layouts. Each module lays out the same floor contents (cubicles, rooms, services)
in its own way and returns a Plano.

To add a layout: write a module with CODIGO, ETIQUETA and disponer(piso) -> Plano (raise NoEntra
when the floor does not fit), and register it here. The API and the front draw any valid plan
without knowing the layout; Disposicion.java only needs the new code and its label.
"""
from . import doble_pasillo, en_l, islas, nucleo_central, pasillo_central, peine

REGISTRO = {m.CODIGO: m for m in (pasillo_central, nucleo_central, peine, en_l, islas, doble_pasillo)}
