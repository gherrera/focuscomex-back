package com.focuscomex.controller;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.focuscomex.dto.JwtRequest;
import com.focuscomex.dto.JwtResponse;
import com.focuscomex.dto.PasswordChangeRequest;
import com.focuscomex.dto.PasswordResetRequest;
import com.focuscomex.dto.UsuarioDTO;
import com.focuscomex.exceptions.ComexException;
import com.focuscomex.security.AuthenticationService;
import com.focuscomex.security.CurrentUser;
import com.focuscomex.security.LoginAttemptService;
import com.focuscomex.services.PasswordResetService;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class AuthController {
	private static Logger log = LogManager.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final AuthenticationService authenticationService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;

    @Value("${api.rate-limit.capacity:20}")
    private int capacity;
    
    @Value("${api.rate-limit.time-window:1}")
    private int timeWindow;
    
    @Value("${api.rate-limit.time-unit:MINUTES}")
    private String timeUnit;

    private Bucket bucket;

    @PostConstruct
    public void init() {
        TimeUnit unit = TimeUnit.valueOf(timeUnit);
        Duration duration = Duration.ofMillis(unit.toMillis(timeWindow));
        
        Bandwidth limit = Bandwidth.classic(capacity, 
            Refill.greedy(capacity, duration));
            
        this.bucket = Bucket.builder()
            .addLimit(limit)
            .build();
            
        log.info("Rate limit inicializado: {} peticiones cada {} {}", 
            capacity, timeWindow, timeUnit);
    }
    
	@PostMapping("login")
    public JwtResponse login(@RequestBody JwtRequest authenticationRequest) {
		if (bucket.tryConsume(1)) {
			try {
		        Authentication auth = authenticate(authenticationRequest.getUsername(), authenticationRequest.getPassword());
		
		        if(auth != null && auth.getPrincipal() instanceof CurrentUser) {
		            CurrentUser currentUser = (CurrentUser)auth.getPrincipal();
		            JwtResponse response = authenticationService.authenticate(currentUser);
		            loginAttemptService.loginSucceeded(authenticationRequest.getUsername());
		            return response;
		        } else {
	                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad Credentials");
		        }
			}catch(Exception e) {
				log.error("Usuario o contraseña inválidos", e);
	            String username = authenticationRequest.getUsername();
				boolean lockedBefore = loginAttemptService.isBlocked(username);
				if(!lockedBefore) {
	                loginAttemptService.loginFailed(username);
	            }
	            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
			}
		}else {
			log.warn("Límite de peticiones alcanzado");
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests - Rate limit exceeded");
		}
    }
	
	@PostMapping("register")
	public JwtResponse register(@RequestBody UsuarioDTO usuario) {
		try {
			CurrentUser newUser = authenticationService.register(usuario);
			return authenticationService.authenticate(newUser);
		} catch (ComexException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@PostMapping("forgot-password")
    public void requestPasswordReset(@RequestBody PasswordResetRequest request) {
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes");
        }
        try {
            passwordResetService.createPasswordResetTokenForUser(request.getEmail());
        } catch (ComexException e) {
            // No revelamos si el email existe o no por seguridad
            log.warn("Intento de reset de contraseña para email no encontrado: {}", request.getEmail());
        }
    }
	
	@GetMapping("reset-password/{token}/validate")
    public void validateResetToken(@PathVariable String token) {
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes");
        }
        if(!passwordResetService.validateToken(token)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido o expirado");
		}
    }
	
	@PostMapping("reset-password/{token}")
    public void resetPassword(@PathVariable String token, @RequestBody @Valid PasswordChangeRequest request) {
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes");
        }
        try {
            passwordResetService.resetPassword(token, request.getPassword());
        } catch (ComexException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
	
	private Authentication authenticate(String username, String password) throws Exception {
        try {
            return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (DisabledException e) {
            throw new ComexException("Usuario no habilitado", e);
        } catch (BadCredentialsException e) {
            throw new ComexException("Usuario y/o clave incorrecta", e);
        } catch (LockedException e) {
            throw new ComexException("Usuario bloqueado", e);
        } catch (Exception e) {
            throw new ComexException("Error de autenticación", e);
        }
    }
	
}