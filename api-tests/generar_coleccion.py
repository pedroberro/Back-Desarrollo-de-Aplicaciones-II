#!/usr/bin/env python3
"""Generates the Postman collection of the API: every endpoint with a success case and an error case.

    python3 api-tests/generar_coleccion.py

writes api-tests/PlantaLibre.postman_collection.json, which is what Postman imports and Newman runs.
The requests run in order and pass ids and tokens to each other through collection variables;
api-tests/preparar-datos.sh creates the environment they start from.
"""
import json
from pathlib import Path

DESTINO = Path(__file__).with_name("PlantaLibre.postman_collection.json")

# Shared by every request: the dates the tests use, computed when the request runs.
PRE_COLECCION = r"""
function iso(d) { return d.toISOString().slice(0, 10); }
const hoy = new Date();
let habil = new Date(hoy.getTime() + 2 * 86400000);
while (habil.getUTCDay() === 0 || habil.getUTCDay() === 6) { habil = new Date(habil.getTime() + 86400000); }
pm.collectionVariables.set('proximoHabil', iso(habil));
pm.collectionVariables.set('diaLejano', iso(new Date(hoy.getTime() + 250 * 86400000)));
"""

# TOTP (RFC 6238, SHA-1, 6 digits, 30 s) from the base32 secret the API hands out on enrolment.
TOTP = r"""
function base32AHex(texto) {
    const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    let bits = '';
    for (const c of texto.replace(/=+$/, '').toUpperCase()) {
        const v = alfabeto.indexOf(c);
        if (v >= 0) { bits += v.toString(2).padStart(5, '0'); }
    }
    let hex = '';
    for (let i = 0; i + 8 <= bits.length; i += 8) { hex += parseInt(bits.substr(i, 8), 2).toString(16).padStart(2, '0'); }
    return hex;
}
function totp(secreto) {
    const CryptoJS = require('crypto-js');
    const paso = Math.floor(Date.now() / 1000 / 30).toString(16).padStart(16, '0');
    const h = CryptoJS.HmacSHA1(CryptoJS.enc.Hex.parse(paso), CryptoJS.enc.Hex.parse(base32AHex(secreto))).toString(CryptoJS.enc.Hex);
    const desde = parseInt(h.substr(h.length - 1), 16);
    const binario = (parseInt(h.substr(desde * 2, 8), 16) & 0x7fffffff) % 1000000;
    return binario.toString().padStart(6, '0');
}
pm.collectionVariables.set('totp', totp(pm.collectionVariables.get('adminSecreto')));
"""

REFRESH = "const c = (pm.response.headers.get('Set-Cookie') || '').match(/pl_refresh=([^;]+)/); if (c) {{ pm.collectionVariables.set('{var}', c[1]); }}"

# Adds minutes to a local "yyyy-MM-ddTHH:mm:ss" without involving any time zone.
SUMAR = r"""
function sumar(local, minutos) { return new Date(new Date(local + 'Z').getTime() + minutos * 60000).toISOString().slice(0, 19); }
"""


def guardar(var, expresion):
    return f"pm.collectionVariables.set('{var}', {expresion});"


def pedido(nombre, metodo, ruta, estado, token=None, cuerpo=None, query=None, pruebas="", pre="", headers=None, mensaje=True):
    script = [f"pm.test('responde {estado}', () => pm.response.to.have.status({estado}));"]
    if estado >= 400 and mensaje:
        script.append("pm.test('el error trae mensaje', () => pm.expect(pm.response.json().message).to.be.a('string'));")
    if pruebas:
        script.append(pruebas)
    url = "{{baseUrl}}" + ruta
    encabezados = [{"key": "Authorization", "value": f"Bearer {{{{{token}}}}}"}] if token else []
    encabezados += [{"key": k, "value": v} for k, v in (headers or {}).items()]
    item = {
        "name": nombre,
        "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": "\n".join(script).split("\n")}}],
        "request": {"method": metodo, "header": encabezados, "url": {"raw": url + ("?" + "&".join(f"{k}={v}" for k, v in query.items()) if query else ""),
                                                                     "host": ["{{baseUrl}}"], "path": ruta.strip("/").split("/"),
                                                                     "query": [{"key": k, "value": v} for k, v in (query or {}).items()]}},
    }
    if pre:
        item["event"].append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre.strip().split("\n")}})
    if cuerpo is not None:
        item["request"]["header"].append({"key": "Content-Type", "value": "application/json"})
        item["request"]["body"] = {"mode": "raw", "raw": json.dumps(cuerpo, ensure_ascii=False, indent=2)}
    return item


