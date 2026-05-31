package com.focuscomex.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.focuscomex.dto.JwtResponse;
import com.focuscomex.enums.UserType;
import com.focuscomex.model.User;
import com.focuscomex.repository.UserRepository;
import com.focuscomex.security.AuthenticationService;
import com.focuscomex.security.CurrentUser;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class GoogleAuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationService authenticationService;
    private final WebClient.Builder webClientBuilder;

    @Value("${google.oauth.client-id}")
    private String googleClientId;

    @Value("${google.oauth.client-secret}")
    private String googleClientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String googleRedirectUri;

    @Value("${api.frontend-url}")
    private String frontendUrl;

    @GetMapping("google")
    public void redirectToGoogle(HttpServletResponse response, HttpServletRequest request) {
        validateGoogleConfig();

        String redirectUri = computeRedirectUri(request);

        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + encode(googleClientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode("openid email profile")
                + "&access_type=offline"
                + "&prompt=consent";

        try {
            response.sendRedirect(authUrl);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo iniciar autenticación Google", e);
        }
    }

    @GetMapping("google/callback")
    @SuppressWarnings("unchecked")
    public void googleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse response,
            HttpServletRequest request) {

        if (StringUtils.hasText(error)) {
            redirectToFrontend(response, "googleError=" + encode(error));
            return;
        }

        if (!StringUtils.hasText(code)) {
            redirectToFrontend(response, "googleError=" + encode("No se recibió código de autorización"));
            return;
        }

        try {
            validateGoogleConfig();
            WebClient webClient = webClientBuilder.build();

            String redirectUri = computeRedirectUri(request);

            Map<String, Object> tokenResponse = webClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("code", code)
                            .with("client_id", googleClientId)
                            .with("client_secret", googleClientSecret)
                            .with("redirect_uri", redirectUri)
                            .with("grant_type", "authorization_code"))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String accessToken = tokenResponse != null ? (String) tokenResponse.get("access_token") : null;
            if (!StringUtils.hasText(accessToken)) {
                redirectToFrontend(response, "googleError=" + encode("No se pudo obtener access token de Google"));
                return;
            }

            Map<String, Object> userInfo = webClient.get()
                    .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String email = userInfo != null ? (String) userInfo.get("email") : null;
            String name = userInfo != null ? (String) userInfo.get("name") : null;

            if (!StringUtils.hasText(email)) {
                redirectToFrontend(response, "googleError=" + encode("No se pudo obtener email de Google"));
                return;
            }

            User user = userRepository.findByUsername(email)
                    .orElseGet(() -> createGoogleUser(email, name));

            CurrentUser currentUser = new CurrentUser(
                    user.getId(),
                    user.getUsername(),
                    user.getPassword(),
                    user.isEnabled(),
                    false,
                    120 * 60,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getType().name())));

            JwtResponse jwtResponse = authenticationService.authenticate(currentUser);
            redirectToFrontend(response, "googleToken=" + encode(jwtResponse.getToken()));
        } catch (Exception e) {
            redirectToFrontend(response, "googleError=" + encode("Error en autenticación Google"));
        }
    }

    // Compute redirect URI from request headers when an explicit property is not provided.
    private String computeRedirectUri(HttpServletRequest request) {
        if (StringUtils.hasText(googleRedirectUri)) {
            return googleRedirectUri;
        }

        // Prefer standard proxy headers
        String proto = firstHeaderValue(request, "X-Forwarded-Proto");
        String host = firstHeaderValue(request, "X-Forwarded-Host");
        String port = firstHeaderValue(request, "X-Forwarded-Port");

        // Fallback to Forwarded header (RFC 7239)
        String forwarded = request.getHeader("Forwarded");
        if (!StringUtils.hasText(proto) && StringUtils.hasText(forwarded)) {
            for (String part : forwarded.split(";|,")) {
                String p = part.trim();
                if (p.startsWith("proto=")) {
                    proto = p.substring("proto=".length());
                } else if (p.startsWith("host=")) {
                    host = p.substring("host=".length());
                }
            }
        }

        // Final fallbacks
        if (!StringUtils.hasText(proto)) {
            proto = request.getScheme();
        }
        if (!StringUtils.hasText(host)) {
            host = request.getHeader("Host");
        }
        if (!StringUtils.hasText(host)) {
            host = request.getServerName();
            port = String.valueOf(request.getServerPort());
        }

        // If host already contains port, don't add port
        String hostWithPort = host;
        if (!host.contains(":")) {
            int portNum = -1;
            try {
                portNum = Integer.parseInt(port);
            } catch (Exception e) {
                // ignore
            }
            if (portNum > 0 && !(("http".equalsIgnoreCase(proto) && portNum == 80) || ("https".equalsIgnoreCase(proto) && portNum == 443))) {
                hostWithPort = host + ":" + portNum;
            }
        }

        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String path = context + "/api/auth/google/callback";
        return proto + "://" + hostWithPort + path;
    }

    private String firstHeaderValue(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        if (!StringUtils.hasText(v)) return null;
        // may be comma separated
        int idx = v.indexOf(',');
        return idx >= 0 ? v.substring(0, idx).trim() : v.trim();
    }

    private User createGoogleUser(String email, String name) {
        User user = new User();
        user.setUsername(email);
        user.setName(StringUtils.hasText(name) ? name : email);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setType(UserType.REGULAR);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setLastLogin(LocalDateTime.now());
        return userRepository.save(user);
    }

    private void validateGoogleConfig() {
        if (!StringUtils.hasText(googleClientId) || !StringUtils.hasText(googleClientSecret)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Google OAuth no configurado. Definir GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET.");
        }
        // note: redirect URI may be computed from incoming request when not explicitly configured
    }

    private void redirectToFrontend(HttpServletResponse response, String query) {
        String frontendBase = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        String target = frontendBase + "/login" + (StringUtils.hasText(query) ? "?" + query : "");
        try {
            response.sendRedirect(target);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo redirigir al frontend", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}