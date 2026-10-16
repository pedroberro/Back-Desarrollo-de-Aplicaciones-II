package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.domain.entity.MedioPago;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.medioPago.MedioPagoRequest;
import com.daii.backdaii.dto.medioPago.MedioPagoResumen;
import com.daii.backdaii.dto.medioPago.MedioPagoUpdateRequest;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.service.MedioPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MedioPagoServiceImpl implements MedioPagoService {

        private static final int MAX_MEDIOS_PAGO = 3;

        private final MedioPagoRepository medioPagoRepository;
        private final UsuarioRepository usuarioRepository;
        private final AuditoriaService auditoria;

        @Override
        @Transactional
        public MedioPagoResumen agregarTarjeta(String email, MedioPagoRequest request) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

                long cantidad = medioPagoRepository.countByUsuarioIdAndEliminadoFalse(usuario.getId());
                if (cantidad >= MAX_MEDIOS_PAGO) {
                        throw new BadRequestException("Podés guardar hasta " + MAX_MEDIOS_PAGO + " métodos de pago");
                }
                if (estaVencida(request.getVencimiento())) {
                        throw new BadRequestException("La tarjeta está vencida");
                }

                String numero = request.getNumeroTarjeta().replaceAll("\\s", "");
                String ultimos4 = numero.substring(numero.length() - 4);
                String enmascarado = "**** **** **** " + ultimos4;

                MedioPago medioPago = MedioPago.builder()
                        .usuario(usuario)
                        .tipo("tarjeta")
                        .descripcion(enmascarado)
                        .numeroTarjetaMasked(enmascarado)
                        .titular(request.getTitular())
                        .vencimiento(request.getVencimiento())
                        .principal(cantidad == 0)
                        .build();

                MedioPagoResumen resumen = toResumen(medioPagoRepository.save(medioPago));
                auditoria.registrar(EventoAuditoria.ALTA_MEDIO_PAGO, ResultadoAuditoria.EXITO, usuario.getEmail(),
                        "Tarjeta terminada en " + ultimos4);
                return resumen;
        }

        @Override
        @Transactional(readOnly = true)
        public List<MedioPagoResumen> listar(String email) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

                return medioPagoRepository.findByUsuarioIdAndEliminadoFalseOrderByPrincipalDescCreadoElAsc(usuario.getId())
                        .stream()
                        .map(this::toResumen)
                        .toList();
        }

        @Override
        @Transactional
        public MedioPagoResumen actualizar(String email, Long id, MedioPagoUpdateRequest request) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

                MedioPago medioPago = medioPagoRepository.findByIdAndUsuarioIdAndEliminadoFalse(id, usuario.getId())
                        .orElseThrow(() -> new NotFoundException("Medio de pago no encontrado"));

                if (estaVencida(request.getVencimiento())) {
                        throw new BadRequestException("La tarjeta está vencida");
                }

                medioPago.setTitular(request.getTitular());
                medioPago.setVencimiento(request.getVencimiento());
                return toResumen(medioPagoRepository.save(medioPago));
        }

        @Override
        @Transactional
        public List<MedioPagoResumen> marcarPrincipal(String email, Long id) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

                medioPagoRepository.findByIdAndUsuarioIdAndEliminadoFalse(id, usuario.getId())
                        .orElseThrow(() -> new NotFoundException("Medio de pago no encontrado"));

                // At most three cards per user, so updating them one by one is fine.
                List<MedioPago> tarjetas = medioPagoRepository
                        .findByUsuarioIdAndEliminadoFalseOrderByPrincipalDescCreadoElAsc(usuario.getId());
                tarjetas.forEach(tarjeta -> tarjeta.setPrincipal(tarjeta.getId().equals(id)));
                medioPagoRepository.saveAllAndFlush(tarjetas);

                return medioPagoRepository.findByUsuarioIdAndEliminadoFalseOrderByPrincipalDescCreadoElAsc(usuario.getId())
                        .stream()
                        .map(this::toResumen)
                        .toList();
        }

        @Override
        @Transactional
        public void eliminar(String email, Long id) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

                MedioPago medioPago = medioPagoRepository.findByIdAndUsuarioIdAndEliminadoFalse(id, usuario.getId())
                        .orElseThrow(() -> new NotFoundException("Medio de pago no encontrado"));

                medioPago.setEliminado(true);
                boolean eraPrincipal = Boolean.TRUE.equals(medioPago.getPrincipal());
                medioPago.setPrincipal(false);
                medioPagoRepository.saveAndFlush(medioPago);

                // A user with cards always has a primary one: the oldest remaining takes over.
                if (eraPrincipal) {
                        medioPagoRepository.findFirstByUsuarioIdAndEliminadoFalseOrderByCreadoElAsc(usuario.getId())
                                .ifPresent(siguiente -> {
                                        siguiente.setPrincipal(true);
                                        medioPagoRepository.save(siguiente);
                                });
                }
                auditoria.registrar(EventoAuditoria.BAJA_MEDIO_PAGO, ResultadoAuditoria.EXITO, usuario.getEmail(),
                        "Tarjeta terminada en " + ultimos4(medioPago.getNumeroTarjetaMasked()));
        }

        // Format is already validated as MM/AA; a card stays valid through its expiry month.
        private boolean estaVencida(String vencimiento) {
                int mes = Integer.parseInt(vencimiento.substring(0, 2));
                int anio = 2000 + Integer.parseInt(vencimiento.substring(3, 5));
                return YearMonth.of(anio, mes).isBefore(YearMonth.now());
        }

        private String ultimos4(String enmascarado) {
                return enmascarado.substring(enmascarado.length() - 4);
        }

        private MedioPagoResumen toResumen(MedioPago medioPago) {
                String estado = Boolean.TRUE.equals(medioPago.getVerificado()) ? "verificado" : "pendiente";
                return new MedioPagoResumen(
                        medioPago.getId(),
                        medioPago.getTipo(),
                        medioPago.getDescripcion(),
                        ultimos4(medioPago.getNumeroTarjetaMasked()),
                        medioPago.getTitular(),
                        medioPago.getVencimiento(),
                        Boolean.TRUE.equals(medioPago.getPrincipal()),
                        medioPago.getVerificado(),
                        estado);
        }
}