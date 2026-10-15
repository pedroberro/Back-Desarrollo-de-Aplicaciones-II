package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.entity.Bloqueo;
import com.daii.backdaii.domain.entity.Cobro;
import com.daii.backdaii.domain.entity.Edificio;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Piso;
import com.daii.backdaii.domain.entity.PosicionPlano;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.ServicioPiso;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.TipoServicio;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.Comodidad;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EdificioRef;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.EspacioBreve;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.Posicion;
import com.daii.backdaii.dto.catalogo.CatalogoDTOs.Ref;
import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueoDTO;
import com.daii.backdaii.dto.reserva.ReservaDTOs.Cambio;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ClienteRef;
import com.daii.backdaii.dto.reserva.ReservaDTOs.EspacioRef;
import com.daii.backdaii.dto.reserva.ReservaDTOs.Pago;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReintegroSiCancela;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaDTO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Entity to DTO conversions shared by the catalog and booking services. Keeps entities out of the controllers. */
final class Vistas {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es-AR"));
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Vistas() {
    }

    /** "C-101, lun 12 oct 09:00–11:00": how mails and the change log name a booking. */
    static String describir(Espacio espacio, Franja franja) {
        return espacio.getCodigo() + ", " + describir(franja);
    }

    static String describir(Franja franja) {
        return franja.inicio().format(DIA) + " " + franja.inicio().format(HORA) + "–" + franja.fin().format(HORA);
    }

    static Ref ref(Piso piso) {
        return new Ref(piso.getId(), piso.getNombre());
    }

    static Ref ref(Edificio edificio) {
        return new Ref(edificio.getId(), edificio.getNombre());
    }

    static EdificioRef edificioRef(Edificio e) {
        return new EdificioRef(e.getId(), e.getNombre(), e.getDireccion(), e.getHoraApertura(), e.getHoraCierre());
    }

    static EspacioRef espacioRef(Espacio e) {
        return new EspacioRef(e.getId(), e.getCodigo(), e.getTipo(), e.getTipo().getEtiqueta(), e.getCapacidad());
    }

    static EspacioBreve espacioBreve(Espacio e) {
        return new EspacioBreve(e.getId(), e.getCodigo(), e.getTipo(), e.getTipo().getEtiqueta(), e.getCapacidad());
    }

    static Posicion posicion(PosicionPlano p) {
        return new Posicion(p.getX(), p.getY(), p.getAncho(), p.getAlto());
    }

    static ClienteRef cliente(Usuario u) {
        return new ClienteRef(u.getId(), u.getNombre(), u.getEmail());
    }

    /** "Monitor 24\" · HDMI y USB-C" → "Monitor 24\"". */
    static List<String> equipamientoCorto(Espacio e) {
        return e.getEquipamiento().stream().map(item -> item.split(" · ")[0]).toList();
    }

    /** Amenities of a floor as the chips read them: "Cocina", "2 baños", "Luz natural". */
    static List<Comodidad> comodidades(Piso piso, Collection<ServicioPiso> servicios) {
        List<Comodidad> comodidades = new ArrayList<>();
        if (servicios.stream().anyMatch(s -> s.getTipo() == TipoServicio.COCINA)) {
            comodidades.add(new Comodidad("COCINA", "Cocina"));
        }
        if (servicios.stream().anyMatch(s -> s.getTipo() == TipoServicio.SALA_ESTAR)) {
            comodidades.add(new Comodidad("SALA_ESTAR", "Sala de estar"));
        }
        long banos = servicios.stream().filter(s -> s.getTipo() == TipoServicio.BANO).count();
        if (banos > 0) {
            comodidades.add(new Comodidad("BANO", banos == 1 ? "Baño" : banos + " baños"));
        }
        if (Boolean.TRUE.equals(piso.getLuzNatural())) {
            comodidades.add(new Comodidad("LUZ_NATURAL", "Luz natural"));
        }
        return comodidades;
    }

    static ReservaDTO reserva(Reserva r, LocalDateTime ahora, boolean cancelable, boolean conCambios) {
        return reserva(r, ahora, cancelable, conCambios, null, null);
    }

    static ReservaDTO reserva(Reserva r, LocalDateTime ahora, boolean cancelable, boolean conCambios,
                              Cobro cobro, ReintegroSiCancela reintegro) {
        Espacio espacio = r.getEspacio();
        Piso piso = espacio.getPiso();
        Edificio edificio = piso.getEdificio();
        List<Cambio> cambios = conCambios
                ? r.getCambios().stream()
                    .map(c -> new Cambio(c.getCreadoEl(), c.getAutor().getNombre(), c.getMotivo(), c.getDetalle()))
                    .toList()
                : null;
        return new ReservaDTO(
                r.getId(),
                r.getCodigo(),
                r.estadoEn(ahora),
                r.getInicio(),
                r.getFin(),
                (int) Duration.between(r.getInicio(), r.getFin()).toMinutes(),
                espacioRef(espacio),
                ref(piso),
                ref(edificio),
                new Ref(edificio.getEmpresa().getId(), edificio.getEmpresa().getNombre()),
                cliente(r.getUsuario()),
                !r.getCreadaPor().getId().equals(r.getUsuario().getId()),
                cancelable,
                r.getCreadaEl(),
                cambios,
                cobro == null ? null : pago(cobro),
                reintegro);
    }

    static Pago pago(Cobro c) {
        String tarjeta = c.getMedioPago() == null ? null : c.getMedioPago().getNumeroTarjetaMasked();
        return new Pago(c.getMonto(), c.getEstado(), tarjeta == null ? null : tarjeta.substring(tarjeta.length() - 4),
                c.getMontoReintegrado());
    }

    static BloqueoDTO bloqueo(Bloqueo b) {
        Espacio espacio = b.getEspacio();
        return new BloqueoDTO(
                b.getId(),
                b.getGrupo(),
                espacioRef(espacio),
                ref(espacio.getPiso()),
                ref(espacio.getPiso().getEdificio()),
                b.getInicio(),
                b.getFin(),
                b.getMotivo(),
                b.getMotivo().getEtiqueta(),
                b.getDetalle(),
                b.getCreadoPor().getNombre(),
                b.getCreadoEl());
    }
}
