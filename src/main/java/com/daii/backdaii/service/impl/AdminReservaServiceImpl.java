package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.disponibilidad.ReglasReserva;
import com.daii.backdaii.domain.entity.Bloqueo;
import com.daii.backdaii.domain.entity.Espacio;
import com.daii.backdaii.domain.entity.Piso;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.TransicionInvalidaException;
import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.evento.ReservaCancelada;
import com.daii.backdaii.domain.evento.ReservaCreada;
import com.daii.backdaii.domain.repository.BloqueoRepository;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.PisoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.reserva.ReservaDTOs.AdminReservaRequest;
import com.daii.backdaii.dto.reserva.ReservaDTOs.AgendaPiso;
import com.daii.backdaii.dto.reserva.ReservaDTOs.BloqueAgenda;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ClienteBusqueda;
import com.daii.backdaii.dto.reserva.ReservaDTOs.FiltroReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.PaginaReservas;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservaDTO;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.service.AdminReservaService;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.service.EmailService;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminReservaServiceImpl implements AdminReservaService {

    private static final int MOTIVO_MINIMO = 10;
    private static final int TAMANIO_MAXIMO = 100;

    private final ReservaRepository reservaRepository;
    private final BloqueoRepository bloqueoRepository;
    private final EspacioRepository espacioRepository;
    private final PisoRepository pisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TomaDeFranja tomaDeFranja;
    private final ReglasReserva reglas;
    private final EmailService emailService;
    private final CobroRepository cobroRepository;
    private final Cancelaciones cancelaciones;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    @Override
    @Transactional(readOnly = true)
    public PaginaReservas listar(FiltroReservas filtro, Long espacioId, int pagina, int tamanio) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanio, 1, TAMANIO_MAXIMO),
                Sort.by("inicio").ascending().and(Sort.by("id")));
        Page<Reserva> page = reservaRepository.findAll(especificacion(filtro, espacioId, ahora), pageable);
        return new PaginaReservas(
                page.getContent().stream().map(r -> Vistas.reserva(r, ahora, reglas.cancelable(r, ahora), false)).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                reservaRepository.count());
    }

    @Override
    @Transactional(readOnly = true)
    public AgendaPiso agenda(Long pisoId, LocalDate fecha) {
        Piso piso = pisoRepository.findConEdificioById(pisoId)
                .orElseThrow(() -> new NotFoundException("Piso no encontrado"));
        List<Espacio> espacios = espacioRepository.findByPisoIdOrderByCodigo(pisoId);
        List<Long> ids = espacios.stream().map(Espacio::getId).toList();
        LocalDateTime desde = fecha.atStartOfDay();
        LocalDateTime hasta = desde.plusDays(1);
        LocalDateTime ahora = LocalDateTime.now(reloj);

        List<BloqueAgenda> bloques = new ArrayList<>();
        for (Reserva r : reservaRepository.findEnRango(ids, desde, hasta, EstadoReserva.ACTIVOS)) {
            bloques.add(new BloqueAgenda("RESERVA", r.getId(), r.getEspacio().getId(), r.getInicio(), r.getFin(),
                    r.estadoEn(ahora), Vistas.cliente(r.getUsuario()), null, null, null, null));
        }
        for (Bloqueo b : bloqueoRepository.findEnRango(ids, desde, hasta)) {
            bloques.add(new BloqueAgenda("BLOQUEO", b.getId(), b.getEspacio().getId(), b.getInicio(), b.getFin(),
                    null, null, b.getMotivo(), b.getMotivo().getEtiqueta(), b.getDetalle(), b.getGrupo()));
        }
        bloques.sort(Comparator.comparing(BloqueAgenda::inicio));

        return new AgendaPiso(Vistas.ref(piso), Vistas.edificioRef(piso.getEdificio()), fecha, ahora,
                espacios.stream().map(Vistas::espacioBreve).toList(), bloques);
    }

    @Override
    @Transactional
    public ReservaDTO crear(String adminEmail, AdminReservaRequest request) {
        Usuario admin = usuario(adminEmail);
        Usuario cliente = cliente(request.usuarioId());
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Franja franja = TomaDeFranja.franja(request.inicio(), request.fin());

        Espacio espacio = tomaDeFranja.bloquear(request.espacioId());
        reglas.validar(franja, espacio.getPiso().getEdificio().getHorario(), ahora);
        tomaDeFranja.verificarLibre(espacio, franja, null, ahora);

        Reserva reserva = tomaDeFranja.guardar(() -> reservaRepository.saveAndFlush(Reserva.builder()
                .espacio(espacio)
                .usuario(cliente)
                .creadaPor(admin)
                .inicio(franja.inicio())
                .fin(franja.fin())
                .estado(EstadoReserva.PENDIENTE)
                .build()));

        emailService.sendAvisoReserva(cliente.getEmail(), cliente.getNombre(), "Te reservaron un espacio",
                "Un administrador cargó a tu nombre la reserva " + reserva.getCodigo() + ": " + Vistas.describir(espacio, franja)
                        + ". Queda pendiente hasta que se cobre con tu tarjeta principal.");
        eventos.publishEvent(new ReservaCreada(reserva.getId()));
        return detalle(reserva.getId(), ahora);
    }

    @Override
    @Transactional
    public ReservaDTO actualizar(String adminEmail, Long id, AdminReservaRequest request) {
        reservaRepository.findBloqueadaById(id); // waits for a charge in flight, see CobroServiceImpl
        Reserva reserva = reservaRepository.findDetalleById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
        Usuario admin = usuario(adminEmail);
        LocalDateTime ahora = LocalDateTime.now(reloj);

        if (!reserva.getFin().isAfter(ahora)) {
            // FINALIZADA is final: nothing of a finished booking changes, its state included.
            throw new TransicionInvalidaException(EstadoReserva.FINALIZADA,
                    request.estado() != null ? request.estado() : reserva.getEstado());
        }

        Usuario clienteAnterior = reserva.getUsuario();
        Usuario cliente = request.usuarioId().equals(clienteAnterior.getId()) ? clienteAnterior : cliente(request.usuarioId());
        EstadoReserva estado = request.estado() != null ? request.estado() : reserva.getEstado();
        if (estado != reserva.getEstado()) {
            // Before any other check or side effect: an invalid transition answers 409 right away.
            reserva.getEstado().validarPaso(estado);
        }
        Franja franja = TomaDeFranja.franja(request.inicio(), request.fin());
        Franja franjaAnterior = new Franja(reserva.getInicio(), reserva.getFin());
        Espacio espacioAnterior = reserva.getEspacio();

        List<String> cambios = new ArrayList<>();
        if (!cliente.getId().equals(clienteAnterior.getId())) {
            cambios.add("Cliente: " + clienteAnterior.getNombre() + " → " + cliente.getNombre());
        }
        if (!request.espacioId().equals(espacioAnterior.getId())) {
            cambios.add("Espacio: " + espacioAnterior.getCodigo() + " → " + espacioRepository.findById(request.espacioId())
                    .map(Espacio::getCodigo).orElseThrow(() -> new NotFoundException("Espacio no encontrado")));
        }
        if (!franja.equals(franjaAnterior)) {
            cambios.add("Horario: " + Vistas.describir(franjaAnterior) + " → " + Vistas.describir(franja));
        }
        if (estado != reserva.getEstado()) {
            cambios.add("Estado: " + etiqueta(reserva.getEstado()) + " → " + etiqueta(estado));
        }
        if (cambios.isEmpty()) {
            throw new BadRequestException("No hay cambios para guardar");
        }

        String motivo = request.motivo() == null ? "" : request.motivo().trim();
        boolean ajena = !clienteAnterior.getId().equals(admin.getId());
        if (ajena && motivo.length() < MOTIVO_MINIMO) {
            throw new BadRequestException("Indicá el motivo del cambio (mínimo " + MOTIVO_MINIMO + " caracteres): "
                    + "se guarda con la reserva y el cliente lo ve", "MOTIVO_REQUERIDO");
        }

        Espacio espacio = tomaDeFranja.bloquear(request.espacioId());
        boolean ocupaOtraFranja = !franja.equals(franjaAnterior) || !espacio.getId().equals(espacioAnterior.getId())
                || !reserva.getEstado().ocupa();
        if (estado.ocupa() && ocupaOtraFranja) {
            reglas.validar(franja, espacio.getPiso().getEdificio().getHorario(), ahora);
            tomaDeFranja.verificarLibre(espacio, franja, reserva.getId(), ahora);
        }

        reserva.setUsuario(cliente);
        reserva.setEspacio(espacio);
        reserva.setInicio(franja.inicio());
        reserva.setFin(franja.fin());
        String detalle = String.join("; ", cambios);
        reserva.registrarCambio(admin, motivo.isEmpty() ? "Sin motivo" : motivo, detalle);
        boolean cancela = estado == EstadoReserva.CANCELADA && reserva.getEstado().ocupa();
        if (cancela) {
            // Full refund and its own mail, the same path as any other cancellation.
            cancelaciones.cancelar(reserva, ReservaCancelada.Motivo.ADMINISTRADOR, motivo);
        } else if (estado != reserva.getEstado()) {
            reserva.cambiarEstado(estado);
        }
        tomaDeFranja.guardar(() -> reservaRepository.saveAndFlush(reserva));
        auditoria.registrar(EventoAuditoria.RESERVA_EDITADA_POR_ADMIN, ResultadoAuditoria.EXITO, adminEmail,
                reserva.getCodigo() + " de " + cliente.getEmail() + ": " + detalle);
        if (cancela) {
            return detalle(reserva.getId(), ahora);
        }

        String aviso = "Un administrador modificó tu reserva " + reserva.getCodigo() + ".\n" + detalle.replace("; ", "\n")
                + "\nMotivo: " + motivo;
        emailService.sendAvisoReserva(cliente.getEmail(), cliente.getNombre(), "Cambió tu reserva " + reserva.getCodigo(), aviso);
        if (!cliente.getId().equals(clienteAnterior.getId())) {
            emailService.sendAvisoReserva(clienteAnterior.getEmail(), clienteAnterior.getNombre(),
                    "Tu reserva " + reserva.getCodigo() + " pasó a otra persona", aviso);
        }
        return detalle(reserva.getId(), ahora);
    }

    @Override
    @Transactional
    public void eliminar(String adminEmail, Long id) {
        reservaRepository.findBloqueadaById(id); // waits for a charge in flight, see CobroServiceImpl
        Reserva reserva = reservaRepository.findDetalleById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
        // A booking still to come is cancelled first: full refund and the customer's mail.
        if (reserva.getEstado().ocupa() && reserva.getFin().isAfter(LocalDateTime.now(reloj))) {
            cancelaciones.cancelar(reserva, ReservaCancelada.Motivo.ELIMINADA, null);
        }
        reservaRepository.delete(reserva);
        auditoria.registrar(EventoAuditoria.RESERVA_ELIMINADA_POR_ADMIN, ResultadoAuditoria.EXITO, adminEmail,
                reserva.getCodigo() + " de " + reserva.getUsuario().getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClienteBusqueda> buscarClientes(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        return usuarioRepository.buscar(Rol.CLIENTE, limpio, PageRequest.of(0, 8)).stream()
                .map(u -> new ClienteBusqueda(u.getId(), u.getNombre(), u.getEmail()))
                .toList();
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private ReservaDTO detalle(Long id, LocalDateTime ahora) {
        Reserva reserva = reservaRepository.findDetalleById(id).orElseThrow();
        return Vistas.reserva(reserva, ahora, reglas.cancelable(reserva, ahora), true,
                cobroRepository.findByReservaId(id).orElse(null), null);
    }

    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private Usuario cliente(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
        if (usuario.getRol() != Rol.CLIENTE) {
            throw new BadRequestException("Solo se puede reservar a nombre de un cliente");
        }
        return usuario;
    }

    private static String etiqueta(EstadoReserva estado) {
        String nombre = estado.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }

    /** Filters of the admin listing. The effective state (FINALIZADA is derived) becomes date conditions. */
    private static Specification<Reserva> especificacion(FiltroReservas f, Long espacioId, LocalDateTime ahora) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            Path<Object> espacio = root.get("espacio");
            if (espacioId != null) {
                condiciones.add(cb.equal(espacio.get("id"), espacioId));
            }
            if (f.pisoId() != null) {
                condiciones.add(cb.equal(espacio.get("piso").get("id"), f.pisoId()));
            }
            if (f.edificioId() != null) {
                condiciones.add(cb.equal(espacio.get("piso").get("edificio").get("id"), f.edificioId()));
            }
            if (f.tipo() != null) {
                condiciones.add(cb.equal(espacio.get("tipo"), f.tipo()));
            }
            if (f.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("inicio"), f.desde().atStartOfDay()));
            }
            if (f.hasta() != null) {
                condiciones.add(cb.lessThan(root.get("inicio"), f.hasta().plusDays(1).atStartOfDay()));
            }
            if (f.estado() != null) {
                condiciones.add(switch (f.estado()) {
                    case PENDIENTE, CONFIRMADA -> cb.and(cb.equal(root.get("estado"), f.estado()),
                            cb.greaterThan(root.get("fin"), ahora));
                    case FINALIZADA -> cb.and(root.get("estado").in(EstadoReserva.ACTIVOS),
                            cb.lessThanOrEqualTo(root.get("fin"), ahora));
                    case CANCELADA -> cb.equal(root.get("estado"), EstadoReserva.CANCELADA);
                });
            }
            if (f.q() != null && !f.q().isBlank()) {
                String texto = f.q().trim().toLowerCase(Locale.ROOT);
                List<Predicate> alguno = new ArrayList<>();
                alguno.add(cb.like(cb.lower(root.get("usuario").get("nombre")), "%" + texto + "%"));
                alguno.add(cb.like(cb.lower(root.get("usuario").get("email")), "%" + texto + "%"));
                String numero = texto.replaceFirst("^r-?", "");
                if (numero.matches("\\d{1,18}")) {
                    alguno.add(cb.equal(root.get("id"), Long.parseLong(numero)));
                }
                condiciones.add(cb.or(alguno.toArray(Predicate[]::new)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
