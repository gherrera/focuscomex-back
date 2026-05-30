package com.focuscomex.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.focuscomex.dto.NewSubscriptionRequest;
import com.focuscomex.dto.SubscriptionChangeRequest;
import com.focuscomex.dto.SubscriptionPreviewDTO;
import com.focuscomex.dto.SuscripcionDTO;
import com.focuscomex.dto.UsuarioDTO;
import com.focuscomex.enums.SuscripcionSource;
import com.focuscomex.enums.SuscripcionStatus;
import com.focuscomex.exceptions.ComexException;
import com.focuscomex.mapper.SuscripcionMapper;
import com.focuscomex.model.MercadoPagoPreapproval;
import com.focuscomex.model.Plan;
import com.focuscomex.model.Suscripcion;
import com.focuscomex.model.User;
import com.focuscomex.repository.MercadoPagoPreapprovalRepository;
import com.focuscomex.repository.PlanRepository;
import com.focuscomex.repository.SuscripcionRepository;
import com.focuscomex.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class SubscriptionManagementService {

    private final SuscripcionRepository suscripcionRepository;
    private final PlanRepository planRepository;
    private final MercadoPagoService mercadoPagoService;
    private final UserService userService;
	private final MercadoPagoPreapprovalRepository mercadoPagoPreapprovalRepository;
	private final UserRepository userRepository;

    private Suscripcion getActiveSubscription(User user) {
    	Suscripcion subscription = user.getCurrentSubscription();
        if(subscription == null 
        		|| subscription.getMpPreapprovalDetails() == null || subscription.getExternalSubscriptionId() == null 
        		|| subscription.getStatus() != SuscripcionStatus.active || !subscription.isPaid() 
        		|| subscription.getPlan() == null || subscription.getPlan().isTrial()) {
        	throw new ComexException("No hay suscripción activa para cancelar");
        }
        return subscription;
    }
    
    private Suscripcion getModifiableSubscription(User user) {
        Suscripcion subscription = user.getCurrentSubscription();
        if(subscription == null || subscription.getExternalSubscriptionId() == null 
                || subscription.getPlan() == null) {
            throw new ComexException("No hay suscripción válida");
        }
        
        // Permitir modificación solo en estados específicos
        if(subscription.getStatus() != SuscripcionStatus.active && 
           subscription.getStatus() != SuscripcionStatus.paused) {
            throw new ComexException("La suscripción debe estar activa o pausada para modificarla");
        }
        
        return subscription;
    }
    
    /**
     * Cancelar suscripción actual (mantiene acceso hasta final del período)
     */
    @Transactional
    public String cancelCurrentSubscription() {
    	User user = userService.getCurrentUser();
        Suscripcion subscription = getActiveSubscription(user);
        try {
            // Cancelar en MercadoPago (manteniendo acceso hasta el final del período)
            mercadoPagoService.cancelSubscription(subscription.getExternalSubscriptionId());
            
            // Marcar como cancelada pero con acceso hasta el final del período
            subscription.setCancelAtPeriodEnd(true);
            subscription.setCanceledAt(OffsetDateTime.now());
            suscripcionRepository.save(subscription);
            
            return "Suscripción cancelada exitosamente. Mantendrás acceso hasta el " + 
                   subscription.getCurrentPeriodEnd();
                   
        } catch (Exception e) {
            throw new RuntimeException("Error cancelando suscripción: " + e.getMessage());
        }
    }

    @Transactional 
    public SuscripcionDTO resumeSubscription() {
    	User user = userService.getCurrentUser();
    	Suscripcion subscription = user.getCurrentSubscription();

        if (subscription == null || !subscription.getStatus().equals(SuscripcionStatus.paused)) {
            throw new RuntimeException("La suscripción no está pausada o no existe");
        }
        
        try {
            // 1. Reactivar en MercadoPago
            mercadoPagoService.resumeSubscription(subscription.getExternalSubscriptionId());
            
            // 2. RECALCULAR fechas del período
            OffsetDateTime now = OffsetDateTime.now();
            subscription.setStatus(SuscripcionStatus.active);
            subscription.setResumedAt(now);
            
            // 3. Extender período basado en tiempo pausado
            if (subscription.getPausedDaysRemaining() != null) {
            	OffsetDateTime newPeriodEnd = now.plusDays(subscription.getPausedDaysRemaining());
                subscription.setCurrentPeriodEnd(newPeriodEnd);
            }
            
            // 4. Limpiar campos de pausa
            subscription.setPausedAt(null);
            subscription.setPausedDaysRemaining(null);
            subscription.setUpdatedAt(now);
            
            return SuscripcionMapper.get().mapToDTO(subscription);            
        } catch (Exception e) {
            throw new RuntimeException("Error reactivando suscripción", e);
        }
    }

    /**
     * Preview del cambio de plan
     */
    public SubscriptionPreviewDTO previewPlanChange(Long newPlanId) {
    	User user = userService.getCurrentUser();
    	Suscripcion subscription = getActiveSubscription(user);
        
        Plan currentPlan = subscription.getPlan();
        Plan newPlan = planRepository.findById(newPlanId)
                .orElseThrow(() -> new RuntimeException("Plan no encontrado"));

        String changeType = determineChangeType(currentPlan, newPlan);
        boolean canChange = true;
        String reason = null;

        // Verificar si puede hacer downgrade
        if ("downgrade".equals(changeType)) {
        	UsuarioDTO userDTO = userService.getCurrentUserDTO();
        	/*
            long activeCausasCount = userDTO.getCausasCount();
            if (activeCausasCount > newPlan.getMaxCausas()) {
                canChange = false;
                reason = String.format("Tienes %d causas activas y el plan %s permite máximo %d", 
                                     activeCausasCount, newPlan.getNombre(), newPlan.getMaxCausas());
            }
            */
        }

        // Calcular montos y fechas
        BigDecimal proratedAmount = null;
        LocalDateTime changeEffectiveDate;
        String description;

        if ("upgrade".equals(changeType)) {
            // Para upgrades: cambio inmediato con prorrateo
            proratedAmount = calculateProratedAmount(subscription, currentPlan, newPlan);
            changeEffectiveDate = LocalDateTime.now();
            description = "El cambio se aplicará inmediatamente. Se cobrará la diferencia proporcional.";
        } else {
            // Para downgrades: cambio en el próximo período
            changeEffectiveDate = subscription.getCurrentPeriodEnd().toLocalDateTime();
            description = "El cambio se aplicará en tu próximo período de facturación.";
        }

        return SubscriptionPreviewDTO.builder()
                .changeType(changeType)
                .currentPlanName(currentPlan.getNombre())
                .newPlanName(newPlan.getNombre())
                .currentPrice(currentPlan.getPrice())
                .newPrice(newPlan.getPrice())
                .proratedAmount(proratedAmount)
                .changeEffectiveDate(changeEffectiveDate)
                .description(description)
                .canChange(canChange)
                .reason(reason)
                .build();
    }

    private void logPlanChange(Suscripcion subscription, Plan previousPlan, Plan newPlan, String changeType) {
        log.info("📋 Plan Change Log - Subscription ID: {}, User ID: {}, Previous Plan: {} (${} CLP), New Plan: {} (${} CLP), Change Type: {}", 
                 subscription.getId(),
                 subscription.getUser().getId(),
                 previousPlan.getNombre(),
                 previousPlan.getPrice(),
                 newPlan.getNombre(), 
                 newPlan.getPrice(),
                 changeType);
        
        // Información adicional para auditoría
        log.info("📊 Plan Change Details - External Subscription ID: {}, Previous Plan ID: {}, New Plan ID: {}, Timestamp: {}", 
                 subscription.getExternalSubscriptionId(),
                 previousPlan.getId(),
                 newPlan.getId(),
                 OffsetDateTime.now());
        
        // Si quieres también podrías guardar este cambio en una tabla de auditoría
        // auditService.logPlanChange(subscription, previousPlan, newPlan, changeType);
    }
    
    /**
     * Cambiar plan de suscripción activa (con prorrateo automático de MP)
     */
    @SuppressWarnings("unchecked")
	@Transactional
    public Map<String, Object> changeSubscriptionPlan(SubscriptionChangeRequest request) {
        User user = userService.getCurrentUser();
        Suscripcion subscription = getModifiableSubscription(user);
        Plan newPlan = planRepository.findById(request.getNewPlanId())
                .orElseThrow(() -> new ComexException("Plan no encontrado"));
        
        // Si el plan actual es trial, crear nueva suscripción en lugar de cambiar plan
        if (subscription.getPlan().isTrial()) {
            log.info("🔄 Plan actual es trial, creando nueva suscripción en lugar de cambio de plan");
            
            NewSubscriptionRequest newSubRequest = new NewSubscriptionRequest();
            newSubRequest.setPlanId(request.getNewPlanId());
            
            return (Map<String, Object>) createNewSubscription(newSubRequest);
        }
        
        // Validar que no es el mismo plan
        if (subscription.getPlan().getId().equals(newPlan.getId())) {
            throw new ComexException("Ya tienes este plan activo");
        }
        
        // Si está pausada, primero debe reactivarla
        if (subscription.getStatus() == SuscripcionStatus.paused) {
            throw new ComexException("Tu suscripción está pausada. Reactívala primero para cambiar de plan.");
        }
        
        UsuarioDTO userDTO = userService.getCurrentUserDTO();
        /*
        if(newPlan.getMaxCausas() < userDTO.getCausasCount()) {
			throw new ComexException("No puedes cambiar a este plan porque tienes más causas activas de las permitidas por el plan seleccionado.");
		}
		*/

        try {
            log.info("🔄 Iniciando cambio de plan: {} → {} (usuario {})", 
                    subscription.getPlan().getNombre(), newPlan.getNombre(), user.getId());
            
            Plan previousPlan = subscription.getPlan();
            String changeType = determineChangeType(previousPlan, newPlan);
            
            if ("upgrade".equals(changeType)) {
                // UPGRADE: Cobrar diferencia proporcional + actualizar plan inmediatamente
                
                // 1. Calcular monto proporcional a cobrar HOY
                BigDecimal proratedAmount = calculateProratedAmount(subscription, previousPlan, newPlan);
                
                if (proratedAmount.compareTo(BigDecimal.ZERO) > 0) {
                    // 2. Crear preferencia de pago por la diferencia
                    log.info("💰 Generando preferencia de pago por diferencia proporcional: ${} CLP", proratedAmount);
                    
                    Map<String, Object> paymentRequest = Map.of(
                        "transaction_amount", proratedAmount.intValue(),
                        "description", String.format("Diferencia proporcional por upgrade a %s", newPlan.getNombre()),
                        "external_reference", subscription.getId(),
                        "payer_email", user.getUsername(),
                        "metadata", Map.of(
							"subscription_id", subscription.getId(),
							"user_id", user.getId(),
							"previous_plan_id", previousPlan.getId(),
							"new_plan_id", newPlan.getId(),
							"product", "subscription_upgrade"
						)
                    );
                    
                    // Crear preferencia de pago para el upgrade
                    Map<String, Object> paymentResponse = mercadoPagoService.createImmediatePayment(paymentRequest);
                    String paymentUrl = (String) paymentResponse.get("init_point");
                    
                    log.info("✅ Preferencia de pago creada para upgrade: {}", paymentResponse.get("id"));
                    
                    // IMPORTANTE: NO aplicamos el cambio de plan hasta confirmar el pago
                    // El upgrade se aplicará automáticamente cuando el webhook confirme el pago
                    
                    log.info("UPGRADE PENDIENTE - Plan actual {} se mantiene hasta confirmar pago de ${}", 
                             previousPlan.getNombre(), proratedAmount);
                    
                    // Crear respuesta con URL de pago pero sin aplicar el cambio
                    Map<String, Object> upgradeResponse = new HashMap<>();
                    upgradeResponse.put("subscription", SuscripcionMapper.get().mapToDTO(subscription)); // Plan actual
                    upgradeResponse.put("paymentRequired", true);
                    upgradeResponse.put("paymentAmount", proratedAmount);
                    upgradeResponse.put("paymentUrl", paymentUrl);
                    upgradeResponse.put("targetPlan", newPlan.getNombre());
                    upgradeResponse.put("message", String.format(
                        "ADVERTENCIA: Tu plan actual %s se mantiene activo hasta confirmar el pago de $%s por la diferencia proporcional para el upgrade a %s.",
                        previousPlan.getNombre(), proratedAmount.toPlainString(), newPlan.getNombre()
                    ));
                    
                    return upgradeResponse;
                } else {
                    // Sin diferencia a cobrar - upgrade gratuito (caso muy raro)
                    // Aun así, por seguridad, aplicamos el cambio inmediatamente solo si no hay costo
                    log.info("🆓 Upgrade gratuito detectado: {} → {} (sin diferencia de precio)", 
                             previousPlan.getNombre(), newPlan.getNombre());
                    
                    // Actualizar plan en MercadoPago
                    Map<String, Object> mpResponse = mercadoPagoService.updateSubscriptionPlan(
                        subscription.getExternalSubscriptionId(), 
                        newPlan
                    );
                    
                    String mpStatus = (String) mpResponse.get("status");
                    if ("authorized".equals(mpStatus) || "active".equals(mpStatus)) {
                        // Actualizar en BD inmediatamente
                        subscription.setPlan(newPlan);
                        subscription.setUpdatedAt(OffsetDateTime.now());
                        logPlanChange(subscription, previousPlan, newPlan, "immediate_no_prorate");
                        subscription = suscripcionRepository.save(subscription);
                        
                        log.info("✅ Upgrade gratuito completado: {} → {}", 
                                 previousPlan.getNombre(), newPlan.getNombre());
                        
                        return Map.of(
                            "subscription", SuscripcionMapper.get().mapToDTO(subscription),
                            "paymentRequired", false,
                            "message", "Plan actualizado exitosamente sin costo adicional."
                        );
                    } else {
                        throw new ComexException("MercadoPago no pudo actualizar la suscripción: " + mpStatus);
                    }
                }
                
            } else {
                // DOWNGRADE: Aplicar inmediatamente con advertencia clara
                log.info("📉 Downgrade inmediato solicitado: {} → {}", 
                         previousPlan.getNombre(), newPlan.getNombre());
                
                // Calcular días restantes del período actual
                long daysRemaining = ChronoUnit.DAYS.between(
                    OffsetDateTime.now().toLocalDate(),
                    subscription.getCurrentPeriodEnd().toLocalDate()
                );
                
                // Calcular el monto que se "pierde" por el downgrade inmediato
                BigDecimal currentMonthlyPrice = new BigDecimal(previousPlan.getPrice());
                BigDecimal newMonthlyPrice = new BigDecimal(newPlan.getPrice());
                BigDecimal priceDifference = currentMonthlyPrice.subtract(newMonthlyPrice);
                
                // Calcular monto perdido proporcional a días restantes
                BigDecimal lostAmount = BigDecimal.ZERO;
                if (daysRemaining > 0 && priceDifference.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal daysInMonth = BigDecimal.valueOf(30); // Aproximación
                    BigDecimal remainingDaysDecimal = BigDecimal.valueOf(daysRemaining);
                    lostAmount = priceDifference.multiply(remainingDaysDecimal).divide(daysInMonth, 2, RoundingMode.HALF_UP);
                }
                
                try {
                    // Actualizar plan en MercadoPago primero
                    Map<String, Object> mpResponse = mercadoPagoService.updateSubscriptionPlan(
                        subscription.getExternalSubscriptionId(), 
                        newPlan
                    );
                    
                    String mpStatus = (String) mpResponse.get("status");
                    if ("authorized".equals(mpStatus) || "active".equals(mpStatus)) {
                        // Actualizar en BD inmediatamente
                        subscription.setPlan(newPlan);
                        subscription.setUpdatedAt(OffsetDateTime.now());
                        logPlanChange(subscription, previousPlan, newPlan, "immediate_downgrade");
                        subscription = suscripcionRepository.save(subscription);
                        
                        log.info("✅ Downgrade inmediato aplicado: {} → {} (Monto no reembolsable: ${})", 
                                 previousPlan.getNombre(), newPlan.getNombre(), lostAmount.toPlainString());
                        
                        // Crear respuesta con información del downgrade
                        Map<String, Object> downgradeResponse = new HashMap<>();
                        downgradeResponse.put("subscription", SuscripcionMapper.get().mapToDTO(subscription));
                        downgradeResponse.put("paymentRequired", false);
                        downgradeResponse.put("previousPlan", previousPlan.getNombre());
                        downgradeResponse.put("newPlan", newPlan.getNombre());
                        downgradeResponse.put("daysRemaining", daysRemaining);
                        downgradeResponse.put("lostAmount", lostAmount);
                        
                        String warningMessage;
                        if (lostAmount.compareTo(BigDecimal.ZERO) > 0) {
                            warningMessage = String.format(
                                "DOWNGRADE APLICADO: Tu plan se cambió inmediatamente de %s a %s. " +
                                "⚠️ IMPORTANTE: Los $%s correspondientes a los %d días restantes del período actual " +
                                "NO serán reembolsados. Tu próxima facturación será con el nuevo plan %s.",
                                previousPlan.getNombre(), 
                                newPlan.getNombre(),
                                lostAmount.toPlainString(),
                                daysRemaining,
                                newPlan.getNombre()
                            );
                        } else {
                            warningMessage = String.format(
                                "DOWNGRADE APLICADO: Tu plan se cambió exitosamente de %s a %s. " +
                                "Tu próxima facturación será con el nuevo plan.",
                                previousPlan.getNombre(), 
                                newPlan.getNombre()
                            );
                        }
                        
                        downgradeResponse.put("message", warningMessage);
                        
                        return downgradeResponse;
                    } else {
                        throw new ComexException("MercadoPago no pudo actualizar la suscripción para el downgrade: " + mpStatus);
                    }
                } catch (Exception e) {
                    log.error("Error aplicando downgrade inmediato: {}", e.getMessage());
                    throw new ComexException("Error aplicando el downgrade: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("ERROR cambiando plan para usuario {}: {}", user.getId(), e.getMessage());
            throw new ComexException("Error cambiando plan: " + e.getMessage(), e);
        }
    }
    
    private OffsetDateTime parseMPDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        
        try {
            // MercadoPago devuelve fechas en formato ISO 8601
            // Por ejemplo: "2025-11-15T10:30:00.000-03:00"
            return OffsetDateTime.parse(dateStr);
        } catch (Exception e) {
            log.warn("Error parseando fecha de MercadoPago: {}", dateStr, e);
            return null;
        }
    }

    /**
     * Obtener historial de facturación
     */
    public List<Object> getBillingHistory() {
    	User user = userService.getCurrentUser();
    	Suscripcion subscription = getActiveSubscription(user);
        
        try {
            return mercadoPagoService.getSubscriptionInvoices(subscription.getExternalSubscriptionId());
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo historial: " + e.getMessage());
        }
    }

    /**
     * Obtener próxima factura
     */
    public Object getUpcomingInvoice() {
    	User user = userService.getCurrentUser();
    	Suscripcion subscription = getActiveSubscription(user);
        
        try {
            return mercadoPagoService.getUpcomingInvoice(subscription.getExternalSubscriptionId());
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo próxima factura: " + e.getMessage());
        }
    }

    // Métodos auxiliares privados

    private String determineChangeType(Plan currentPlan, Plan newPlan) {
        int comparison = currentPlan.getPrice().compareTo(newPlan.getPrice());
        if (comparison < 0) return "upgrade";
        if (comparison > 0) return "downgrade";
        return "same";
    }

    private BigDecimal calculateProratedAmount(Suscripcion subscription, Plan currentPlan, Plan newPlan) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime periodEnd = subscription.getCurrentPeriodEnd();
        
        // Días restantes del período actual
        long daysRemaining = ChronoUnit.DAYS.between(now, periodEnd);
        long totalDaysInPeriod = ChronoUnit.DAYS.between(subscription.getCurrentPeriodStart(), periodEnd);
        
        // Diferencia de precio diaria
        BigDecimal priceDifference = new BigDecimal(newPlan.getPrice()).subtract(new BigDecimal(currentPlan.getPrice()));
        BigDecimal dailyDifference = priceDifference.divide(BigDecimal.valueOf(totalDaysInPeriod), RoundingMode.HALF_UP);
        
        // Monto prorrateado
        return dailyDifference.multiply(BigDecimal.valueOf(daysRemaining));
    }
    
    @Transactional
    public SuscripcionDTO pauseSubscription() {
    	User user = userService.getCurrentUser();
    	Suscripcion subscription = getActiveSubscription(user);
        
        try {
            log.info("PAUSANDO suscripcion para usuario {}", user.getId());
            
        	Map<String, Object> mpDetails = mercadoPagoService.getPreapproval(subscription.getExternalSubscriptionId());
            String mpStatus = mpDetails.get("status").toString();
        	if("authorized".equals(mpStatus)) {
        		Map<String, Object> mpResponse = mercadoPagoService.pauseSubscription(subscription.getExternalSubscriptionId());
        		mpStatus = (String) mpResponse.get("status");
        	}
            if ("paused".equals(mpStatus)) {
                // Actualizar en nuestra BD
                subscription.setStatus(SuscripcionStatus.paused);
                subscription.setUpdatedAt(OffsetDateTime.now());
                subscription.setPausedAt(OffsetDateTime.now());
                
                // 3. IMPORTANTE: Calcular tiempo restante del período
                OffsetDateTime now = OffsetDateTime.now();
                OffsetDateTime periodEnd = subscription.getCurrentPeriodEnd();
                
                if (periodEnd.isAfter(now)) {
                    // Guardar días restantes para cuando se reactive
                    long daysRemaining = ChronoUnit.DAYS.between(now, periodEnd);
                    subscription.setPausedDaysRemaining(daysRemaining);
                }
                
                subscription = suscripcionRepository.save(subscription);
                
                log.info("✅ Suscripción pausada exitosamente para usuario {}", user.getId());
                return SuscripcionMapper.get().mapToDTO(subscription);
            } else {
                throw new RuntimeException("MercadoPago no pudo pausar la suscripción: " + mpStatus);
            }
            
        } catch (Exception e) {
            log.error("ERROR pausando suscripcion para usuario {}: {}", user.getId(), e.getMessage());
            throw new RuntimeException("No se pudo pausar la suscripción: " + e.getMessage(), e);
        }
    }

    /**
     * Crear nueva suscripción para usuarios con suscripción cancelada
     */
    @SuppressWarnings("unchecked")
	@Transactional
    public Object createNewSubscription(NewSubscriptionRequest request) {
        User user = userService.getCurrentUser();
        log.info("🚀 Creando nueva suscripción para usuario {}", user.getId());
        
        try {
            // 1. Verificar estado actual
            Suscripcion currentSubscription = user.getCurrentSubscription();
            
            if (currentSubscription != null && 
                ((currentSubscription.getStatus() == SuscripcionStatus.active && !currentSubscription.isCancelAtPeriodEnd()) || 
                 currentSubscription.getStatus() == SuscripcionStatus.paused)) {
                throw new IllegalStateException("Ya tienes una suscripción activa. Para cambiar de plan usa la función de cambio de plan.");
            }
            
            // 2. Obtener plan destino
            Plan targetPlan = planRepository.findById(request.getPlanId())
                    .orElseThrow(() -> new RuntimeException("Plan no encontrado"));
            
            // 4. Cancelar suscripción anterior si existe
            if (currentSubscription != null && currentSubscription.getStatus() != SuscripcionStatus.cancelled) {
                currentSubscription.setStatus(SuscripcionStatus.cancelled);
                currentSubscription.setCanceledAt(OffsetDateTime.now());
                currentSubscription.setUpdatedAt(OffsetDateTime.now());
                suscripcionRepository.save(currentSubscription);
                log.info("📋 Suscripción anterior cancelada para usuario {}", user.getId());
            }
            
            // 5. Crear nueva suscripción en BD (estado pending hasta confirmación de pago)
            OffsetDateTime start = OffsetDateTime.now();
    		start = start.withHour(0).withMinute(0).withSecond(0).withNano(0);
    		OffsetDateTime end = start.plusMonths(1).minusSeconds(1);
    		
            Suscripcion newSubscription = new Suscripcion();
            newSubscription.setUser(user);
            newSubscription.setPlan(targetPlan);
            newSubscription.setStatus(SuscripcionStatus.pending);
            newSubscription.setCreatedAt(OffsetDateTime.now());
            newSubscription.setUpdatedAt(OffsetDateTime.now());
            newSubscription.setStartedAt(OffsetDateTime.now());
            newSubscription.setCurrentPeriodStart(start);
            newSubscription.setCurrentPeriodEnd(end);
            newSubscription.setSource(SuscripcionSource.mercadopago);
            
            suscripcionRepository.save(newSubscription);
            log.info("✅ Nueva suscripción creada exitosamente para usuario {} con plan {}", user.getId(), targetPlan.getNombre());

            user.setCurrentSubscription(newSubscription);
    		userRepository.save(user);
    		
            Map<String, Object> mp = mercadoPagoService.createSubscription(user.getUsername(), targetPlan.getNombre(), targetPlan.getPrice().doubleValue(), newSubscription.getStartedAt(), newSubscription.getId());
            String externalId = (String) mp.get("id");
            String initPointUrl = (String) mp.get("init_point");
            
            MercadoPagoPreapproval mpDetails = new MercadoPagoPreapproval();
			mpDetails.setId((String) mp.get("id"));
			mpDetails.setSuscripcion(newSubscription);
			mpDetails.setMpStatus((String) mp.get("status"));
			mpDetails.setMpPayerId(Long.valueOf(mp.get("payer_id").toString()));
			mpDetails.setExternalReference(mp.get("external_reference").toString());
			mpDetails.setInitPointUrl(mp.get("init_point").toString());
			mpDetails.setDateCreated(OffsetDateTime.parse((String)mp.get("date_created")));
			mpDetails.setLastModified(OffsetDateTime.parse((String)mp.get("last_modified")));
			mpDetails.setNextPaymentDate(OffsetDateTime.parse((String)mp.get("next_payment_date")));
			if(mp.get("auto_recurring") != null) {
				Map<String, Object> autoRec = (Map<String, Object>)mp.get("auto_recurring");
				mpDetails.setTransactionAmount(BigDecimal.valueOf(Long.parseLong(autoRec.get("transaction_amount").toString())));
				mpDetails.setCurrencyId((String) autoRec.get("currency_id"));
			}
			mercadoPagoPreapprovalRepository.saveAndFlush(mpDetails);
			
			newSubscription.setExternalSubscriptionId((String) mp.get("id"));
			newSubscription.setMpPreapprovalDetails(mpDetails);
			suscripcionRepository.save(newSubscription);
         
            // 6. Retornar URL de pago
            return Map.of(
                "subscriptionId", newSubscription.getId(),
                "paymentUrl", initPointUrl,
                "externalId", externalId,
                "plan", targetPlan,
                "message", "Suscripción creada. Completa el pago para activarla."
            );
            
        } catch (Exception e) {
            log.error("ERROR creando nueva suscripcion para usuario {}: {}", user.getId(), e.getMessage());
            throw new RuntimeException("No se pudo crear la nueva suscripción: " + e.getMessage(), e);
        }
    }

    /**
     * Aplicar upgrade pagado - llamado desde webhook de MercadoPago
     * Este método se ejecuta solo DESPUÉS de confirmar el pago
     */
    @Transactional
    public void applyPaidUpgrade(Long subscriptionId, Long userId, Long previousPlanId, Long newPlanId) {
        try {
            log.info("💳 Aplicando upgrade pagado para suscripción {} - usuario: {}, plan: {} → {}", 
                     subscriptionId, userId, previousPlanId, newPlanId);
            
            // 1. Verificar que la suscripción existe
            Suscripcion subscription = suscripcionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ComexException("Suscripción no encontrada"));
            
            // 2. Verificar que pertenece al usuario correcto
            if (!subscription.getUser().getId().equals(userId)) {
                throw new ComexException("La suscripción no pertenece al usuario especificado");
            }
            
            // 3. Obtener planes
            Plan previousPlan = planRepository.findById(previousPlanId)
                    .orElseThrow(() -> new ComexException("Plan anterior no encontrado"));
            Plan newPlan = planRepository.findById(newPlanId)
                    .orElseThrow(() -> new ComexException("Plan nuevo no encontrado"));
            
            // 4. Aplicar el cambio de plan AHORA que el pago está confirmado
            subscription.setPlan(newPlan);
            subscription.setStatus(SuscripcionStatus.active);
            subscription.setPaid(true);
            subscription.setUpdatedAt(OffsetDateTime.now());
            
            // 5. Actualizar el plan en MercadoPago
            mercadoPagoService.updateSubscriptionPlan(
                subscription.getExternalSubscriptionId(),
                newPlan
            );
            
            subscription = suscripcionRepository.save(subscription);
            
            // 6. Registrar el cambio en el log
            logPlanChange(subscription, previousPlan, newPlan, "upgrade_completed");
            
            log.info("✅ Upgrade aplicado exitosamente - suscripción {} ahora en plan {}", 
                     subscriptionId, newPlan.getNombre());
                     
        } catch (Exception e) {
            log.error("ERROR aplicando upgrade pagado para suscripcion {}: {}", subscriptionId, e.getMessage());
            throw new ComexException("Error aplicando upgrade pagado: " + e.getMessage(), e);
        }
    }

}