def carpeta(nombre, descripcion, items):
    return {"name": nombre, "description": descripcion, "item": items}


NO_EXISTE = "999999"

salud = carpeta("01 Salud", "El servicio responde.", [
    pedido("Estado del servicio", "GET", "/api/health", 200,
           pruebas="pm.test('está ok', () => pm.expect(pm.response.json().status).to.eql('ok'));"),
    pedido("Método no permitido", "POST", "/api/health", 405, mensaje=False),
])

autenticacion = carpeta("02 Autenticación", "Registro, confirmación, inicio de sesión, renovación y segundo factor.", [
    pedido("Login de cliente", "POST", "/api/auth/login", 200, cuerpo={"email": "{{demoEmail}}", "password": "{{demoPassword}}"},
           pruebas=guardar("demoToken", "pm.response.json().token") + "\n" + REFRESH.format(var="demoRefresh")
           + "\npm.test('entrega el token', () => pm.expect(pm.response.json().token).to.be.a('string'));"),
    pedido("Login con clave incorrecta", "POST", "/api/auth/login", 401,
           cuerpo={"email": "{{demoEmail}}", "password": "Incorrecta-123"}),
    pedido("Login sin email", "POST", "/api/auth/login", 400, cuerpo={"password": "Incorrecta-123"},
           pruebas="pm.test('señala el campo', () => pm.expect(pm.response.json().errors).to.have.property('email'));"),
    pedido("Registro", "POST", "/api/auth/register", 201,
           pre="pm.collectionVariables.set('registroEmail', 'postman-' + pm.variables.replaceIn('{{$guid}}') + '@pruebas.plantalibre.local');",
           cuerpo={"nombre": "Registro Postman", "email": "{{registroEmail}}", "telefono": "+54 11 5555 0000",
                   "password": "Registro-2026-Postman", "rol": "CLIENTE"},
           pruebas="pm.test('queda pendiente de confirmar', () => pm.expect(pm.response.json().estado).to.eql('PENDIENTE'));"),
    pedido("Registro con email repetido", "POST", "/api/auth/register", 409,
           cuerpo={"nombre": "Repetido", "email": "{{nuevoEmail}}", "telefono": "+54 11 5555 0000",
                   "password": "Registro-2026-Postman", "rol": "CLIENTE"}),
    pedido("Email disponible", "POST", "/api/auth/email-disponible", 200, cuerpo={"email": "{{nuevoEmail}}"},
           pruebas="pm.test('ya está tomado', () => pm.expect(pm.response.json().disponible).to.eql(false));"),
    pedido("Email disponible con email inválido", "POST", "/api/auth/email-disponible", 400, cuerpo={"email": "no-es-un-email"}),
    pedido("Reenviar confirmación", "POST", "/api/auth/reenviar-confirmacion", 200, cuerpo={"email": "{{registroEmail}}"}),
    pedido("Reenviar confirmación sin email", "POST", "/api/auth/reenviar-confirmacion", 400, cuerpo={}),
    pedido("Confirmar email", "POST", "/api/auth/confirmar-email", 200, cuerpo={"token": "{{confirmToken}}"},
           pruebas=guardar("nuevoToken", "pm.response.json().token") + "\n" + guardar("nuevoId", "pm.response.json().usuario.id")
           + "\npm.test('queda activo', () => pm.expect(pm.response.json().usuario.estado).to.eql('ACTIVO'));"),
    pedido("Confirmar email con link inválido", "POST", "/api/auth/confirmar-email", 400, cuerpo={"token": "no-existe"},
           pruebas="pm.test('código LINK_INVALIDO', () => pm.expect(pm.response.json().code).to.eql('LINK_INVALIDO'));"),
    pedido("Olvidé mi clave", "POST", "/api/auth/forgot-password", 200, cuerpo={"email": "{{registroEmail}}"}),
    pedido("Olvidé mi clave sin email", "POST", "/api/auth/forgot-password", 400, cuerpo={}),
    pedido("Restablecer la clave", "POST", "/api/auth/reset-password", 200,
           cuerpo={"token": "{{resetToken}}", "nuevaPassword": "Restablecida-2026-Postman"}),
    pedido("Restablecer con link inválido", "POST", "/api/auth/reset-password", 400,
           cuerpo={"token": "no-existe", "nuevaPassword": "Restablecida-2026-Postman"}),
    pedido("Renovar la sesión", "POST", "/api/auth/refresh", 200, headers={"Cookie": "pl_refresh={{demoRefresh}}"},
           pruebas=guardar("demoToken", "pm.response.json().token") + "\n" + REFRESH.format(var="demoRefresh")),
    pedido("Renovar sin sesión", "POST", "/api/auth/refresh", 401, mensaje=False,
           pruebas="pm.test('sin cuerpo', () => pm.expect(pm.response.text()).to.eql(''));"),
    pedido("Login de administrador", "POST", "/api/auth/login", 200, cuerpo={"email": "{{adminEmail}}", "password": "{{adminPassword}}"},
           pruebas="pm.test('pide segundo factor', () => pm.expect(pm.response.json().segundoFactorRequerido).to.eql(true));\n"
           + guardar("desafio", "pm.response.json().desafio") + "\n" + guardar("adminSecreto", "pm.response.json().altaSegundoFactor.secreto")),
    pedido("Segundo factor con código incorrecto", "POST", "/api/auth/login/segundo-factor", 401,
           pre=TOTP + "\npm.collectionVariables.set('totpIncorrecto', pm.collectionVariables.get('totp') === '000000' ? '111111' : '000000');",
           cuerpo={"desafio": "{{desafio}}", "codigo": "{{totpIncorrecto}}"},
           pruebas="pm.test('código CODIGO_INCORRECTO', () => pm.expect(pm.response.json().code).to.eql('CODIGO_INCORRECTO'));"),
    pedido("Segundo factor", "POST", "/api/auth/login/segundo-factor", 200, pre=TOTP,
           cuerpo={"desafio": "{{desafio}}", "codigo": "{{totp}}"},
           pruebas=guardar("adminToken", "pm.response.json().token")),
])

