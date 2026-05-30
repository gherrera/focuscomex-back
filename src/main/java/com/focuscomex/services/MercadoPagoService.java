package com.focuscomex.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focuscomex.dto.CreatePreferenceRequest;
import com.focuscomex.exceptions.ComexException;
import com.focuscomex.model.Plan;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
@Log4j2
public class MercadoPagoService {
	
	private final @Qualifier("subsWebClient") WebClient subsWebClient;

	@Value("${mercadopago.checkount.access-token}")
    private String checkoutAccessToken;
		
	@Value("${api.frontend-url}")
    private String frontUrl;
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	@PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(checkoutAccessToken);
    }
	
	private String buildNotificationUrl() {
		ServletRequestAttributes attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
		
		HttpServletRequest request =  attributes.getRequest();
		String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath());
		return baseUrl + "/api/mercadopago/webhook";
	}
	
	public Map<String, String> createPreference(CreatePreferenceRequest request, String prefixBackurl) throws MPException, MPApiException {
        // Crear item
        PreferenceItemRequest item = PreferenceItemRequest.builder()
        	.id(request.getId())
        	.categoryId("services")
            .title(request.getTitle())
            .quantity(request.getQuantity())
            .unitPrice(new BigDecimal(request.getUnitPrice()))
            .description(request.getDescription())
            .currencyId("CLP")
            .build();

        // Configurar payer
        PreferencePayerRequest payer = PreferencePayerRequest.builder()
            .email(request.getPayerEmail())
            .build();

        // URLs de retorno
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
            .success(frontUrl + prefixBackurl + "/success")
            .failure(frontUrl + prefixBackurl + "/failure")
            .pending(frontUrl + prefixBackurl + "/pending")
            .build();

        // Crear preferencia
        PreferenceRequest preferenceRequest = PreferenceRequest.builder()
            .items(List.of(item))
            .payer(payer)
            .backUrls(backUrls)
            .autoReturn("approved")
            .externalReference(request.getExternalReference())
            .metadata(request.getMetadata())
            .notificationUrl(buildNotificationUrl())
            .build();

        // Crear cliente y preferencia
        PreferenceClient client = new PreferenceClient();
        Preference preference = client.create(preferenceRequest);

        Map<String, String> response = Map.of(
				"initPoint", preference.getInitPoint(),
				"preferenceId", preference.getId()
			);
        return response;
    }
	
	public Map<String, Object> getAccountInfo() {
	    return subsWebClient.get()
	            .uri("/users/me")
	            .accept(MediaType.APPLICATION_JSON)
	            .exchangeToMono(resp -> resp.bodyToMono(String.class))
	            .map(body -> {
	                try {
	                    return objectMapper.readValue(body, new TypeReference<Map<String,Object>>() {});
	                } catch (Exception e) {
	                    throw new ComexException("Error parseando users/me: " + e.getMessage(), e);
	                }
	            })
	            .block();
	}
	
	public Map<String, Object> createSubscription(
			String payerEmail,
            String reason,
            Double amount,
            OffsetDateTime startDate,
            Long externalReference
          ) {
		
		if (startDate == null || startDate.isBefore(OffsetDateTime.now())) {
		    startDate = OffsetDateTime.now().plusMinutes(5);
		}
		OffsetDateTime startMillis = startDate.truncatedTo(ChronoUnit.MILLIS);
	    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
	    String start = startMillis.format(fmt); // ejemplo: 2025-10-21T22:40:34.000-03:00
	    
		Map<String, Object> payload = new HashMap<>();
		payload.put("payer_email", payerEmail);
		payload.put("reason", reason);

		Map<String, Object> autorecurring = new HashMap<>();
		autorecurring.put("frequency", 1);
		autorecurring.put("frequency_type", "months");
		autorecurring.put("transaction_amount", amount);
		autorecurring.put("currency_id", "CLP");
		autorecurring.put("start_date", start);
		payload.put("auto_recurring", autorecurring);
		
		payload.put("external_reference", externalReference.toString());
		//payload.put("back_url", baseUrl + "/suscripcion/return");
		payload.put("back_url", frontUrl);
		payload.put("notification_url", buildNotificationUrl() + "/subs");

		Mono<Map<String, Object>> result = subsWebClient.post()
	            .uri("/preapproval")
	            .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(this::handleResponse)
                //.timeout(REQUEST_TIMEOUT)
                .doOnError(err -> log.error("Error creating preapproval: {}", err.getMessage(), err));
		return result.block();
	}
	
	private Mono<Map<String, Object>> handleResponse(ClientResponse response) {
		HttpStatusCode status = response.statusCode();

        if (status.is2xxSuccessful()) {
            return response.bodyToMono(String.class)
                    .flatMap(body -> {
                        try {
                            if (body == null || body.isBlank()) {
                                return Mono.just(Map.of());
                            }
                            Map<String, Object> map = objectMapper.readValue(body, new TypeReference<>() {});
                            return Mono.just(map);
                        } catch (Exception ex) {
                            log.error("Error parsing MP response body to Map", ex);
                            return Mono.error(new ComexException("Error parsing response from Mercado Pago: " + ex.getMessage(), ex));
                        }
                    });
        } else {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> {
                        String errMsg = String.format("MP API returned status %s. Body: %s", status.value(), body);
                        log.error(errMsg);
                        return Mono.error(new ComexException(errMsg));
                    });
        }
    }
    
	@SuppressWarnings("unchecked")
	private Map<String, Object> getMpDetails(String endpoint) {
		log.debug("Calling MercadoPago API: {}", endpoint);
        try {
            Map<String, Object> response = subsWebClient.get()
                    .uri(endpoint)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
        			        .doAfterRetry(rs -> log.warn("Retrying request endpoint {} (retry #{})", endpoint, rs.totalRetriesInARow() + 1))
        			    )
                    .block();
            
            log.debug("MercadoPago API response received for endpoint {}", endpoint);
            return response;
            
        } catch (Exception e) {
            log.error("MercadoPago API error", e.getMessage());
            throw e;
        }
	}
	
	public Map<String, Object> getPreapproval(String preapprovalId) {
        String endpoint = "/preapproval/" + preapprovalId;

        return getMpDetails(endpoint);
    }
    
	public Map<String, Object> getAuthorizedPayment(String authorizedPaymentId) {
        String endpoint = "/authorized_payments/" + authorizedPaymentId;

        return getMpDetails(endpoint);
    }

    // ===== MÉTODOS PARA GESTIÓN DE SUSCRIPCIONES =====

    /**
     * Cancelar suscripción en MercadoPago (mantiene acceso hasta el final del período)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> cancelSubscription(String subscriptionId) {
        String endpoint = "/preapproval/" + subscriptionId;
        
        Map<String, String> requestBody = Map.of(
            "status", "cancelled",
            "cancel_at_period_end", "true"
        );
        
        try {
            Map<String, Object> response = subsWebClient.put()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .doAfterRetry(rs -> log.warn("Retrying cancel subscription for {} (retry #{})", subscriptionId, rs.totalRetriesInARow() + 1))
                    )
                    .block();
            
            log.info("Subscription {} cancelled successfully", subscriptionId);
            return response;
            
        } catch (Exception e) {
            log.error("Error cancelling subscription {}: {}", subscriptionId, e.getMessage());
            throw new ComexException("Error cancelando suscripción en MercadoPago", e);
        }
    }

    /**
     * Reactivar suscripción en MercadoPago
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> reactivateSubscription(String subscriptionId) {
        String endpoint = "/preapproval/" + subscriptionId;
        
        Map<String, String> requestBody = Map.of(
            "status", "authorized",
            "cancel_at_period_end", "false"
        );
        
        try {
            Map<String, Object> response = subsWebClient.put()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .exchangeToMono(clientResponse -> {
                        log.debug("📥 MP Response Status: {}", clientResponse.statusCode());
                        
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return clientResponse.bodyToMono(Map.class);
                        } else {
                            // Capturar el body del error para debug
                            return clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> {
                                        log.error("❌ MP Error Response: Status={}, Body={}", 
                                                 clientResponse.statusCode(), errorBody);
                                        return Mono.error(new ComexException(
                                            String.format("MercadoPago rechazó la actualización: %s - %s", 
                                                         clientResponse.statusCode(), errorBody)));
                                    });
                        }
                    })
                    .timeout(Duration.ofMinutes(5))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(10))
                            .filter(throwable -> {
                                // Solo reintentar en errores de red, no en 400 Bad Request
                                return !(throwable instanceof ComexException && 
                                       throwable.getMessage().contains("400"));
                            })
                            .doAfterRetry(rs -> log.warn("🔄 Reintentando actualización de suscripción {} (intento #{})", 
                                                       subscriptionId, rs.totalRetriesInARow() + 1))
                    )
                    .block();
            
            log.info("Subscription {} reactivated successfully", subscriptionId);
            return response;
            
        } catch (Exception e) {
            log.error("Error reactivating subscription {}: {}", subscriptionId, e.getMessage());
            throw new ComexException("Error reactivando suscripción en MercadoPago", e);
        }
    }
    
    /**
     * REACTIVAR suscripción pausada en MercadoPago
     * Ahora SÍ funciona porque cambiamos de "paused" a "authorized"
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resumeSubscription(String subscriptionId) {
        String endpoint = "/preapproval/" + subscriptionId;
        
        // Reactivar suscripción pausada
        Map<String, String> requestBody = Map.of(
            "status", "authorized"
        );
        
        try {
            Map<String, Object> response = subsWebClient.put()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .exchangeToMono(clientResponse -> {
                        log.debug("📥 MP Resume Response Status: {}", clientResponse.statusCode());
                        
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return clientResponse.bodyToMono(Map.class);
                        } else {
                            return clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> {
                                        log.error("❌ MP Error reactivando: Status={}, Body={}", 
                                                 clientResponse.statusCode(), errorBody);
                                        return Mono.error(new ComexException(
                                            String.format("Error reactivando suscripción en MP: %s - %s", 
                                                         clientResponse.statusCode(), errorBody)));
                                    });
                        }
                    })
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(5))
                            .filter(throwable -> {
                                // No reintentar errores 400
                                return !(throwable instanceof ComexException && 
                                       throwable.getMessage().contains("400"));
                            })
                            .doAfterRetry(rs -> log.warn("Retrying resume subscription for {} (retry #{})", 
                                                       subscriptionId, rs.totalRetriesInARow() + 1))
                    )
                    .block();
            
            log.info("✅ Subscription {} resumed successfully", subscriptionId);
            return response;
            
        } catch (Exception e) {
            log.error("❌ Error resuming subscription {}: {}", subscriptionId, e.getMessage());
            throw new ComexException("Error reactivando suscripción en MercadoPago: " + e.getMessage(), e);
        }
    }

    /**
     * Actualizar plan de suscripción (para upgrades inmediatos)
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> updateSubscriptionPlan(String subscriptionId, Plan newPlan) {
        String endpoint = "/preapproval/" + subscriptionId;
        
        // Construir payload completo según API de MercadoPago
        Map<String, Object> autoRecurring = new HashMap<>();
        autoRecurring.put("frequency", 1);
        autoRecurring.put("frequency_type", "months"); // o "days" según tu configuración
        autoRecurring.put("transaction_amount", newPlan.getPrice().doubleValue());
        autoRecurring.put("currency_id", "CLP");
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("auto_recurring", autoRecurring);
        requestBody.put("reason", "Cambio a plan: " + newPlan.getNombre());
        
        try {
            Map<String, Object> response = subsWebClient.put()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .exchangeToMono(clientResponse -> {
                        log.debug("📥 MP Response Status: {}", clientResponse.statusCode());
                        
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return clientResponse.bodyToMono(Map.class);
                        } else {
                            // Capturar el body del error para debug
                            return clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> {
                                        log.error("❌ MP Error Response: Status={}, Body={}", 
                                                 clientResponse.statusCode(), errorBody);
                                        return Mono.error(new ComexException(
                                            String.format("MercadoPago rechazó la actualización: %s - %s", 
                                                         clientResponse.statusCode(), errorBody)));
                                    });
                        }
                    })
                    .timeout(Duration.ofMinutes(5))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(10))
                            .filter(throwable -> {
                                // Solo reintentar en errores de red, no en 400 Bad Request
                                return !(throwable instanceof ComexException && 
                                       throwable.getMessage().contains("400"));
                            })
                            .doAfterRetry(rs -> log.warn("🔄 Reintentando actualización de suscripción {} (intento #{})", 
                                                       subscriptionId, rs.totalRetriesInARow() + 1))
                    )
                    .block();
            
            log.info("Subscription {} plan updated successfully", subscriptionId);
            return response;
        } catch (Exception e) {
            log.error("Error updating subscription plan {}: {}", subscriptionId, e.getMessage());
            throw new ComexException("Error actualizando plan en MercadoPago", e);
        }
    }

    /**
     * Obtener historial de facturas de una suscripción
     */
    public List<Object> getSubscriptionInvoices(String subscriptionId) {
        String endpoint = "/preapproval/" + subscriptionId + "/invoices";
        
        try {
            List<Object> response = subsWebClient.get()
                    .uri(endpoint)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono(clientResponse -> {
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return clientResponse.bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<Object>>() {});
                        } else if (clientResponse.statusCode().value() == 404) {
                            // 404 significa que no hay facturas históricas para esta suscripción
                            log.debug("No invoices found for subscription {} (404)", subscriptionId);
                            return Mono.just(List.of());
                        } else {
                            return clientResponse.createException().flatMap(Mono::error);
                        }
                    })
                    .timeout(Duration.ofMinutes(1))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(10))
                            .filter(throwable -> {
                                // Solo reintentar en errores de red, no en 400 Bad Request ni 404
                                return !(throwable instanceof ComexException && 
                                       (throwable.getMessage().contains("400") || throwable.getMessage().contains("404")));
                            })
                            .doAfterRetry(rs -> log.warn("🔄 Retrying get invoices for {} (retry #{})", 
                                                       subscriptionId, rs.totalRetriesInARow() + 1))
                    )
                    .block();
            
            log.debug("Retrieved {} invoices for subscription {}", response != null ? response.size() : 0, subscriptionId);
            return response != null ? response : List.of();
            
        } catch (Exception e) {
            log.error("Error getting subscription invoices {}: {}", subscriptionId, e.getMessage());
            // Si es un error de "no encontrado", devolver lista vacía en lugar de lanzar excepción
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                log.info("No invoice history available for subscription {}", subscriptionId);
                return List.of();
            }
            throw new ComexException("Error obteniendo historial de facturas", e);
        }
    }

    /**
     * Obtener próxima factura de una suscripción
     */
    @SuppressWarnings("unchecked")
	public Object getUpcomingInvoice(String subscriptionId) {
        String endpoint = "/preapproval/" + subscriptionId + "/upcoming_invoice";
        
        try {
            Map<String, Object> response = subsWebClient.get()
                    .uri(endpoint)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono(clientResponse -> {
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return clientResponse.bodyToMono(Map.class);
                        } else if (clientResponse.statusCode().value() == 404) {
                            // 404 significa que no hay próxima factura programada
                            log.debug("No upcoming invoice found for subscription {} (404)", subscriptionId);
                            return Mono.empty(); // Usar Mono.empty() en lugar de Mono.just(null)
                        } else {
                            return clientResponse.createException().flatMap(Mono::error);
                        }
                    })
                    .timeout(Duration.ofMinutes(1))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(10))
                            .filter(throwable -> {
                                // Solo reintentar en errores de red, no en 400 Bad Request ni 404
                                return !(throwable instanceof ComexException && 
                                       (throwable.getMessage().contains("400") || throwable.getMessage().contains("404")));
                            })
                            .doAfterRetry(rs -> log.warn("🔄 Retrying get upcoming invoice for {} (retry #{})", 
                                                       subscriptionId, rs.totalRetriesInARow() + 1))
                    )
                    .blockOptional() // Usar blockOptional() para manejar Mono.empty()
                    .orElse(null); // Convertir Optional.empty() a null
            
            log.debug("Retrieved upcoming invoice for subscription {}: {}", subscriptionId, response != null ? "found" : "not found");
            return response;
            
        } catch (Exception e) {
            log.error("Error getting upcoming invoice {}: {}", subscriptionId, e.getMessage());
            // Si es un error de "no encontrado", devolver null en lugar de lanzar excepción
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                log.info("No upcoming invoice available for subscription {}", subscriptionId);
                return null;
            }
            throw new ComexException("Error obteniendo próxima factura", e);
        }
    }
    
    /**
     * SUSPENDER suscripción en MercadoPago (pausa los cobros pero mantiene la suscripción)
     * Mucho mejor que cancelar porque se puede reactivar fácilmente
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pauseSubscription(String subscriptionId) {
        String endpoint = "/preapproval/" + subscriptionId;
        
        // Pausar la suscripción en lugar de cancelarla
        Map<String, String> requestBody = Map.of(
            "status", "paused"
        );
        
        try {
            Map<String, Object> response = subsWebClient.put()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .exchangeToMono(clientResponse -> {
                        log.debug("📥 MP Pause Response Status: {}", clientResponse.statusCode());
                        
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return clientResponse.bodyToMono(Map.class);
                        } else {
                            return clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> {
                                        log.error("❌ MP Error pausando: Status={}, Body={}", 
                                                 clientResponse.statusCode(), errorBody);
                                        return Mono.error(new ComexException(
                                            String.format("Error pausando suscripción en MP: %s - %s", 
                                                         clientResponse.statusCode(), errorBody)));
                                    });
                        }
                    })
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(5))
                            .doAfterRetry(rs -> log.warn("Retrying pause subscription for {} (retry #{})", 
                                                       subscriptionId, rs.totalRetriesInARow() + 1))
                    )
                    .block();
            
            log.info("✅ Subscription {} paused successfully", subscriptionId);
            return response;
        } catch (Exception e) {
            log.error("❌ Error pausing subscription {}: {}", subscriptionId, e.getMessage());
            throw new ComexException("Error pausando suscripción en MercadoPago: " + e.getMessage(), e);
        }
    }

    /**
     * Crear preferencia de pago para prorrateo de upgrades
     */
    @SuppressWarnings("unchecked")
	public Map<String, Object> createImmediatePayment(Map<String, Object> paymentRequest) {
        try {
            log.info("💳 Creando preferencia de pago para prorrateo por ${} CLP", paymentRequest.get("transaction_amount"));
            
            // Crear preferencia de pago en lugar de pago directo
            CreatePreferenceRequest prefRequest = new CreatePreferenceRequest();
            prefRequest.setId("upgrade_" + System.currentTimeMillis());
            prefRequest.setTitle(paymentRequest.get("description").toString());
            prefRequest.setQuantity(1);
            prefRequest.setUnitPrice((int)paymentRequest.get("transaction_amount"));
            prefRequest.setDescription(paymentRequest.get("description").toString());
            prefRequest.setPayerEmail(paymentRequest.get("payer_email").toString());
            prefRequest.setExternalReference(paymentRequest.get("external_reference").toString());
            prefRequest.setMetadata((Map<String, Object>)paymentRequest.get("metadata"));
            
            Map<String, String> preferenceResult = createPreference(prefRequest, "/suscripcion");
            
            // Convertir respuesta para mantener compatibilidad
            Map<String, Object> response = new HashMap<>();
            response.put("id", preferenceResult.get("preferenceId"));
            response.put("status", "pending"); // Las preferencias inician como pending
            response.put("transaction_amount", paymentRequest.get("transaction_amount"));
            response.put("init_point", preferenceResult.get("initPoint"));
            response.put("external_reference", paymentRequest.get("external_reference"));
            
            log.info("✅ Preferencia de pago creada para upgrade: {}", preferenceResult.get("preferenceId"));
            
            return response;
            
        } catch (Exception e) {
            log.error("❌ Error creating upgrade payment preference: {}", e.getMessage());
            throw new ComexException("Error creando preferencia de pago para upgrade: " + e.getMessage(), e);
        }
    }
}
