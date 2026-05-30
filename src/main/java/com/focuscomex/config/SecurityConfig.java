package com.focuscomex.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.focuscomex.security.AuthenticationService;
import com.focuscomex.security.TokenBasedAuthentication;
import com.focuscomex.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	static {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }
	
	private final JwtUtil jwtUtil;
	private final AuthenticationService authenticationService;
	
	@Bean
	AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authConfig) throws Exception {
		return authConfig.getAuthenticationManager();
	}
	
	@Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
        		String corsUrls = System.getenv("cors_urls");
                registry.addMapping("/api/**")
                	.allowedOriginPatterns(corsUrls.split(","))
                	.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }
        };
    }
	
	@Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    		.securityContext(context -> context.requireExplicitSave(false))
            ;
        http
			.authorizeHttpRequests(requests -> {
                requests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                requests
                    .requestMatchers("/").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/api/mercadopago/**").permitAll()
                    .requestMatchers("/api/webhook/**").permitAll()
                    .requestMatchers("/api/adm/**").hasRole("ADMIN")
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated();
			})
            .addFilterBefore(new JwtAuthFilter(jwtUtil, authenticationService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

	@RequiredArgsConstructor
    public static class JwtAuthFilter extends OncePerRequestFilter {
        private JwtUtil jwtUtil;
        private AuthenticationService authenticationService;

        public JwtAuthFilter(JwtUtil jwtUtil, AuthenticationService authenticationService) {
            this.jwtUtil = jwtUtil;
            this.authenticationService = authenticationService;
        }
        
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
        	String token = null;
        	String header = request.getHeader("Authorization");
        	if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
        	    token = header.substring(7);
        	}
            if (token != null) {
                String username = jwtUtil.extractUsername(token);
                if (username != null && jwtUtil.validateToken(token)) {
                	UserDetails currentUser = authenticationService.loadUserByUsername(username);
                	
                	TokenBasedAuthentication authentication = new TokenBasedAuthentication(currentUser);
                    authentication.setToken(token);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}