catalogo = carpeta("03 Catálogo", "Empresas, edificios, planos, espacios y su disponibilidad.", [
    pedido("Empresas", "GET", "/api/empresas", 200, token="demoToken",
           pruebas=guardar("empresaId", "pm.response.json()[0].id") + "\npm.test('hay empresas', () => pm.expect(pm.response.json()).to.not.be.empty);"),
    pedido("Empresas sin sesión", "GET", "/api/empresas", 401),
    pedido("Empresa", "GET", "/api/empresas/{{empresaId}}", 200, token="demoToken",
           pruebas="pm.test('es la pedida', () => pm.expect(pm.response.json().empresa.id).to.eql(Number(pm.collectionVariables.get('empresaId'))));"),
    pedido("Empresa inexistente", "GET", f"/api/empresas/{NO_EXISTE}", 404, token="demoToken"),
    pedido("Edificios", "GET", "/api/edificios", 200, token="demoToken",
           pruebas=guardar("edificioId", "pm.response.json()[0].id") + "\n" + guardar("pisoId", "pm.response.json()[0].pisos[0].id")),
    pedido("Edificios con token inválido", "GET", "/api/edificios", 401, headers={"Authorization": "Bearer no.es.un.token"}),
    pedido("Edificio", "GET", "/api/edificios/{{edificioId}}", 200, token="demoToken",
           pruebas="pm.test('trae sus pisos', () => pm.expect(pm.response.json().pisos).to.not.be.empty);"),
    pedido("Edificio inexistente", "GET", f"/api/edificios/{NO_EXISTE}", 404, token="demoToken"),
    pedido("Plano del piso", "GET", "/api/pisos/{{pisoId}}/plano", 200, token="demoToken",
           query={"fecha": "{{proximoHabil}}", "desde": "10:00", "hasta": "11:00"},
           pruebas=guardar("espacioId", "pm.response.json().espacios[0].id")
           + "\npm.test('el resumen suma los espacios', () => { const j = pm.response.json(); "
             "pm.expect(j.resumen.libres + j.resumen.ocupados + j.resumen.bloqueados).to.eql(j.espacios.length); });"),
    pedido("Plano con horario invertido", "GET", "/api/pisos/{{pisoId}}/plano", 400, token="demoToken",
           query={"fecha": "{{proximoHabil}}", "desde": "12:00", "hasta": "10:00"}),
    pedido("Espacios del piso", "GET", "/api/pisos/{{pisoId}}/espacios", 200, token="demoToken",
           pruebas="pm.test('incluye el espacio del plano', () => pm.expect(pm.response.json().map(e => e.id)).to.include(Number(pm.collectionVariables.get('espacioId'))));"),
    pedido("Espacios de un piso inexistente", "GET", f"/api/pisos/{NO_EXISTE}/espacios", 404, token="demoToken"),
    pedido("Espacio", "GET", "/api/espacios/{{espacioId}}", 200, token="demoToken",
           pruebas="pm.test('tiene precio por hora', () => pm.expect(pm.response.json().precioHora).to.be.above(0));"),
    pedido("Espacio inexistente", "GET", f"/api/espacios/{NO_EXISTE}", 404, token="demoToken"),
    pedido("Agenda del espacio", "GET", "/api/espacios/{{espacioId}}/agenda", 200, token="demoToken",
           query={"desde": "{{proximoHabil}}", "hasta": "{{proximoHabil}}"},
           pruebas="pm.test('trae ocupaciones', () => pm.expect(pm.response.json().ocupaciones).to.be.an('array'));"),
    pedido("Agenda con rango invertido", "GET", "/api/espacios/{{espacioId}}/agenda", 400, token="demoToken",
           query={"desde": "{{proximoHabil}}", "hasta": "2000-01-01"}),
    pedido("Franjas libres", "GET", "/api/espacios/{{espacioId}}/franjas-libres", 200, token="demoToken",
           query={"fecha": "{{proximoHabil}}", "duracion": "60"},
           pruebas=SUMAR + "const huecos = pm.response.json();\n"
           # Two bookings in a row are made from it: the first gap of at least two hours.
           + "const largo = huecos.find(h => new Date(h.fin.slice(0, 19) + 'Z') - new Date(h.inicio.slice(0, 19) + 'Z') >= 7200000) || huecos[0];\n"
           + "const inicio = largo.inicio.slice(0, 19);\n"
           + guardar("franjaInicio", "inicio") + "\n" + guardar("franjaFin", "sumar(inicio, 60)") + "\n"
           + guardar("franja2Inicio", "sumar(inicio, 60)") + "\n" + guardar("franja2Fin", "sumar(inicio, 120)")
           + "\npm.test('hay franjas', () => pm.expect(pm.response.json()).to.not.be.empty);"),
    pedido("Franjas libres de un espacio inexistente", "GET", f"/api/espacios/{NO_EXISTE}/franjas-libres", 404, token="demoToken",
           query={"fecha": "{{proximoHabil}}"}),
])

