package com.focuscomex.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.focuscomex.dto.NewSubscriptionRequest;
import com.focuscomex.dto.SubscriptionChangeRequest;
import com.focuscomex.dto.SubscriptionPreviewDTO;
import com.focuscomex.dto.SuscripcionDTO;
import com.focuscomex.services.PlanService;
import com.focuscomex.services.SubscriptionManagementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/suscripciones")
@RequiredArgsConstructor
public class SuscripcionController {

	private final PlanService planService; 
    private final SubscriptionManagementService subscriptionService;

	@PostMapping
	public SuscripcionDTO createSuscripcion(@RequestBody SuscripcionDTO suscripcion) {
		try {
			return planService.saveSuscripcion(suscripcion);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@PutMapping
	public SuscripcionDTO updateSubscription(@RequestBody SuscripcionDTO suscripcion) {
		try {
			return planService.saveSuscripcion(suscripcion);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	/**
     * Cancelar suscripción actual (mantiene acceso hasta final del período)
     */
    @PostMapping("current/cancel")
    public ResponseEntity<Map<String, String>> cancelCurrentSubscription() {
        try {
            String result = subscriptionService.cancelCurrentSubscription();
            return ResponseEntity.ok(Map.of("message", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("current/pause")
    public ResponseEntity<SuscripcionDTO> pauseSubscription() {
        try {
            SuscripcionDTO subscription = subscriptionService.pauseSubscription();
            return ResponseEntity.ok(subscription);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reactivar suscripción pausasada
     */
    @PostMapping("current/resume")
    public ResponseEntity<?> resumeSubscription() {
        try {
            SuscripcionDTO suscription = subscriptionService.resumeSubscription();
            return ResponseEntity.ok(suscription);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Preview del cambio de plan (mostrar información antes de confirmar)
     */
    @GetMapping("preview-change/{planId}")
    public ResponseEntity<SubscriptionPreviewDTO> previewPlanChange(@PathVariable Long planId) {
        try {
            SubscriptionPreviewDTO preview = subscriptionService.previewPlanChange(planId);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Cambiar plan de suscripción
     */
    @PostMapping("change-plan")
    public ResponseEntity<?> changePlan(@RequestBody SubscriptionChangeRequest request) {
        try {
        	Map<String, Object> result = subscriptionService.changeSubscriptionPlan(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Obtener historial de facturación
     */
    @GetMapping("billing-history")
    public ResponseEntity<?> getBillingHistory() {
        try {
            var history = subscriptionService.getBillingHistory();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtener información de la próxima factura
     */
    @GetMapping("upcoming-invoice")
    public ResponseEntity<?> getUpcomingInvoice() {
        try {
            var invoice = subscriptionService.getUpcomingInvoice();
            return ResponseEntity.ok(invoice);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Crear nueva suscripción (para usuarios con suscripción cancelada o sin suscripción)
     */
    @PostMapping("new")
    public ResponseEntity<?> createNewSubscription(@RequestBody NewSubscriptionRequest request) {
        try {
            var result = subscriptionService.createNewSubscription(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
