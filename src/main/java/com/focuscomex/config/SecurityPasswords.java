package com.focuscomex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityPasswords {

	@Bean
	PasswordEncoder passwordEncoder() {
	    // Por defecto usa {bcrypt}; también entiende {argon2id}, {pbkdf2}, etc.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
