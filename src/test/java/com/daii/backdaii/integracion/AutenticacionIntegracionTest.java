package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.EmailConfirmationToken;
import com.daii.backdaii.domain.entity.PasswordResetToken;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoUsuario;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.EmailConfirmationTokenRepository;
import com.daii.backdaii.domain.repository.PasswordResetTokenRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.security.SecureTokens;
import com.daii.backdaii.security.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-up, email confirmation, sign-in with a real JWT, session renewal, password changes and the
 * administrator's second factor, through the API as the front uses it.
 */
class AutenticacionIntegracionTest extends IntegracionBase {

    private static final String CLAVE = "Planta-Libre-Prueba-7392";
    private static final String CLAVE_NUEVA = "Otra-Clave-Distinta-4815";
    private static final String COOKIE = "pl_refresh";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private EmailConfirmationTokenRepository confirmaciones;
    @Autowired
    private PasswordResetTokenRepository recuperaciones;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private Clock reloj;

    @Test
    void registroConfirmacionEInicioDeSesion() throws Exception {
        String email = emailNuevo();

        enviar("/api/auth/register", Map.of("nombre", "Ana Prueba", "email", email, "telefono", "+54 11 5555 0000",
                "password", CLAVE, "rol", "CLIENTE", "medioPago", Map.of("titular", "ANA PRUEBA",
                        "numeroTarjeta", "4111 1111 1111 1111", "vencimiento", "12/35", "codigoSeguridad", "123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        enviar("/api/auth/register", Map.of("nombre", "Ana Prueba", "email", email, "telefono", "+54 11 5555 0000",
                "password", CLAVE, "rol", "CLIENTE"))
                .andExpect(status().isConflict());
        enviar("/api/auth/email-disponible", Map.of("email", email)).andExpect(jsonPath("$.disponible").value(false));
        enviar("/api/auth/email-disponible", Map.of("email", emailNuevo())).andExpect(jsonPath("$.disponible").value(true));

        enviar("/api/auth/login", Map.of("email", email, "password", CLAVE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NO_CONFIRMADO"));
        enviar("/api/auth/reenviar-confirmacion", Map.of("email", email)).andExpect(status().isOk());

        Usuario usuario = usuarios.findByEmail(email).orElseThrow();
        String link = tokenDeConfirmacion(usuario);
        enviar("/api/auth/confirmar-email", Map.of("token", link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        enviar("/api/auth/confirmar-email", Map.of("token", link))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LINK_USADO"));
        enviar("/api/auth/confirmar-email", Map.of("token", "no-existe"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LINK_INVALIDO"));
        assertThat(usuarios.findByEmail(email).orElseThrow().getEstado()).isEqualTo(EstadoUsuario.ACTIVO);

        MvcResult login = enviar("/api/auth/login", Map.of("email", email.toUpperCase(), "password", CLAVE))
                .andExpect(status().isOk()).andReturn();
        String token = token(login);
        assertThat(login.getResponse().getCookie(COOKIE)).isNotNull();

        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.telefono").value("+54 11 5555 0000"));
        mvc.perform(patch("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"+54 11 4444 1111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("+54 11 4444 1111"));
    }

    @Test
    void unaClaveIncorrectaNoInicia() throws Exception {
        Usuario usuario = activo();

        enviar("/api/auth/login", Map.of("email", usuario.getEmail(), "password", "Incorrecta-123"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laSesionSeRenuevaYUnRefreshReusadoSeRechaza() throws Exception {
        Usuario usuario = activo();
        MvcResult login = enviar("/api/auth/login", Map.of("email", usuario.getEmail(), "password", CLAVE)).andReturn();
        Cookie primero = login.getResponse().getCookie(COOKIE);

        MvcResult renovado = mvc.perform(post("/api/auth/refresh").cookie(primero))
                .andExpect(status().isOk()).andReturn();
        assertThat(token(renovado)).isNotBlank();
        assertThat(renovado.getResponse().getCookie(COOKIE).getValue()).isNotEqualTo(primero.getValue());

        mvc.perform(post("/api/auth/refresh").cookie(primero)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void cerrarSesionInvalidaElToken() throws Exception {
        Usuario usuario = activo();
        MvcResult login = enviar("/api/auth/login", Map.of("email", usuario.getEmail(), "password", CLAVE)).andReturn();
        String token = token(login);

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .cookie(login.getResponse().getCookie(COOKIE)))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarLaClaveCierraLasDemasSesiones() throws Exception {
        Usuario usuario = activo();
        String viejo = token(enviar("/api/auth/login", Map.of("email", usuario.getEmail(), "password", CLAVE)).andReturn());
        // The change is stored with second precision: it has to fall in a later second than the token.
        Thread.sleep(1100);

        mvc.perform(post("/api/auth/change-password").with(como(usuario.getEmail(), Rol.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("passwordActual", "Incorrecta-123", "passwordNueva", CLAVE_NUEVA))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/change-password").with(como(usuario.getEmail(), Rol.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("passwordActual", CLAVE, "passwordNueva", CLAVE_NUEVA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // The token issued before the change no longer opens anything.
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + viejo))
                .andExpect(status().isUnauthorized());
        enviar("/api/auth/login", Map.of("email", usuario.getEmail(), "password", CLAVE_NUEVA)).andExpect(status().isOk());
    }

    @Test
    void recuperarLaClaveConElLinkDelMail() throws Exception {
        Usuario usuario = activo();

        enviar("/api/auth/forgot-password", Map.of("email", usuario.getEmail())).andExpect(status().isOk());
        enviar("/api/auth/forgot-password", Map.of("email", emailNuevo())).andExpect(status().isOk());
        assertThat(recuperaciones.findAll()).anySatisfy(t -> assertThat(t.getUsuario().getId()).isEqualTo(usuario.getId()));

        String crudo = SecureTokens.generar();
        recuperaciones.deleteAll(recuperaciones.findAll().stream().filter(t -> t.getUsuario().getId().equals(usuario.getId())).toList());
        recuperaciones.save(PasswordResetToken.builder().usuario(usuario).tokenHash(SecureTokens.hash(crudo))
                .expiresAt(LocalDateTime.now().plusMinutes(30)).build());

        enviar("/api/auth/reset-password", Map.of("token", crudo, "nuevaPassword", CLAVE_NUEVA)).andExpect(status().isOk());
        enviar("/api/auth/reset-password", Map.of("token", crudo, "nuevaPassword", CLAVE_NUEVA)).andExpect(status().isBadRequest());
        enviar("/api/auth/reset-password", Map.of("token", "no-existe", "nuevaPassword", CLAVE_NUEVA)).andExpect(status().isBadRequest());
        enviar("/api/auth/login", Map.of("email", usuario.getEmail(), "password", CLAVE_NUEVA)).andExpect(status().isOk());
    }

    @Test
    void elAdminEntraConSegundoFactor() throws Exception {
        Usuario admin = usuarios.findByEmail(Escenario.ADMIN).orElseThrow();
        admin.setPassword(encoder.encode(CLAVE));
        admin.setSegundoFactorActivo(false);
        admin.setSegundoFactorUltimoPaso(null);
        usuarios.save(admin);

        JsonNode paso1 = json.readTree(enviar("/api/auth/login", Map.of("email", Escenario.ADMIN, "password", CLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segundoFactorRequerido").value(true))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        String desafio = paso1.get("desafio").asText();
        String secreto = paso1.get("altaSegundoFactor").get("secreto").asText();

        enviar("/api/auth/login/segundo-factor", Map.of("desafio", desafio, "codigo", "000000".equals(codigo(secreto)) ? "111111" : "000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CODIGO_INCORRECTO"));
        enviar("/api/auth/login/segundo-factor", Map.of("desafio", "no-es-un-desafio", "codigo", codigo(secreto)))
                .andExpect(status().isUnauthorized());
        String token = token(enviar("/api/auth/login/segundo-factor", Map.of("desafio", desafio, "codigo", codigo(secreto)))
                .andExpect(status().isOk()).andReturn());

        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + Escenario.ADMIN + "')]").exists());
        mvc.perform(get("/api/admin/auditoria").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("evento", "LOGIN_SEGUNDO_FACTOR").param("usuario", Escenario.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty());
        assertThat(usuarios.findByEmail(Escenario.ADMIN).orElseThrow().getSegundoFactorActivo()).isTrue();
    }

    @Test
    void unClienteNoVeElListadoDeUsuariosNiLaAuditoria() throws Exception {
        Usuario usuario = activo();
        mvc.perform(get("/api/usuarios").with(como(usuario.getEmail(), Rol.CLIENTE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auditoria").with(como(usuario.getEmail(), Rol.CLIENTE))).andExpect(status().isForbidden());
    }

    private Usuario activo() {
        return usuarios.save(Usuario.builder()
                .nombre("Cliente activo")
                .email(emailNuevo())
                .password(encoder.encode(CLAVE))
                .rol(Rol.CLIENTE)
                .estado(EstadoUsuario.ACTIVO)
                .build());
    }

    private String tokenDeConfirmacion(Usuario usuario) {
        String crudo = SecureTokens.generar();
        confirmaciones.save(EmailConfirmationToken.builder().usuario(usuario).tokenHash(SecureTokens.hash(crudo)).build());
        return crudo;
    }

    private String codigo(String secreto) {
        return Totp.codigo(secreto, Totp.paso(reloj.instant()));
    }

    private ResultActions enviar(String ruta, Map<String, ?> cuerpo) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private String token(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
    }

    private static String emailNuevo() {
        return "persona-" + UUID.randomUUID() + "@pruebas.plantalibre.local";
    }
}
