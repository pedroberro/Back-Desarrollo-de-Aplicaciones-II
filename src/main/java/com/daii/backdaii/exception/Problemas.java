package com.daii.backdaii.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error answers in Problem Details format (RFC 9457): type, title, status, detail and instance,
 * served as application/problem+json. The fields the front already reads (message, code, errors
 * and the extras of each error, such as alternativas) stay as extension members.
 */
public final class Problemas {

    public static final MediaType TIPO = MediaType.APPLICATION_PROBLEM_JSON;
    static final String PREFIJO_TIPO = "urn:planta-libre:problema:";

    private static final Map<Integer, String> TITULOS = Map.ofEntries(
            Map.entry(400, "Solicitud inválida"),
            Map.entry(401, "No autenticado"),
            Map.entry(403, "Sin permiso"),
            Map.entry(404, "No encontrado"),
            Map.entry(405, "Método no permitido"),
            Map.entry(409, "Conflicto con el estado actual"),
            Map.entry(415, "Tipo de contenido no soportado"),
            Map.entry(429, "Demasiadas solicitudes"),
            Map.entry(500, "Error inesperado"));

    private Problemas() {
    }

    /** The body of an error answer: the RFC 9457 members first, then everything the error carried. */
    public static Map<String, Object> cuerpo(int status, String instancia, Map<String, Object> error) {
        Map<String, Object> problema = new LinkedHashMap<>();
        Object codigo = error.get("code");
        problema.put("type", codigo != null ? PREFIJO_TIPO + codigo : "about:blank");
        problema.put("title", TITULOS.getOrDefault(status, HttpStatus.valueOf(status).getReasonPhrase()));
        problema.put("status", status);
        problema.put("detail", error.get("message"));
        problema.put("instance", instancia);
        error.forEach((clave, valor) -> {
            if (valor != null) {
                problema.putIfAbsent(clave, valor);
            }
        });
        return problema;
    }

    /** For the filters that answer before any controller (authentication, rate limit). */
    public static void escribir(HttpServletResponse respuesta, ObjectMapper json, HttpStatus status, String instancia,
                                ErrorResponse error) throws IOException {
        respuesta.setStatus(status.value());
        respuesta.setContentType(TIPO.toString());
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        @SuppressWarnings("unchecked")
        Map<String, Object> campos = json.convertValue(error, Map.class);
        json.writeValue(respuesta.getWriter(), cuerpo(status.value(), instancia, campos));
    }
}
