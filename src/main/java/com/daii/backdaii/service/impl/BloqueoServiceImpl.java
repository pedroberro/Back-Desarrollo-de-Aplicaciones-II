package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.disponibilidad.MotorDisponibilidad;
import com.daii.backdaii.domain.entity.Bloqueo;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.domain.evento.ReservaCancelada;
import com.daii.backdaii.domain.repository.BloqueoRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.PisoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.reserva.ReservaDTOs.Alcance;
import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueoDTO;
import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueosCreados;
import com.daii.backdaii.dto.reserva.ReservaDTOs.CrearBloqueoRequest;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaAfectada;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.ConflictException;
import com.daii.backdaii.exception.FranjaTomadaException;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.exception.ReservasAfectadasException;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.service.BloqueoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BloqueoServiceImpl implements BloqueoService {

    private static final Duration DURACION_MAXIMA = Duration.ofDays(31);

    private final BloqueoRepository bloqueoRepository;
    private final EspacioRepository espacioRepository;
    private final PisoRepository pisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaRepository reservaRepository;
    private final Cancelaciones cancelaciones;
    private final AuditoriaService auditoria;
    private final TomaDeFranja tomaDeFranja;
    private final Clock reloj;

    @Override
    @Transactional
    public BloqueosCreados crear(String adminEmail, CrearBloqueoRequest request) {
        Usuario admin = usuarioRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Franja franja = TomaDeFranja.franja(request.inicio(), request.fin());
        validar(franja, ahora);

        // Locked in id order: two admins blocking overlapping floors cannot deadlock.
        List<Espacio> espacios = espaciosDelAlcance(request).stream()
                .sorted(Comparator.comparing(Espacio::getId))
                .map(e -> tomaDeFranja.bloquear(e.getId()))
                .toList();

        // Bookings in the way: the administrator confirms, and they are cancelled with a full refund.
        String motivoBloqueo = request.motivo().getEtiqueta().toLowerCase(Locale.ROOT)
                + (request.detalle() == null || request.detalle().isBlank() ? "" : ": " + request.detalle().trim());
        List<Reserva> afectadas = reservaRepository.findEnRango(espacios.stream().map(Espacio::getId).toList(),
                        franja.inicio(), franja.fin(), EstadoReserva.ACTIVOS).stream()
                .filter(r -> r.getFin().isAfter(ahora))
                .toList();
        if (!afectadas.isEmpty() && !Boolean.TRUE.equals(request.cancelarReservas())) {
            throw new ReservasAfectadasException((afectadas.size() == 1 ? "Hay 1 reserva" : "Hay " + afectadas.size() + " reservas")
                    + " en ese horario. Si bloqueás igual, se cancelan, se devuelve el total y se avisa a cada cliente por mail.",
                    afectadas.stream().map(r -> new ReservaAfectada(r.getId(), r.getCodigo(), r.getEspacio().getCodigo(),
                            r.getUsuario().getNombre(), r.getInicio(), r.getFin())).toList());
        }
        for (Reserva afectada : afectadas) {
            Reserva reserva = reservaRepository.findBloqueadaById(afectada.getId()).orElseThrow();
            if (!reserva.getEstado().ocupa()) {
                continue;                   // cancelled meanwhile
            }
            reserva.registrarCambio(admin, "Bloqueo del espacio: " + motivoBloqueo,
                    "Estado: " + etiqueta(reserva.getEstado()) + " → Cancelada");
            cancelaciones.cancelar(reserva, ReservaCancelada.Motivo.BLOQUEO, motivoBloqueo);
        }
        reservaRepository.flush();

        // What is left in the way are other blocks: those are never overridden.
        List<String> ocupados = new ArrayList<>();
        for (Espacio espacio : espacios) {
            try {
                tomaDeFranja.verificarLibre(espacio, franja, null, ahora);
            } catch (FranjaTomadaException e) {
                ocupados.add(espacio.getCodigo());
            }
        }
        if (!ocupados.isEmpty()) {
            throw new ConflictException((ocupados.size() == 1 ? ocupados.get(0) + " ya tiene" : String.join(", ", ocupados) + " ya tienen")
                    + " un bloqueo en ese horario. Modificá o eliminá ese bloqueo primero.", FranjaTomadaException.CODE);
        }

        UUID grupo = UUID.randomUUID();
        String detalle = request.detalle() == null ? "" : request.detalle().trim();
        List<Bloqueo> creados = tomaDeFranja.guardar(() -> bloqueoRepository.saveAllAndFlush(espacios.stream()
                .map(espacio -> Bloqueo.builder()
                        .espacio(espacio)
                        .inicio(franja.inicio())
                        .fin(franja.fin())
                        .motivo(request.motivo())
                        .detalle(detalle)
                        .grupo(grupo)
                        .creadoPor(admin)
                        .build())
                .toList()));

        auditoria.registrar(EventoAuditoria.BLOQUEO_CREADO, ResultadoAuditoria.EXITO, adminEmail,
                creados.size() + " espacio(s), " + motivoBloqueo + ", " + afectadas.size() + " reserva(s) cancelada(s)");
        return new BloqueosCreados(grupo, creados.size(), creados.stream().map(Vistas::bloqueo).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloqueoDTO> listarVigentes() {
        return bloqueoRepository.findVigentesDesde(LocalDateTime.now(reloj)).stream().map(Vistas::bloqueo).toList();
    }

    @Override
    @Transactional
    public void eliminar(Long id, boolean todoElGrupo) {
        Bloqueo bloqueo = bloqueoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Bloqueo no encontrado"));
        List<Bloqueo> borrados = todoElGrupo ? bloqueoRepository.findByGrupo(bloqueo.getGrupo()) : List.of(bloqueo);
        bloqueoRepository.deleteAll(borrados);
        auditoria.registrar(EventoAuditoria.BLOQUEO_ELIMINADO, ResultadoAuditoria.EXITO, usuarioActual(),
                borrados.size() + " bloqueo(s) de " + bloqueo.getMotivo().getEtiqueta());
    }

    private static String usuarioActual() {
        var autenticacion = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return autenticacion == null ? null : autenticacion.getName();
    }

    private static String etiqueta(EstadoReserva estado) {
        String nombre = estado.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }

    private List<Espacio> espaciosDelAlcance(CrearBloqueoRequest request) {
        if (request.alcance() == Alcance.ESPACIO) {
            if (request.espacioId() == null) {
                throw new BadRequestException("Elegí el espacio a bloquear");
            }
            return List.of(espacioRepository.findById(request.espacioId())
                    .orElseThrow(() -> new NotFoundException("Espacio no encontrado")));
        }
        if (request.pisoId() == null || !pisoRepository.existsById(request.pisoId())) {
            throw new BadRequestException("Elegí el piso a bloquear");
        }
        List<Espacio> espacios = espacioRepository.findByPisoIdOrderByCodigo(request.pisoId());
        if (espacios.isEmpty()) {
            throw new BadRequestException("El piso no tiene espacios reservables");
        }
        return espacios;
    }

    private static void validar(Franja franja, LocalDateTime ahora) {
        long paso = MotorDisponibilidad.PASO.toMinutes();
        if (franja.inicio().getMinute() % paso != 0 || franja.fin().getMinute() % paso != 0) {
            throw new BadRequestException("Los bloqueos van en bloques de 30 minutos");
        }
        if (!franja.fin().isAfter(ahora)) {
            throw new BadRequestException("El bloqueo tiene que terminar en el futuro");
        }
        if (franja.duracion().compareTo(DURACION_MAXIMA) > 0) {
            throw new BadRequestException("Un bloqueo dura como máximo 31 días");
        }
    }
}
