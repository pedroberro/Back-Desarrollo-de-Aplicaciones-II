package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.cobro.Tarifario;
import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.entity.Cobro;
import com.daii.backdaii.domain.entity.MedioPago;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.enums.EstadoCobro;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.evento.CobroResuelto;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.service.CobroService;
import com.daii.backdaii.service.PasarelaPagos;
import com.daii.backdaii.service.PasarelaPagos.ResultadoPago;
import com.daii.backdaii.service.PasarelaPagos.SolicitudPago;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Second half of the booking: runs when the charge request arrives from the queue, not during
 * the customer's HTTP request. Everything below is one transaction: if the provider fails
 * technically, nothing is written, the booking stays PENDIENTE and the message is retried.
 */
@Service
@RequiredArgsConstructor
public class CobroServiceImpl implements CobroService {

    private static final Logger log = LoggerFactory.getLogger(CobroServiceImpl.class);

    private final ReservaRepository reservaRepository;
    private final MedioPagoRepository medioPagoRepository;
    private final CobroRepository cobroRepository;
    private final PasarelaPagos pasarela;
    private final Tarifario tarifario;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    @Override
    @Transactional
    public void procesar(Long reservaId) {
        Optional<Reserva> encontrada = reservaRepository.findBloqueadaById(reservaId);
        if (encontrada.isEmpty()) {
            log.warn("Cobro descartado: la reserva {} no existe", reservaId);
            return;
        }
        Reserva reserva = encontrada.get();
        if (reserva.getEstado() != EstadoReserva.PENDIENTE || cobroRepository.existsByReservaId(reservaId)) {
            // Cancelled or confirmed by hand before the message arrived, or a redelivery.
            log.info("Cobro descartado: la reserva {} ya no está pendiente de cobro", reserva.getCodigo());
            return;
        }

        Franja franja = new Franja(reserva.getInicio(), reserva.getFin());
        BigDecimal monto = tarifario.monto(reserva.getEspacio().getTipo(), franja);
        MedioPago medio = medioDePago(reserva);
        ResultadoPago resultado = medio == null
                ? ResultadoPago.rechazado("La cuenta no tiene un método de pago")
                : pasarela.cobrar(new SolicitudPago(reserva.getCodigo(), monto, ultimos4(medio), medio.getTitular()));

        cobroRepository.save(Cobro.builder()
                .reserva(reserva)
                .medioPago(medio)
                .monto(monto)
                .estado(resultado.aprobado() ? EstadoCobro.APROBADO : EstadoCobro.RECHAZADO)
                .referencia(resultado.referencia())
                .motivoRechazo(resultado.motivoRechazo())
                .procesadoEl(LocalDateTime.now(reloj))
                .build());
        // A declined booking is cancelled so the slot is free again for someone else.
        reserva.setEstado(resultado.aprobado() ? EstadoReserva.CONFIRMADA : EstadoReserva.CANCELADA);
        log.info("Reserva {}: cobro {} por {}", reserva.getCodigo(), resultado.aprobado() ? "aprobado" : "rechazado", monto);

        eventos.publishEvent(new CobroResuelto(reserva.getId(), reserva.getCodigo(),
                Vistas.describir(reserva.getEspacio(), franja), reserva.getUsuario().getEmail(),
                reserva.getUsuario().getNombre(), monto, medio == null ? null : ultimos4(medio),
                resultado.aprobado(), resultado.motivoRechazo()));
    }

    /** The card chosen when booking, if it still exists; otherwise the customer's main card. */
    private MedioPago medioDePago(Reserva reserva) {
        MedioPago elegido = reserva.getMedioPago();
        if (elegido != null && !Boolean.TRUE.equals(elegido.getEliminado())) {
            return elegido;
        }
        return medioPagoRepository.findByUsuarioIdAndEliminadoFalseOrderByPrincipalDescCreadoElAsc(reserva.getUsuario().getId())
                .stream().findFirst().orElse(null);
    }

    private static String ultimos4(MedioPago medio) {
        String enmascarado = medio.getNumeroTarjetaMasked();
        return enmascarado.substring(enmascarado.length() - 4);
    }
}
