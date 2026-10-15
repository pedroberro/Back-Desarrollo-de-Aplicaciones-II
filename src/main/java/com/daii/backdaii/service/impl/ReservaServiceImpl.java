package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.disponibilidad.ReglasReserva;
import com.daii.backdaii.domain.entity.Cobro;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.MedioPago;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.evento.ReservaCancelada;
import com.daii.backdaii.domain.evento.ReservaCreada;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.reserva.ReservaDTOs.CrearReservaRequest;
import com.daii.backdaii.dto.reserva.ReservaDTOs.MisReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReintegroSiCancela;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaDTO;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.ConflictException;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.service.ReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements ReservaService {

    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MedioPagoRepository medioPagoRepository;
    private final CobroRepository cobroRepository;
    private final Cancelaciones cancelaciones;
    private final TomaDeFranja tomaDeFranja;
    private final ReglasReserva reglas;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    @Override
    @Transactional
    public ReservaDTO crear(String email, CrearReservaRequest request) {
        Usuario usuario = usuario(email);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Franja franja = TomaDeFranja.franja(request.inicio(), request.fin());
        MedioPago medioPago = medioPago(usuario, request.medioPagoId());
        // The screen warns about it too, but the rule lives here: the API is callable directly.
        reservaRepository.findDelUsuarioEnRango(usuario.getId(), franja.inicio(), franja.fin(), EstadoReserva.ACTIVOS).stream()
                .findFirst()
                .ifPresent(otra -> {
                    throw new ConflictException("Ya tenés la reserva " + otra.getCodigo() + " en " + otra.getEspacio().getCodigo()
                            + " en ese horario. No podés reservar dos espacios a la vez.", "RESERVA_SUPERPUESTA");
                });

        Espacio espacio = tomaDeFranja.bloquear(request.espacioId());
        reglas.validar(franja, espacio.getPiso().getEdificio().getHorario(), ahora);
        tomaDeFranja.verificarLibre(espacio, franja, null, ahora);

        Reserva reserva = tomaDeFranja.guardar(() -> reservaRepository.saveAndFlush(Reserva.builder()
                .espacio(espacio)
                .usuario(usuario)
                .creadaPor(usuario)
                .inicio(franja.inicio())
                .fin(franja.fin())
                .estado(EstadoReserva.PENDIENTE)
                .medioPago(medioPago)
                .build()));
        // Stays PENDIENTE until the charge, which runs off the request (see CobroServiceImpl).
        eventos.publishEvent(new ReservaCreada(reserva.getId()));
        return dto(reservaRepository.findDetalleById(reserva.getId()).orElseThrow(), ahora, false);
    }

    @Override
    @Transactional(readOnly = true)
    public MisReservas misReservas(String email) {
        Usuario usuario = usuario(email);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<Reserva> todas = reservaRepository.findByUsuarioIdOrderByInicioAsc(usuario.getId());
        Map<Long, Cobro> cobros = todas.isEmpty() ? Map.of()
                : cobroRepository.findByReservaIdIn(todas.stream().map(Reserva::getId).toList()).stream()
                        .collect(Collectors.toMap(c -> c.getReserva().getId(), Function.identity()));

        List<ReservaDTO> proximas = todas.stream()
                .filter(r -> r.estadoEn(ahora).ocupa())
                .map(r -> dto(r, ahora, false, cobros.get(r.getId())))
                .toList();
        List<ReservaDTO> historial = todas.stream()
                .filter(r -> !r.estadoEn(ahora).ocupa())
                .sorted(Comparator.comparing(Reserva::getInicio).reversed())
                .map(r -> dto(r, ahora, false, cobros.get(r.getId())))
                .toList();
        return new MisReservas(proximas, historial);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaDTO detalle(String email, Long id, boolean esAdmin) {
        Reserva reserva = reservaRepository.findDetalleById(id)
                .filter(r -> esAdmin || r.getUsuario().getEmail().equalsIgnoreCase(email))
                // Someone else's booking answers like a missing one, so ids cannot be probed.
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
        return dto(reserva, LocalDateTime.now(reloj), true);
    }

    @Override
    @Transactional
    public ReservaDTO cancelar(String email, Long id) {
        // Waits for a charge in flight on this booking, so it reads the state that charge left.
        reservaRepository.findBloqueadaById(id);
        Reserva reserva = reservaRepository.findDetalleById(id)
                .filter(r -> r.getUsuario().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (!reglas.cancelable(reserva, ahora)) {
            throw new BadRequestException("Solo se pueden cancelar las reservas pendientes o confirmadas que todavía no empezaron");
        }
        // Policy, refund and the customer's mail: the same path an administrator's cancellation takes.
        cancelaciones.cancelar(reserva, ReservaCancelada.Motivo.CLIENTE, null);
        return dto(reservaRepository.saveAndFlush(reserva), ahora, true);
    }

    private ReservaDTO dto(Reserva reserva, LocalDateTime ahora, boolean conCambios) {
        return dto(reserva, ahora, conCambios, cobroRepository.findByReservaId(reserva.getId()).orElse(null));
    }

    private ReservaDTO dto(Reserva reserva, LocalDateTime ahora, boolean conCambios, Cobro cobro) {
        boolean cancelable = reglas.cancelable(reserva, ahora);
        ReintegroSiCancela reintegro = null;
        if (cancelable) {
            Cancelaciones.Resultado simulado = cancelaciones.simular(reserva, cobro, ahora);
            reintegro = new ReintegroSiCancela(simulado.porcentaje(), simulado.reintegrado());
        }
        return Vistas.reserva(reserva, ahora, cancelable, conCambios, cobro, reintegro);
    }

    /** Optional: without a card the charge uses the customer's main one. */
    private MedioPago medioPago(Usuario usuario, Long medioPagoId) {
        if (medioPagoId == null) {
            return null;
        }
        return medioPagoRepository.findByIdAndUsuarioIdAndEliminadoFalse(medioPagoId, usuario.getId())
                .orElseThrow(() -> new BadRequestException("Elegí uno de tus métodos de pago", "MEDIO_PAGO_INVALIDO"));
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }
}