TARJETA = {"titular": "CLIENTE POSTMAN", "numeroTarjeta": "4111 1111 1111 1111", "vencimiento": "12/35", "codigoSeguridad": "123"}
medios = carpeta("04 Medios de pago", "Tarjetas del cliente: solo se guardan los últimos cuatro dígitos.", [
    pedido("Agregar tarjeta", "POST", "/api/usuarios/me/medios-pago", 201, token="nuevoToken", cuerpo=TARJETA,
           pruebas=guardar("medioId", "pm.response.json().id")
           + "\npm.test('guarda solo los últimos cuatro', () => pm.expect(pm.response.json().ultimos4).to.eql('1111'));"),
    pedido("Agregar tarjeta vencida", "POST", "/api/usuarios/me/medios-pago", 400, token="nuevoToken",
           cuerpo={**TARJETA, "vencimiento": "01/20"}),
    pedido("Mis tarjetas", "GET", "/api/usuarios/me/medios-pago", 200, token="nuevoToken",
           pruebas="pm.test('la primera es la principal', () => pm.expect(pm.response.json()[0].principal).to.eql(true));"),
    pedido("Mis tarjetas sin sesión", "GET", "/api/usuarios/me/medios-pago", 401),
    pedido("Editar tarjeta", "PUT", "/api/usuarios/me/medios-pago/{{medioId}}", 200, token="nuevoToken",
           cuerpo={"titular": "TITULAR EDITADO", "vencimiento": "10/36"},
           pruebas="pm.test('cambió el titular', () => pm.expect(pm.response.json().titular).to.eql('TITULAR EDITADO'));"),
    pedido("Editar tarjeta inexistente", "PUT", f"/api/usuarios/me/medios-pago/{NO_EXISTE}", 404, token="nuevoToken",
           cuerpo={"titular": "TITULAR EDITADO", "vencimiento": "10/36"}),
    pedido("Marcar principal", "PATCH", "/api/usuarios/me/medios-pago/{{medioId}}/principal", 200, token="nuevoToken"),
    pedido("Marcar principal inexistente", "PATCH", f"/api/usuarios/me/medios-pago/{NO_EXISTE}/principal", 404, token="nuevoToken"),
])

