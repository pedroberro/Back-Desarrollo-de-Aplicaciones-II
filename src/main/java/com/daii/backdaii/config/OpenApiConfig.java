package com.daii.backdaii.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * The published API contract (/v3/api-docs). Three things springdoc cannot infer:
 * <ul>
 *   <li>Dates and times (and opening hours) are the building's local time, without offset ("2026-11-03T10:00:00"), not
 *       RFC 3339 date-times.</li>
 *   <li>Optional fields travel as null.</li>
 *   <li>Every endpoint can answer 400, 403, 404 and 409 with an error in Problem Details format (RFC 9457).</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String ERROR = "Problema";
    private static final Map<String, String> ERRORES = Map.of(
            "400", "Datos inválidos",
            "403", "Sin permiso para la acción",
            "404", "No existe o no es del usuario",
            "409", "Choca con el estado actual del recurso");

    static {
        SpringDocUtils.getConfig().replaceWithSchema(LocalDateTime.class,
                new StringSchema().pattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?$")
                        .example("2026-11-03T10:00:00")
                        .description("Hora local del edificio, sin zona horaria"));
        SpringDocUtils.getConfig().replaceWithSchema(LocalTime.class,
                new StringSchema().pattern("^\\d{2}:\\d{2}(:\\d{2})?$").example("08:00").description("Hora local, HH:mm"));
    }

    @Bean
    OpenAPI contratoPlantaLibre() {
        return new OpenAPI()
                .info(new Info().title("Planta Libre API").version("1.0")
                        .description("Reserva de espacios de trabajo. Los endpoints protegidos esperan el token de acceso "
                                + "en el header Authorization: Bearer."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    @SuppressWarnings("rawtypes")
    OpenApiCustomizer erroresYNulos() {
        return openApi -> {
            Map<String, Schema> esquemas = openApi.getComponents().getSchemas();
            if (esquemas != null) {
                esquemas.put(ERROR, problema());
                esquemas.values().forEach(OpenApiConfig::opcionalesNulos);
            }
            openApi.getPaths().forEach((ruta, item) -> item.readOperations().forEach(operacion ->
                    ERRORES.forEach((codigo, descripcion) -> {
                        if (!ruta.equals("/api/health") && !operacion.getResponses().containsKey(codigo)) {
                            operacion.getResponses().addApiResponse(codigo, new ApiResponse().description(descripcion)
                                    .content(new Content().addMediaType("application/problem+json",
                                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR)))));
                        }
                    })));
        };
    }

    /** RFC 9457 members plus the extensions the front reads (message, code, errors and each error's extras). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema problema() {
        return new ObjectSchema()
                .description("Error en formato Problem Details (RFC 9457), servido como application/problem+json")
                .addProperty("type", new StringSchema().example("urn:planta-libre:problema:TRANSICION_INVALIDA"))
                .addProperty("title", new StringSchema().example("Conflicto con el estado actual"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema().example("Una reserva confirmada no puede pasar a pendiente"))
                .addProperty("instance", new StringSchema().example("/api/admin/reservas/42"))
                .addProperty("message", new StringSchema().description("Igual a detail; lo lee el front"))
                .addProperty("code", new StringSchema().nullable(true).description("Código del caso, si el cliente lo trata aparte"))
                .addProperty("errors", new ObjectSchema().nullable(true).additionalProperties(new StringSchema())
                        .description("Campo inválido y su mensaje, en los errores de validación"))
                .required(java.util.List.of("type", "title", "status", "detail"));
    }

    /** Jackson writes absent values as null: every property not marked required accepts it. */
    @SuppressWarnings("rawtypes")
    private static void opcionalesNulos(Schema esquema) {
        if (esquema.getProperties() == null) {
            return;
        }
        Map<String, Schema> propiedades = esquema.getProperties();
        propiedades.replaceAll((nombre, propiedad) -> {
            if (esquema.getRequired() != null && esquema.getRequired().contains(nombre)) {
                return propiedad;
            }
            // In OpenAPI 3.0 a $ref ignores its siblings: nullable has to wrap it.
            if (propiedad.get$ref() != null) {
                return new ComposedSchema().addAllOfItem(new Schema<>().$ref(propiedad.get$ref())).nullable(true);
            }
            return propiedad.nullable(true);
        });
    }
}
