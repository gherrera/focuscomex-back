package com.focuscomex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.focuscomex.model.WebhookEvent;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

	/**
     * Comprueba si ya existe un evento con la clave dada.
     * Útil para evitar procesar el mismo webhook dos veces.
     *
     * @param eventKey clave única del evento (ej: "subscription_preapproval:12345:created")
     * @return true si existe
     */
    boolean existsByEventKey(String eventKey);
    
    /**
     * Busca el evento por su clave única.
     *
     * @param eventKey clave única del evento
     * @return Optional con el WebhookEvent si existe
     */
    Optional<WebhookEvent> findByEventKey(String eventKey);
}