reservas = carpeta("05 Reservas", "El cliente reserva una franja libre.", [
    pedido("Reservar", "POST", "/api/reservas", 201, token="nuevoToken",
           cuerpo={"espacioId": "{{espacioId}}", "inicio": "{{franjaInicio}}", "fin": "{{franjaFin}}", "medioPagoId": "{{medioId}}"},
           pruebas=guardar("reservaId", "pm.response.json().id")
           + "\npm.test('queda pendiente de cobro', () => pm.expect(pm.response.json().estado).to.eql('PENDIENTE'));"),
    pedido("Reservar una franja tomada", "POST", "/api/reservas", 409, token="nuevoToken",
           cuerpo={"espacioId": "{{espacioId}}", "inicio": "{{franjaInicio}}", "fin": "{{franjaFin}}"}),
    pedido("Reservar sin espacio", "POST", "/api/reservas", 400, token="nuevoToken",
           cuerpo={"inicio": "{{franjaInicio}}", "fin": "{{franjaFin}}"}),
    pedido("Mis reservas", "GET", "/api/reservas/mias", 200, token="nuevoToken",
           pruebas="pm.test('está la nueva', () => pm.expect(pm.response.json().proximas.map(r => r.id)).to.include(Number(pm.collectionVariables.get('reservaId'))));"),
    pedido("Mis reservas sin sesión", "GET", "/api/reservas/mias", 401),
    pedido("Detalle de la reserva", "GET", "/api/reservas/{{reservaId}}", 200, token="nuevoToken",
           pruebas="pm.test('se puede cancelar', () => pm.expect(pm.response.json().cancelable).to.eql(true));"),
    pedido("Detalle de una reserva inexistente", "GET", f"/api/reservas/{NO_EXISTE}", 404, token="nuevoToken"),
])

usuarios = carpeta("06 Usuarios", "Perfil propio y listado para el administrador.", [
    pedido("Mi perfil", "GET", "/api/usuarios/me", 200, token="nuevoToken",
           pruebas="pm.test('es el usuario confirmado', () => pm.expect(pm.response.json().email).to.eql(pm.environment.get('nuevoEmail')));"),
    pedido("Mi perfil sin sesión", "GET", "/api/usuarios/me", 401),
    pedido("Editar mi teléfono", "PATCH", "/api/usuarios/me", 200, token="nuevoToken", cuerpo={"telefono": "+54 11 4444 1111"},
           pruebas="pm.test('quedó el nuevo', () => pm.expect(pm.response.json().telefono).to.eql('+54 11 4444 1111'));"),
    pedido("Editar con teléfono inválido", "PATCH", "/api/usuarios/me", 400, token="nuevoToken", cuerpo={"telefono": "abc"}),
    pedido("Perfil del administrador", "GET", "/api/usuarios/me", 200, token="adminToken",
           pruebas=guardar("adminId", "pm.response.json().id")),
    pedido("Listado de usuarios", "GET", "/api/usuarios", 200, token="adminToken",
           pruebas="pm.test('incluye al cliente', () => pm.expect(pm.response.json().map(u => u.email)).to.include(pm.environment.get('nuevoEmail')));"),
    pedido("Listado de usuarios como cliente", "GET", "/api/usuarios", 403, token="nuevoToken"),
])

