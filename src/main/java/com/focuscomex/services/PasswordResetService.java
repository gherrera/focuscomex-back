package com.focuscomex.services;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.focuscomex.exceptions.ComexException;
import com.focuscomex.model.PasswordResetToken;
import com.focuscomex.model.User;
import com.focuscomex.repository.PasswordResetTokenRepository;
import com.focuscomex.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailOrchestratorService mailService;

    @Value("${api.frontend-url}")
    private String frontendUrl;

    @Value("${api.reset-password.token-expiration}")
    private int tokenExpirationSeconds;

    @Value("${api.reset-password.path}")
    private String resetPath;

    @Transactional
    public void createPasswordResetTokenForUser(String email) {
        User user = userRepository.findByUsername(email)
            .orElseThrow(() -> new ComexException("Usuario no encontrado"));

        // Invalidar todos los tokens activos anteriores
        List<PasswordResetToken> activeTokens = tokenRepository.findAllByUserAndUsedFalseAndExpiryDateAfter(user, new Date());
        if (!activeTokens.isEmpty()) {
            activeTokens.forEach(token -> {
                token.setUsed(true);
                tokenRepository.save(token);
            });
            log.debug("Invalidados {} tokens activos anteriores para el usuario: {}", activeTokens.size(), email);
        }

        // Crear nuevo token
        String token = UUID.randomUUID().toString();
        PasswordResetToken myToken = new PasswordResetToken();
        myToken.setToken(token);
        myToken.setUser(user);
        myToken.setExpiryDate(calculateExpiryDate());
        tokenRepository.save(myToken);

        // Enviar correo
        String resetUrl = String.format("%s%s/%s", frontendUrl, resetPath, token);
        Map<String, Object> model = new HashMap<>();
        model.put("nombre", user.getName());
        model.put("resetUrl", resetUrl);

        try {
            mailService.sendMailWithTemplate(
                user.getUsername(),
                "Restauración de Contraseña",
                "olvide-clave",
                model, null, null
            );
            log.info("Correo de restablecimiento enviado a: {}", email);
        } catch (Exception e) {
            log.error("Error enviando correo de restablecimiento a: {}", email, e);
            throw new ComexException("Error enviando correo de restablecimiento");
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
            .orElseThrow(() -> new ComexException("Token inválido"));

        if (resetToken.isExpired()) {
            throw new ComexException("Token expirado");
        }

        if (resetToken.isUsed()) {
            throw new ComexException("Token ya utilizado");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Contraseña restablecida exitosamente para: {}", user.getUsername());
    }

    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        try {
            PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ComexException("Token inválido"));

            if (resetToken.isExpired()) {
                log.debug("Token expirado: {}", token);
                return false;
            }

            if (resetToken.isUsed()) {
                log.debug("Token ya utilizado: {}", token);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.debug("Error validando token: {}", token, e);
            return false;
        }
    }

    private LocalDateTime calculateExpiryDate() {
        return LocalDateTime.now().plusSeconds(tokenExpirationSeconds);
    }
}