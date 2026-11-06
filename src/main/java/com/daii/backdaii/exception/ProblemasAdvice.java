package com.daii.backdaii.exception;

import com.daii.backdaii.dto.reserva.ReservaDTOs.FranjaTomada;
import com.daii.backdaii.dto.reserva.ReservaDTOs.ReservasAfectadas;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

/**
 * Turns every error body of GlobalExceptionHandler into Problem Details (RFC 9457) on its way out,
 * so the handlers keep building their usual bodies.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ProblemasAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper json;

    @Override
    public boolean supports(MethodParameter tipo, Class<? extends HttpMessageConverter<?>> conversor) {
        Class<?> clase = tipo.getParameterType();
        return clase.equals(org.springframework.http.ResponseEntity.class) || esError(clase);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object cuerpo, MethodParameter tipo, MediaType medio,
                                  Class<? extends HttpMessageConverter<?>> conversor,
                                  ServerHttpRequest pedido, ServerHttpResponse respuesta) {
        if (cuerpo == null || !esError(cuerpo.getClass()) || !(respuesta instanceof ServletServerHttpResponse servlet)) {
            return cuerpo;
        }
        respuesta.getHeaders().setContentType(Problemas.TIPO);
        Map<String, Object> campos = json.convertValue(cuerpo, Map.class);
        return Problemas.cuerpo(servlet.getServletResponse().getStatus(), pedido.getURI().getPath(), campos);
    }

    private static boolean esError(Class<?> clase) {
        return clase.equals(ErrorResponse.class) || clase.equals(FranjaTomada.class) || clase.equals(ReservasAfectadas.class);
    }
}