ADMIN_EDITAR = {"usuarioId": "{{nuevoId}}", "espacioId": "{{espacioId}}", "inicio": "{{franjaInicio}}", "fin": "{{franjaFin}}",
                "motivo": "Confirmada por la administración"}
administracion = carpeta("07 Administración", "Reservas, agenda, clientes, bloqueos y auditoría.", [
    pedido("Listado de reservas", "GET", "/api/admin/reservas", 200, token="adminToken", query={"q": "{{nuevoEmail}}"},
           pruebas="pm.test('encuentra la del cliente', () => pm.expect(pm.response.json().totalElements).to.be.at.least(1));"),
    pedido("Listado de reservas como cliente", "GET", "/api/admin/reservas", 403, token="nuevoToken"),
    pedido("Agenda del piso", "GET", "/api/admin/agenda", 200, token="adminToken", query={"pisoId": "{{pisoId}}", "fecha": "{{proximoHabil}}"},
           pruebas="pm.test('trae los espacios del piso', () => pm.expect(pm.response.json().espacios).to.not.be.empty);"),
    pedido("Agenda sin piso", "GET", "/api/admin/agenda", 400, token="adminToken", query={"fecha": "{{proximoHabil}}"}),
    pedido("Buscar clientes", "GET", "/api/admin/clientes", 200, token="adminToken", query={"q": "{{nuevoEmail}}"},
           pruebas="pm.test('lo encuentra', () => pm.expect(pm.response.json()[0].id).to.eql(Number(pm.collectionVariables.get('nuevoId'))));"),
    pedido("Buscar clientes como cliente", "GET", "/api/admin/clientes", 403, token="nuevoToken"),
    pedido("Confirmar la reserva", "PUT", "/api/admin/reservas/{{reservaId}}", 200, token="adminToken",
           cuerpo={**ADMIN_EDITAR, "estado": "CONFIRMADA"},
           pruebas="pm.test('queda confirmada', () => pm.expect(pm.response.json().estado).to.eql('CONFIRMADA'));"),
    pedido("Volver la reserva a pendiente", "PUT", "/api/admin/reservas/{{reservaId}}", 409, token="adminToken",
           cuerpo={**ADMIN_EDITAR, "estado": "PENDIENTE"},
           pruebas="pm.test('transición inválida', () => pm.expect(pm.response.json().code).to.eql('TRANSICION_INVALIDA'));"),
    pedido("Reservar para un cliente", "POST", "/api/admin/reservas", 201, token="adminToken",
           cuerpo={"usuarioId": "{{nuevoId}}", "espacioId": "{{espacioId}}", "inicio": "{{franja2Inicio}}", "fin": "{{franja2Fin}}"},
           pruebas=guardar("adminReservaId", "pm.response.json().id")
           + "\npm.test('figura creada por el admin', () => pm.expect(pm.response.json().creadaPorAdmin).to.eql(true));"),
    pedido("Reservar para un administrador", "POST", "/api/admin/reservas", 400, token="adminToken",
           cuerpo={"usuarioId": "{{adminId}}", "espacioId": "{{espacioId}}", "inicio": "{{franja2Inicio}}", "fin": "{{franja2Fin}}"}),
    pedido("Eliminar reserva", "DELETE", "/api/admin/reservas/{{adminReservaId}}", 200, token="adminToken"),
    pedido("Eliminar reserva inexistente", "DELETE", f"/api/admin/reservas/{NO_EXISTE}", 404, token="adminToken"),
    pedido("Bloquear un espacio", "POST", "/api/admin/bloqueos", 201, token="adminToken",
           cuerpo={"alcance": "ESPACIO", "espacioId": "{{espacioId}}", "inicio": "{{diaLejano}}T09:00:00", "fin": "{{diaLejano}}T13:00:00",
                   "motivo": "MANTENIMIENTO", "detalle": "Pintura"},
           pruebas=guardar("bloqueoId", "pm.response.json().bloqueos[0].id")),
    pedido("Bloquear fuera de los bloques de 30 minutos", "POST", "/api/admin/bloqueos", 400, token="adminToken",
           cuerpo={"alcance": "ESPACIO", "espacioId": "{{espacioId}}", "inicio": "{{diaLejano}}T14:10:00", "fin": "{{diaLejano}}T15:00:00",
                   "motivo": "MANTENIMIENTO"}),
    pedido("Bloqueos vigentes", "GET", "/api/admin/bloqueos", 200, token="adminToken",
           pruebas="pm.test('incluye el nuevo', () => pm.expect(pm.response.json().map(b => b.id)).to.include(Number(pm.collectionVariables.get('bloqueoId'))));"),
    pedido("Bloqueos como cliente", "GET", "/api/admin/bloqueos", 403, token="nuevoToken"),
    pedido("Eliminar bloqueo", "DELETE", "/api/admin/bloqueos/{{bloqueoId}}", 200, token="adminToken"),
    pedido("Eliminar bloqueo inexistente", "DELETE", f"/api/admin/bloqueos/{NO_EXISTE}", 404, token="adminToken"),
    pedido("Auditoría", "GET", "/api/admin/auditoria", 200, token="adminToken", query={"evento": "LOGIN_SEGUNDO_FACTOR"},
           pruebas="pm.test('registró el ingreso del admin', () => pm.expect(pm.response.json().content).to.not.be.empty);"),
    pedido("Auditoría como cliente", "GET", "/api/admin/auditoria", 403, token="nuevoToken"),
])

