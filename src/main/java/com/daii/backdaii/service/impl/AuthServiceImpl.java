package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.entity.EmailConfirmationToken;
import com.daii.backdaii.domain.entity.PasswordResetToken;
import com.daii.backdaii.domain.entity.TokenBlacklist;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoUsuario;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.EmailConfirmationTokenRepository;
import com.daii.backdaii.domain.repository.PasswordResetTokenRepository;
import com.daii.backdaii.domain.repository.TokenBlacklistRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.dto.auth.AuthResponse;
import com.daii.backdaii.dto.auth.ChangePasswordRequest;
import com.daii.backdaii.dto.auth.LoginRequest;
import com.daii.backdaii.dto.auth.RegistroRequest;
import com.daii.backdaii.dto.auth.ResetPasswordRequest;
import com.daii.backdaii.dto.auth.ResultadoAuth;
import com.daii.backdaii.dto.auth.SegundoFactorRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;
import com.daii.backdaii.exception.BadRequestException;
import com.daii.backdaii.exception.ConflictException;
import com.daii.backdaii.exception.EmailNoConfirmadoException;
import com.daii.backdaii.exception.NoAutorizadoException;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.mapper.UsuarioMapper;
import com.daii.backdaii.security.JwtTokenProvider;
import com.daii.backdaii.security.PoliticaContrasenias;
import com.daii.backdaii.security.SecureTokens;
import com.daii.backdaii.security.Sesiones;
import com.daii.backdaii.security.Totp;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.service.AuthService;
import com.daii.backdaii.service.EmailService;
import com.daii.backdaii.service.MedioPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UsuarioMapper usuarioMapper;
    private final MedioPagoService medioPagoService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final EmailConfirmationTokenRepository emailConfirmationTokenRepository;
    private final Sesiones sesiones;
    private final PoliticaContrasenias politicaContrasenias;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    @Value("${app.frontend.base-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public UsuarioDTO register(RegistroRequest request) {
        String email = normalizarEmail(request.getEmail());
        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflictException("El email ya está registrado");
        }
        politicaContrasenias.validar(request.getPassword());

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .email(email)
                .telefono(request.getTelefono())
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(Rol.CLIENTE)
                .estado(EstadoUsuario.PENDIENTE)
                .build();

        usuario = usuarioRepository.save(usuario);

        if (request.getMedioPago() != null) {
            medioPagoService.agregarTarjeta(usuario.getEmail(), request.getMedioPago());
        }

        // Last step, so a failure above (e.g. an expired card) does not leave a mail already sent.
        enviarConfirmacion(usuario);
        auditoria.registrar(EventoAuditoria.REGISTRO, ResultadoAuditoria.EXITO, email, null);
        return usuarioMapper.toDTO(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailDisponible(String email) {
        return !usuarioRepository.existsByEmail(normalizarEmail(email));
    }

    @Override
    @Transactional
    public ResultadoAuth login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizarEmail(request.getEmail()), request.getPassword())
        );

        Usuario usuario = usuarioRepository.findByEmail(normalizarEmail(request.getEmail()))
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        // Checked after the password, so a wrong password never reveals that the account is pending.
        if (usuario.getEstado() == EstadoUsuario.PENDIENTE) {
            throw new EmailNoConfirmadoException();
        }

        // Administrators never get a session with the password alone (SEC-03).
        if (usuario.getRol() == Rol.ADMIN) {
            AuthResponse.AltaSegundoFactor alta = null;
            if (!Boolean.TRUE.equals(usuario.getSegundoFactorActivo())) {
                // First sign-in, or a secret never confirmed: a fresh one, shown once.
                usuario.setSegundoFactorSecreto(Totp.generarSecreto());
                usuarioRepository.save(usuario);
                alta = new AuthResponse.AltaSegundoFactor(usuario.getSegundoFactorSecreto(),
                        Totp.uri("Planta Libre", usuario.getEmail(), usuario.getSegundoFactorSecreto()));
            }
            return new ResultadoAuth(AuthResponse.builder()
                    .segundoFactorRequerido(true)
                    .desafio(jwtTokenProvider.generarDesafio(usuario.getEmail()))
                    .altaSegundoFactor(alta)
                    .build(), null);
        }
        return iniciarSesion(usuario);
    }

    @Override
    @Transactional(noRollbackFor = NoAutorizadoException.class)
    public ResultadoAuth segundoFactor(SegundoFactorRequest request) {
        String email = jwtTokenProvider.leerDesafio(request.desafio())
                .orElseThrow(() -> new NoAutorizadoException("El inicio de sesión venció. Ingresá de nuevo tu contraseña.", "DESAFIO_INVALIDO"));
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NoAutorizadoException("El inicio de sesión venció. Ingresá de nuevo tu contraseña.", "DESAFIO_INVALIDO"));
        OptionalLong paso = Totp.verificar(usuario.getSegundoFactorSecreto(), request.codigo(), reloj.instant(),
                usuario.getSegundoFactorUltimoPaso());
        if (paso.isEmpty()) {
            auditoria.registrar(EventoAuditoria.LOGIN_SEGUNDO_FACTOR, ResultadoAuditoria.FALLO, email, "Código incorrecto");
            throw new NoAutorizadoException("El código no es correcto. Revisá la hora del teléfono y probá con el código nuevo.",
                    "CODIGO_INCORRECTO");
        }
        boolean alta = !Boolean.TRUE.equals(usuario.getSegundoFactorActivo());
        usuario.setSegundoFactorActivo(true);
        usuario.setSegundoFactorUltimoPaso(paso.getAsLong());
        usuarioRepository.save(usuario);
        if (alta) {
            auditoria.registrar(EventoAuditoria.ALTA_SEGUNDO_FACTOR, ResultadoAuditoria.EXITO, email, null);
        }
        auditoria.registrar(EventoAuditoria.LOGIN_SEGUNDO_FACTOR, ResultadoAuditoria.EXITO, email, null);
        return iniciarSesion(usuario);
    }

    @Override
    @Transactional
    public ResultadoAuth refresh(String refresh) {
        Sesiones.Emitido emitido = sesiones.rotar(refresh);
        Usuario usuario = usuarioRepository.findById(emitido.usuario().getId()).orElseThrow();
        return sesion(usuario, emitido);
    }

    @Override
    @Transactional
    public ResultadoAuth confirmarEmail(String rawToken) {
        EmailConfirmationToken confirmacion = emailConfirmationTokenRepository.findByTokenHash(SecureTokens.hash(rawToken))
                .orElseThrow(() -> new BadRequestException("El link de confirmación no es válido.", "LINK_INVALIDO"));

        // Links never expire but work once: signing in again needs the password.
        if (confirmacion.getUsedAt() != null) {
            throw new BadRequestException("Este link ya se usó. Tu email está confirmado: ingresá con tu contraseña.", "LINK_USADO");
        }

        Usuario usuario = confirmacion.getUsuario();
        if (usuario.getEstado() == EstadoUsuario.PENDIENTE) {
            usuario.setEstado(EstadoUsuario.ACTIVO);
            usuarioRepository.save(usuario);
        }
        confirmacion.setUsedAt(LocalDateTime.now());
        emailConfirmationTokenRepository.save(confirmacion);

        return iniciarSesion(usuario);
    }

    @Override
    @Transactional
    public void reenviarConfirmacion(String email) {
        usuarioRepository.findByEmail(normalizarEmail(email))
                .filter(usuario -> usuario.getEstado() == EstadoUsuario.PENDIENTE)
                .ifPresent(this::enviarConfirmacion);
    }

    private void enviarConfirmacion(Usuario usuario) {
        // Only the latest link is valid, so an old mail cannot be used after asking for a new one.
        emailConfirmationTokenRepository.deleteByUsuarioIdAndUsedAtIsNull(usuario.getId());
        String rawToken = SecureTokens.generar();
        emailConfirmationTokenRepository.save(EmailConfirmationToken.builder()
                .usuario(usuario)
                .tokenHash(SecureTokens.hash(rawToken))
                .build());
        emailService.sendEmailConfirmation(usuario.getEmail(), usuario.getNombre(),
                frontendUrl + "/confirmar-email?token=" + rawToken);
    }

    private ResultadoAuth iniciarSesion(Usuario usuario) {
        return sesion(usuario, sesiones.abrir(usuario));
    }

    private ResultadoAuth sesion(Usuario usuario, Sesiones.Emitido emitido) {
        String token = jwtTokenProvider.generateToken(
                usuario.getEmail(), usuario.getId(), usuario.getRol().name(), emitido.familia());
        return new ResultadoAuth(new AuthResponse(token, usuarioMapper.toDTO(usuario)), emitido.crudo());
    }

    @Override
    @Transactional
    public ResultadoAuth changePassword(String email, ChangePasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPasswordActual(), usuario.getPassword())) {
            auditoria.registrar(EventoAuditoria.CAMBIO_PASSWORD, ResultadoAuditoria.FALLO, email, "Contraseña actual incorrecta");
            throw new BadRequestException("La contraseña actual es incorrecta");
        }
        politicaContrasenias.validar(request.getPasswordNueva());

        usuario.setPassword(passwordEncoder.encode(request.getPasswordNueva()));
        usuario.setPasswordChangedAt(LocalDateTime.now().withNano(0));
        usuarioRepository.save(usuario);
        sesiones.cerrarTodas(usuario);
        auditoria.registrar(EventoAuditoria.CAMBIO_PASSWORD, ResultadoAuditoria.EXITO, email, null);
        return iniciarSesion(usuario);
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        Optional<Usuario> optional = usuarioRepository.findByEmail(normalizarEmail(email));
        if (optional.isEmpty()) {
            return;
        }

        Usuario usuario = optional.get();
        passwordResetTokenRepository.deleteByUsuarioId(usuario.getId());

        String rawToken = SecureTokens.generar();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .usuario(usuario)
                .tokenHash(SecureTokens.hash(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(usuario.getEmail(), resetLink);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(SecureTokens.hash(request.getToken()))
                .orElseThrow(() -> new BadRequestException("Token de recuperación inválido o vencido"));

        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token de recuperación inválido o vencido");
        }
        politicaContrasenias.validar(request.getNuevaPassword());

        Usuario usuario = resetToken.getUsuario();
        usuario.setPassword(passwordEncoder.encode(request.getNuevaPassword()));
        usuario.setPasswordChangedAt(LocalDateTime.now().withNano(0));
        usuarioRepository.save(usuario);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);
        sesiones.cerrarTodas(usuario);
        auditoria.registrar(EventoAuditoria.RECUPERACION_PASSWORD, ResultadoAuditoria.EXITO, usuario.getEmail(), null);
    }

    // Emails are stored lowercase so "Ana@x.com" and "ana@x.com" are the same account.
    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void logout(String authorizationHeader, String refresh) {
        String token = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7) : authorizationHeader;
        String email = sesiones.duenio(refresh);
        jwtTokenProvider.leerAcceso(token).ifPresent(claims -> {
            if (!tokenBlacklistRepository.existsByToken(token)) {
                tokenBlacklistRepository.save(TokenBlacklist.builder()
                        .token(token)
                        .expiresAt(JwtTokenProvider.vence(claims))
                        .build());
            }
        });
        sesiones.cerrar(refresh);
        auditoria.registrar(EventoAuditoria.LOGOUT, ResultadoAuditoria.EXITO, email, null);
    }
}
