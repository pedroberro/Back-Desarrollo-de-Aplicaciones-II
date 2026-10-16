package com.daii.backdaii.security;

import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.mensajeria.FirmaMensajes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The pure pieces of the security criteria: secrets, TOTP, field encryption, signatures, passwords. */
class SeguridadTest {

    private static final String SECRETO = "kQ9vT2mXr7LpZ4bN8wYc1HsF6jD3uGeA";

    // ---- SEC-10 ----------------------------------------------------------------------------------

    @Test
    void sinSecretoOConUnoDebilLaAplicacionNoArranca() {
        assertThatThrownBy(() -> Secretos.requerido("JWT_SECRET", null)).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> Secretos.requerido("JWT_SECRET", "corto")).hasMessageContaining("corta");
        assertThatThrownBy(() -> Secretos.requerido("JWT_SECRET", "myDefaultSecretKeyForDevelopmentOnly2024"))
                .hasMessageContaining("ejemplo");
        assertThatThrownBy(() -> Secretos.requerido("JWT_SECRET", "a".repeat(40))).hasMessageContaining("aleatorio");
        assertThat(Secretos.requerido("JWT_SECRET", SECRETO)).isEqualTo(SECRETO);
    }

    // ---- SEC-03 ----------------------------------------------------------------------------------

    @Test
    void totpCoincideConLosVectoresDelRfc6238() {
        // RFC 6238, apéndice B: secreto ASCII "12345678901234567890", SHA-1, últimos 6 dígitos.
        String secreto = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertThat(Totp.codigo(secreto, Totp.paso(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.codigo(secreto, Totp.paso(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.codigo(secreto, Totp.paso(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void totpAceptaUnPasoDeDesfasajePeroNoReutilizarElCodigo() {
        String secreto = Totp.generarSecreto();
        Instant ahora = Instant.ofEpochSecond(1_800_000_000L);
        long paso = Totp.paso(ahora);
        assertThat(Totp.verificar(secreto, Totp.codigo(secreto, paso - 1), ahora, null)).hasValue(paso - 1);
        assertThat(Totp.verificar(secreto, Totp.codigo(secreto, paso), ahora, null)).hasValue(paso);
        assertThat(Totp.verificar(secreto, Totp.codigo(secreto, paso), ahora, paso)).isEmpty();
        assertThat(Totp.verificar(secreto, Totp.codigo(secreto, paso - 3), ahora, null)).isEmpty();
        assertThat(Totp.verificar(secreto, "12345", ahora, null)).isEmpty();
    }

    // ---- SEC-09 ----------------------------------------------------------------------------------

    @Test
    void elCampoQuedaCifradoYSeRecuperaIgual() {
        CifradoCampos cifrado = new CifradoCampos(SECRETO, "v1", "");
        String guardado = cifrado.cifrar("Ana Ruiz");
        assertThat(guardado).startsWith("enc:v1:").doesNotContain("Ana");
        assertThat(cifrado.cifrar("Ana Ruiz")).isNotEqualTo(guardado);
        assertThat(cifrado.descifrar(guardado)).isEqualTo("Ana Ruiz");
        assertThat(cifrado.descifrar("texto plano viejo")).isEqualTo("texto plano viejo");
    }

    @Test
    void conLaClaveRotadaSeLeeLoViejoYSeMarcaParaRecifrar() {
        String viejo = new CifradoCampos(SECRETO, "v1", "").cifrar("+54 11 5555 0000");
        CifradoCampos rotado = new CifradoCampos("Zp4rW8nQ2sLx6vB9mT1kY7cH3jF5gDuE", "v2", "v1=" + SECRETO);
        assertThat(rotado.descifrar(viejo)).isEqualTo("+54 11 5555 0000");
        assertThat(rotado.desactualizado(viejo)).isTrue();
        assertThat(rotado.desactualizado(rotado.cifrar("x"))).isFalse();
    }

    @Test
    void unValorAlteradoNoSeDescifra() {
        CifradoCampos cifrado = new CifradoCampos(SECRETO, "v1", "");
        String guardado = cifrado.cifrar("Ana Ruiz");
        char ultimo = guardado.charAt(guardado.length() - 3);
        String alterado = guardado.substring(0, guardado.length() - 3) + (ultimo == 'A' ? 'B' : 'A') + guardado.substring(guardado.length() - 2);
        assertThatThrownBy(() -> cifrado.descifrar(alterado)).hasMessageContaining("alterado");
    }

    // ---- SEC-08 ----------------------------------------------------------------------------------

    @Test
    void unMensajeAlteradoNoPasaLaFirma() {
        FirmaMensajes firma = new FirmaMensajes(SECRETO);
        byte[] cuerpo = "{\"reservaId\":42}".getBytes(StandardCharsets.UTF_8);
        String valida = firma.firmar("m-1", cuerpo);
        assertThat(firma.valida("m-1", cuerpo, valida)).isTrue();
        assertThat(firma.valida("m-1", "{\"reservaId\":43}".getBytes(StandardCharsets.UTF_8), valida)).isFalse();
        assertThat(firma.valida("m-2", cuerpo, valida)).isFalse();
        assertThat(firma.valida("m-1", cuerpo, null)).isFalse();
        assertThat(new FirmaMensajes("Zp4rW8nQ2sLx6vB9mT1kY7cH3jF5gDuE").valida("m-1", cuerpo, valida)).isFalse();
    }

    // ---- SEC-04 ----------------------------------------------------------------------------------

    @Test
    void unaContrasenaFiltradaSeRechaza() {
        PoliticaContrasenias sinConsultaExterna = new PoliticaContrasenias(p -> false, false);
        assertThatThrownBy(() -> sinConsultaExterna.validar("Password1")).isInstanceOf(BadRequestException.class);
        assertThatCode(() -> sinConsultaExterna.validar("Xk29vLq7aP")).doesNotThrowAnyException();

        PoliticaContrasenias conConsulta = new PoliticaContrasenias(p -> p.equals("Filtrada2024"), true);
        assertThatThrownBy(() -> conConsulta.validar("Filtrada2024")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void laConsultaDeFiltracionesSoloMandaElPrefijoDelHash() {
        // SHA-1 de "password": 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8 → se envía "5BAA6".
        assertThat(PwnedPasswordsCliente.sha1("password")).isEqualTo("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8");
    }
}
