package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoCobro;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.mensajeria.CobroSolicitado;
import com.daii.backdaii.mensajeria.Colas;
import com.daii.backdaii.mensajeria.FirmaMensajes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The charge queue end to end on a real RabbitMQ: same broker configuration as production
 * (docker/rabbitmq/rabbitmq.conf: TLS only, plain AMQP off) and the app connecting with amqps and
 * its own account. The certificate is created for the test run.
 */
@TestPropertySource(properties = {
        "app.mensajeria.habilitada=true",
        "app.mensajeria.firma-secreto=pruebas-integracion-firma-Ua6Lm3Vq9Bt2Hx8Pc4",
        "app.pasarela.demora-ms=0",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.ssl.bundle=rabbitmq"
})
class ConsumidorCobrosIntegracionTest extends IntegracionBase {

    private static final String USUARIO = "planta-libre-api";
    private static final String CLAVE = "clave-del-broker-de-pruebas";
    private static final Path CERTIFICADOS = certificados();

    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13-management")
            .withEnv("RABBITMQ_DEFAULT_USER", USUARIO)
            .withEnv("RABBITMQ_DEFAULT_PASS", CLAVE)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/rabbitmq/rabbitmq.conf"), 0644),
                    "/etc/rabbitmq/conf.d/90-planta-libre.conf")
            .withCopyFileToContainer(MountableFile.forHostPath(CERTIFICADOS.resolve("ca.pem"), 0644), "/etc/rabbitmq/certs/ca.pem")
            .withCopyFileToContainer(MountableFile.forHostPath(CERTIFICADOS.resolve("servidor.pem"), 0644), "/etc/rabbitmq/certs/servidor.pem")
            .withCopyFileToContainer(MountableFile.forHostPath(CERTIFICADOS.resolve("servidor.key"), 0644), "/etc/rabbitmq/certs/servidor.key")
            .withExposedPorts(5671)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.addresses", () -> "amqps://" + USUARIO + ":" + CLAVE + "@"
                + RABBIT.getHost() + ":" + RABBIT.getMappedPort(5671));
        registro.add("spring.ssl.bundle.pem.rabbitmq.truststore.certificate",
                () -> "file:" + CERTIFICADOS.resolve("ca.pem").toAbsolutePath());
    }

    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private FirmaMensajes firma;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private EspacioRepository espacios;
    @Autowired
    private MedioPagoRepository medios;
    @Autowired
    private CobroRepository cobros;
    @Autowired
    private Clock reloj;

    private Escenario escenario;
    private Usuario cliente;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        cliente = escenario.cliente();
    }

    @Test
    void unPedidoFirmadoCobraYConfirmaLaReserva() {
        escenario.tarjeta(cliente, "4242");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);

        publicar(reserva.getId(), UUID.randomUUID().toString(), true);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CONFIRMADA));
        assertThat(cobros.findByReservaId(reserva.getId())).get()
                .satisfies(cobro -> assertThat(cobro.getEstado()).isEqualTo(EstadoCobro.APROBADO));
    }

    @Test
    void conLaTarjetaRechazadaLaReservaSeCancela() {
        escenario.tarjeta(cliente, "0002");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);

        publicar(reserva.getId(), UUID.randomUUID().toString(), true);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CANCELADA));
        assertThat(cobros.findByReservaId(reserva.getId())).get()
                .satisfies(cobro -> assertThat(cobro.getEstado()).isEqualTo(EstadoCobro.RECHAZADO));
    }

    @Test
    void unMensajeDuplicadoSeProcesaUnaSolaVez() {
        escenario.tarjeta(cliente, "4242");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);
        String id = UUID.randomUUID().toString();

        publicar(reserva.getId(), id, true);
        publicar(reserva.getId(), id, true);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CONFIRMADA));
        // Gives the redelivered copy time to arrive: it must not add a second charge.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cobros.findByReservaIdIn(java.util.List.of(reserva.getId()))).hasSize(1));
    }

    @Test
    void unMensajeConFirmaInvalidaVaALaDlqSinCobrar() {
        escenario.tarjeta(cliente, "4242");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);
        String id = UUID.randomUUID().toString();

        publicar(reserva.getId(), id, false);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Message enDlq = rabbit.receive(Colas.COBROS_DLQ, 500);
            assertThat(enDlq).isNotNull();
            assertThat(enDlq.getMessageProperties().getMessageId()).isEqualTo(id);
        });
        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(cobros.existsByReservaId(reserva.getId())).isFalse();
    }

    private void publicar(Long reservaId, String mensajeId, boolean firmado) {
        rabbit.convertAndSend(Colas.EXCHANGE_RESERVAS, Colas.COBRO_SOLICITADO,
                new CobroSolicitado(reservaId, Instant.now(reloj)), mensaje -> {
                    mensaje.getMessageProperties().setMessageId(mensajeId);
                    mensaje.getMessageProperties().setHeader(FirmaMensajes.CABECERA,
                            firmado ? firma.firmar(mensajeId, mensaje.getBody()) : "firma-falsa");
                    return mensaje;
                });
    }

    /** Self-signed certificate for localhost, written as PEM files for the broker and the app's truststore. */
    private static Path certificados() {
        try {
            Path dir = Files.createTempDirectory("rabbitmq-tls");
            Path almacen = dir.resolve("servidor.p12");
            char[] clave = "solo-para-esta-prueba".toCharArray();
            Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair", "-alias", "rabbitmq", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                    "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                    "-storetype", "PKCS12", "-keystore", almacen.toString(),
                    "-storepass", new String(clave), "-keypass", new String(clave))
                    .redirectErrorStream(true).start();
            String salida = new String(keytool.getInputStream().readAllBytes());
            if (keytool.waitFor() != 0) {
                throw new IllegalStateException("keytool falló: " + salida);
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(almacen)) {
                ks.load(in, clave);
            }
            PrivateKey llave = (PrivateKey) ks.getKey("rabbitmq", clave);
            Certificate certificado = ks.getCertificate("rabbitmq");
            String pem = pem("CERTIFICATE", certificado.getEncoded());
            Files.writeString(dir.resolve("servidor.pem"), pem);
            Files.writeString(dir.resolve("ca.pem"), pem);
            Files.writeString(dir.resolve("servidor.key"), pem("PRIVATE KEY", llave.getEncoded()));
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el certificado del broker de pruebas", e);
        }
    }

    private static String pem(String tipo, byte[] contenido) {
        return "-----BEGIN " + tipo + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(contenido)
                + "\n-----END " + tipo + "-----\n";
    }
}
