package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.disponibilidad.ReglasReserva;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.reserva.ReservaDTOs.CrearReservaRequest;
import com.daii.backdaii.dto.reserva.ReservaDTOs.MisReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaDTO;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.service.ReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements ReservaService {

    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TomaDeFranja tomaDeFranja;
    private final ReglasReserva reglas;
    private final Clock reloj;

    @Override
    @Transactional
    public ReservaDTO crear(String email, CrearReservaRequest request) {
        Usuario usuario = usuario(email);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Franja franja = TomaDeFranja.franja(request.inicio(), request.fin());

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
                .build()));
        return dto(reservaRepository.findDetalleById(reserva.getId()).orElseThrow(), ahora, false);
    }

    @Override
    @Transactional(readOnly = true)
    public MisReservas misReservas(String email) {
        Usuario usuario = usuario(email);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<Reserva> todas = reservaRepository.findByUsuarioIdOrderByInicioAsc(usuario.getId());

        List<ReservaDTO> proximas = todas.stream()
                .filter(r -> r.estadoEn(ahora).ocupa())
                .map(r -> dto(r, ahora, false))
                .toList();
        List<ReservaDTO> historial = todas.stream()
                .filter(r -> !r.estadoEn(ahora).ocupa())
                .sorted(Comparator.comparing(Reserva::getInicio).reversed())
                .map(r -> dto(r, ahora, false))
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
        Reserva reserva = reservaRepository.findDetalleById(id)
                .filter(r -> r.getUsuario().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (!reglas.cancelable(reserva, ahora)) {
            throw new BadRequestException("Solo se pueden cancelar las reservas pendientes o confirmadas que todavía no empezaron");
        }
        reserva.setEstado(EstadoReserva.CANCELADA);
        return dto(reservaRepository.save(reserva), ahora, true);
    }

    private ReservaDTO dto(Reserva reserva, LocalDateTime ahora, boolean conCambios) {
        return Vistas.reserva(reserva, ahora, reglas.cancelable(reserva, ahora), conCambios);
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }
}
