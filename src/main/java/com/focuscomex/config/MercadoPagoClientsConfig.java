package com.focuscomex.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;


@Configuration
public class MercadoPagoClientsConfig {

	@Value("${mercadopago.subs.access-token}")
    private String subsAccessToken;

    @Bean
    @Qualifier("subsWebClient")
    WebClient subsWebClient() {
        return WebClient.builder()
            .baseUrl("https://api.mercadopago.com")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + subsAccessToken)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
