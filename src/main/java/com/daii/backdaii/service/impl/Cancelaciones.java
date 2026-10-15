package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.cobro.PoliticaCancelacion;
import com.daii.backdaii.domain.cobro.PoliticaCancelacion.Quien;
import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.entity.Cobro;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.evento.ReservaCancelada;
import com.daii.backdaii.domain.evento.ReservaCancelada.Motivo;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.exception.ConflictException;
import com.daii.backdaii.service.PasarelaPagos;
import com.daii.backdaii.service.PasarelaPagos.ResultadoPago;
import com.daii.backdaii.service.PasarelaPagos.SolicitudReintegro;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The one way a booking stops taking its slot, whoever asks: customer, administrator, delete or
 * block. Applies the cancellation policy, refunds the charge, and announces it so the customer
 * gets a mail after commit. Must run inside the caller's transaction, with the booking row locked
 * (ReservaRepository.findBloqueadaById): if the refund fails, the cancellation rolls back with it.
 */
@Component
@RequiredArgsConstructor
class Cancelaciones {

    private final CobroRepository cobroRepository;
    private final PasarelaPagos pasarela;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    record Resultado(int porcentaje, BigDecimal cobrado, BigDecimal reintegrado) {
    }

    /**
     * @param detalle what the mail adds: the administrator's reason, or the block's motive
     */
    Resultado cancelar(Reserva reserva, Motivo motivo, String detalle) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Quien quien = motivo == Motivo.CLIENTE ? Quien.CLIENTE : Quien.ADMINISTRADOR;
        int porcentaje = PoliticaCancelacion.porcentajeReintegro(reserva.getInicio(), ahora, quien);

        Optional<Cobro> cobro = cobroRepository.findByReservaId(reserva.getId()).filter(Cobro::reintegrable);
        BigDecimal cobrado = cobro.map(Cobro::getMonto).orElse(null);
        BigDecimal reintegrado = cobro.map(c -> PoliticaCancelacion.reintegro(c.getMonto(), porcentaje)).orElse(BigDecimal.ZERO);
        if (cobro.isPresent()) {
            reintegrar(cobro.get(), reintegrado, ahora);
        }

        reserva.setEstado(EstadoReserva.CANCELADA);
        eventos.publishEvent(new ReservaCancelada(reserva.getCodigo(),
                Vistas.describir(reserva.getEspacio(), new Franja(reserva.getInicio(), reserva.getFin())),
                reserva.getUsuario().getEmail(), reserva.getUsuario().getNombre(), motivo, detalle,
                cobrado, reintegrado, porcentaje));
        return new Resultado(porcentaje, cobrado, reintegrado);
    }

    /** What the customer would get back cancelling now; null monto when nothing was charged yet. */
    Resultado simular(Reserva reserva, Cobro cobro, LocalDateTime ahora) {
        int porcentaje = PoliticaCancelacion.porcentajeReintegro(reserva.getInicio(), ahora, Quien.CLIENTE);
        boolean cobrada = cobro != null && cobro.reintegrable();
        return new Resultado(porcentaje, cobrada ? cobro.getMonto() : null,
                cobrada ? PoliticaCancelacion.reintegro(cobro.getMonto(), porcentaje) : null);
    }

    private void reintegrar(Cobro cobro, BigDecimal monto, LocalDateTime ahora) {
        if (monto.signum() > 0) {
            ResultadoPago resultado = pasarela.reintegrar(new SolicitudReintegro(cobro.getReferencia(), monto));
            if (!resultado.aprobado()) {
                throw new ConflictException("No pudimos devolver el pago: " + resultado.motivoRechazo()
                        + ". La reserva sigue activa; probá de nuevo en unos minutos.", "REINTEGRO_RECHAZADO");
            }
            cobro.setReintegroReferencia(resultado.referencia());
        }
        // Zero is recorded too: the charge was settled by the cancellation and is not refundable later.
        cobro.setMontoReintegrado(monto);
        cobro.setReintegradoEl(ahora);
    }
}