cancelacion = carpeta("08 Cancelación", "El cliente cancela su reserva confirmada.", [
    pedido("Cancelar la reserva", "PATCH", "/api/reservas/{{reservaId}}/cancelar", 200, token="nuevoToken",
           pruebas="pm.test('queda cancelada', () => pm.expect(pm.response.json().estado).to.eql('CANCELADA'));"),
    pedido("Cancelar una reserva ya cancelada", "PATCH", "/api/reservas/{{reservaId}}/cancelar", 409, token="nuevoToken",
           pruebas="pm.test('no es cancelable', () => pm.expect(pm.response.json().code).to.eql('RESERVA_NO_CANCELABLE'));"),
    pedido("Eliminar tarjeta", "DELETE", "/api/usuarios/me/medios-pago/{{medioId}}", 200, token="nuevoToken"),
    pedido("Eliminar tarjeta inexistente", "DELETE", f"/api/usuarios/me/medios-pago/{NO_EXISTE}", 404, token="nuevoToken"),
])

sesion = carpeta("09 Sesión", "Cambio de clave y cierre de sesión.", [
    pedido("Cambiar la clave con la actual incorrecta", "POST", "/api/auth/change-password", 400, token="nuevoToken",
           cuerpo={"passwordActual": "Incorrecta-123", "passwordNueva": "Cambiada-2026-Postman"}),
    pedido("Cambiar la clave", "POST", "/api/auth/change-password", 200, token="nuevoToken",
           cuerpo={"passwordActual": "{{nuevoClave}}", "passwordNueva": "Cambiada-2026-Postman"},
           pruebas=guardar("nuevoToken", "pm.response.json().token") + "\n" + REFRESH.format(var="nuevoRefresh")),
    pedido("Cerrar sesión", "POST", "/api/auth/logout", 200, token="nuevoToken", headers={"Cookie": "pl_refresh={{nuevoRefresh}}"}),
    pedido("El token cerrado ya no sirve", "GET", "/api/usuarios/me", 401, token="nuevoToken"),
    pedido("Cerrar sesión con un método no permitido", "GET", "/api/auth/logout", 405, mensaje=False),
])

coleccion = {
    "info": {
        "name": "Planta Libre API",
        "description": "Cada endpoint con un caso de éxito y uno de error. Se corre en orden: los pedidos se pasan ids y tokens. "
                       "Generada por api-tests/generar_coleccion.py; el entorno lo crea api-tests/preparar-datos.sh.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [{"listen": "prerequest", "script": {"type": "text/javascript", "exec": PRE_COLECCION.strip().split("\n")}}],
    "variable": [],
    "item": [salud, autenticacion, catalogo, medios, reservas, usuarios, administracion, cancelacion, sesion],
}

DESTINO.write_text(json.dumps(coleccion, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
cantidad = sum(len(c["item"]) for c in coleccion["item"])
print(f"{DESTINO.name}: {cantidad} pedidos")
