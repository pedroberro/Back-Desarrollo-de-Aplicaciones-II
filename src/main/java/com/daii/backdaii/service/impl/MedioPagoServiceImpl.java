package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.entity.MedioPago;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.dto.medioPago.MedioPagoRequest;
import com.daii.backdaii.dto.medioPago.MedioPagoResumen;
import com.daii.backdaii.service.MedioPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedioPagoServiceImpl implements MedioPagoService {

        private final MedioPagoRepository medioPagoRepository;
        private final UsuarioRepository usuarioRepository;

        @Override
        @Transactional
        public MedioPagoResumen agregarTarjeta(String email, MedioPagoRequest request) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

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
                        .build();

                return toResumen(medioPagoRepository.save(medioPago));
        }

        @Override
        @Transactional(readOnly = true)
        public List<MedioPagoResumen> listar(String email) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                return medioPagoRepository.findByUsuarioIdAndEliminadoFalse(usuario.getId())
                        .stream()
                        .map(this::toResumen)
                        .toList();
        }

        @Override
        @Transactional
        public void eliminar(String email, Long id) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                MedioPago medioPago = medioPagoRepository.findByIdAndUsuarioIdAndEliminadoFalse(id, usuario.getId())
                        .orElseThrow(() -> new RuntimeException("Medio de pago no encontrado"));

                medioPago.setEliminado(true);
                medioPagoRepository.save(medioPago);
        }

        private MedioPagoResumen toResumen(MedioPago medioPago) {
                String estado = Boolean.TRUE.equals(medioPago.getVerificado()) ? "verificado" : "pendiente";
                return new MedioPagoResumen(
                        medioPago.getId(),
                        medioPago.getTipo(),
                        medioPago.getDescripcion(),
                        medioPago.getVerificado(),
                        estado);
        }
}