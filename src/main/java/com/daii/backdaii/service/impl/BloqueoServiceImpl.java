package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.disponibilidad.MotorDisponibilidad;
import com.daii.backdaii.domain.entity.Bloqueo;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.BloqueoRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.PisoRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.reserva.ReservaDTOs.Alcance;
import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueoDTO;
import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueosCreados;
import com.daii.backdaii.dto.reserva.ReservaDTOs.CrearBloqueoRequest;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.ConflictException;
import com.daii.backdaii.exception.FranjaTomadaException;
import com.daii.backdaii.exception.NotFoundException;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BloqueoServiceImpl implements BloqueoService {

    private static final Duration DURACION_MAXIMA = Duration.ofDays(31);

    private final BloqueoRepository bloqueoRepository;
    private final EspacioRepository espacioRepository;
    private final PisoRepository pisoRepository;
    private final UsuarioRepository usuarioRepository;
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

        List<String> ocupados = new ArrayList<>();
        for (Espacio espacio : espacios) {
            try {
                tomaDeFranja.verificarLibre(espacio, franja, null, ahora);
            } catch (FranjaTomadaException e) {
                ocupados.add(espacio.getCodigo());
            }
        }
        if (!ocupados.isEmpty()) {
            throw new ConflictException((ocupados.size() == 1 ? ocupados.get(0) + " tiene" : String.join(", ", ocupados) + " tienen")
                    + " reservas o bloqueos en ese horario. Movelos o cancelalos antes de bloquear.", FranjaTomadaException.CODE);
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
        bloqueoRepository.deleteAll(todoElGrupo ? bloqueoRepository.findByGrupo(bloqueo.getGrupo()) : List.of(bloqueo));
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
