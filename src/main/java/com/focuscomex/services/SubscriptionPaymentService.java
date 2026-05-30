package com.focuscomex.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.focuscomex.enums.SuscripcionStatus;
import com.focuscomex.model.MercadoPagoPreapproval;
import com.focuscomex.model.Plan;
import com.focuscomex.model.SubscriptionPayment;
import com.focuscomex.model.Suscripcion;
import com.focuscomex.model.User;
import com.focuscomex.repository.MercadoPagoPreapprovalRepository;
import com.focuscomex.repository.SubscriptionPaymentRepository;
import com.focuscomex.repository.SuscripcionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class SubscriptionPaymentService {

	private final MercadoPagoPreapprovalRepository mercadoPagoPreapprovalRepository;
	private final SubscriptionPaymentRepository paymentRepository;
	private final SuscripcionRepository subscriptionRepo;
	private final WalletService walletService;

    /**
     * Lógica de negocio para preapproval (transaccional)
     */
    @SuppressWarnings("unchecked")
	@Transactional
    public void processPreapprovalData(String preapprovalId, Map<String, Object> mpDetails) {
    	String mpStatus = Optional.ofNullable(mpDetails.get("status")).map(Object::toString).orElse("unknown");
        Long externalRef = null;
        Object extObj = mpDetails.get("external_reference");
        if (extObj != null) {
            if (extObj instanceof Number) {
                externalRef = ((Number) extObj).longValue();
            } else {
                try {
                    externalRef = Long.valueOf(extObj.toString());
                } catch (NumberFormatException nfe) {
                    log.warn("Invalid external_reference in preapproval details: {}", extObj);
                }
            }
        }
        Suscripcion sub = subscriptionRepo.findById(externalRef).orElseThrow(() -> new RuntimeException("Suscripcion no encontrada para preapproval ID: " + extObj));
    	MercadoPagoPreapproval mpPreapproval = sub.getMpPreapprovalDetails();
        
        if(mpPreapproval != null && mpPreapproval.getId().equals(preapprovalId)) {
        	sub.setStatus(mapStatus(mpStatus));
        	mpPreapproval.setMpStatus(mpStatus);

            // parse auto_recurring
        	Map<String,Object> auto = (Map<String,Object>) mpDetails.get("auto_recurring");
            if (auto != null && auto.get("next_payment_date") != null) {
            	sub.setCurrentPeriodStart(OffsetDateTime.parse(auto.get("start_date").toString()));
            	sub.setCurrentPeriodEnd(OffsetDateTime.parse(auto.get("next_payment_date").toString()));

            	mpPreapproval.setNextPaymentDate(auto.get("next_payment_date") != null ? OffsetDateTime.parse(auto.get("next_payment_date").toString()) : null);
            }
            if(SuscripcionStatus.active.equals(sub.getStatus())) {
				sub.setPaid(true);
				
				Plan plan = sub.getPlan();
				User user = sub.getUser();
				Suscripcion currentSuscripcion = user.getCurrentSubscription();
				
				if(plan != null && plan.getTokensIncluded() != null && currentSuscripcion != null && sub.getId().equals(currentSuscripcion.getId()) && user.getTokensPlan() == null) {
					int tokensToGrant = plan.getTokensIncluded();
					if(user.getTokensFree() != null) {
						tokensToGrant -= user.getTokensFree();
						walletService.grantFreeTokens(user, tokensToGrant, "Tokens adicionales por plan " + plan.getNombre());
					}else {
						walletService.grantFreeTokens(user, tokensToGrant, "Tokens incluidos en plan " + plan.getNombre());
					}
					user.setTokensPlan(tokensToGrant);
				}
			} else {
				sub.setPaid(false);
				if(SuscripcionStatus.cancelled.equals(sub.getStatus())) {
					sub.setCanceledAt(OffsetDateTime.now());
				}
			}
            
        	mercadoPagoPreapprovalRepository.save(mpPreapproval);
            subscriptionRepo.save(sub);
        }
    }
    
    /**
     * Lógica de negocio para authorized payment (transaccional)
     */
    @Transactional
    public void processAuthorizedPaymentData(String authorizedPaymentId, Map<String,Object> mpDetails) {
        // 3) Extraer campos relevantes
        String mpId = Optional.ofNullable(mpDetails.get("id")).map(Object::toString).orElse(authorizedPaymentId);
        String preapprovalId = Optional.ofNullable(mpDetails.get("preapproval_id")).map(Object::toString).orElse(null);
        String status = Optional.ofNullable(mpDetails.get("status")).map(Object::toString).orElse(null);
        String statusDetail = Optional.ofNullable(mpDetails.get("status_detail")).map(Object::toString).orElse(null);
        BigDecimal amount = Optional.ofNullable(mpDetails.get("transaction_amount"))
                .map(Object::toString).map(BigDecimal::new).orElse(null);
        String currency = Optional.ofNullable(mpDetails.get("currency_id")).map(Object::toString).orElse(null);
        OffsetDateTime debitDate = Optional.ofNullable(mpDetails.get("debit_date"))
        		.map(Object::toString).map(OffsetDateTime::parse).orElse(null);
        OffsetDateTime nextRetryDate = Optional.ofNullable(mpDetails.get("next_retry_date"))
        		.map(Object::toString).map(OffsetDateTime::parse).orElse(null);
        OffsetDateTime dateCreated = Optional.ofNullable(mpDetails.get("date_created"))
        		.map(Object::toString).map(OffsetDateTime::parse).orElse(null);
        String externalRef = Optional.ofNullable(mpDetails.get("external_reference")).map(Object::toString).orElse(null);
        Long subId = externalRef == null ? null : Long.valueOf(externalRef);
        
        // 4) Buscar suscripción
        if (preapprovalId == null) {
            log.warn("authorized_payment {} no tiene preapproval_id", authorizedPaymentId);
            return;
        }

        MercadoPagoPreapproval mpPreapproval = mercadoPagoPreapprovalRepository.findById(preapprovalId)
                .orElseGet(() -> {
                    // Si no existe la subs, puedes optar por crearla parcialmente con datos mínimos,
                    // o loguearla para revisión (aquí optamos por logear y abortar)
                    log.warn("No existe Subscription con id {}. Creación automática no implementada.", preapprovalId);
                    return null;
                });

        if (mpPreapproval == null) return;

        mpPreapproval.setNextPaymentDate(nextRetryDate);
        
        Suscripcion subscription = mpPreapproval.getSuscripcion();

        if ("processed".equalsIgnoreCase(status)) {
            // preferir dateApproved, fallback a debitDate
        	OffsetDateTime start = debitDate;
            if (start == null) start = OffsetDateTime.now();
            // calcular end según frecuencia (ejemplo 1 mes)
            OffsetDateTime end = start.plus(Period.ofMonths(1)); // o usa addPeriod según tu plan

            mpPreapproval.setNextPaymentDate(end);
			mpPreapproval.setMpStatus("active");
            
            if(subscription != null && subscription.getId().equals(subId)) {
            	subscription.setCurrentPeriodStart(start);
				subscription.setCurrentPeriodEnd(end);
				subscription.setStatus(SuscripcionStatus.active);
				subscription.setPaid(true);
				subscriptionRepo.save(subscription);
            }
        } else if ("recycling".equalsIgnoreCase(status)) {
            // marcar intento fallido; guardar retry_attempt, next_retry_date
        } else if ("cancelled".equalsIgnoreCase(status)) {
            if(subscription != null && subscription.getId().equals(subId)) {
				subscription.setStatus(SuscripcionStatus.cancelled);
				subscription.setPaid(false);
				subscription.setCanceledAt(OffsetDateTime.now());
				subscriptionRepo.save(subscription);
				
				mpPreapproval.setMpStatus(status);
            }
        }

        mpPreapproval.setLastPaymentDate(dateCreated);
        mercadoPagoPreapprovalRepository.save(mpPreapproval); // cascade guarda payment

        // 5) Crear SubscriptionPayment y relacionar
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .id(mpId)
                .subscription(mpPreapproval)
                .amount(amount)
                .currency(currency)
                .status(status)
                .statusDetail(statusDetail)
                .approvedAt(debitDate)
                .build();

        // Persistir
        paymentRepository.save(payment);

        log.info("Processed authorized_payment {} linked to subscription {}", mpId, preapprovalId);
    }
    
    // -------------------------------------------------------------------------
    // 3️⃣ Procesar PAYMENT (payment.created / payment.updated)
    // -------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
	public void processPaymentData(String paymentId, Map<String,Object> mpDetails) {
    	String status = Optional.ofNullable(mpDetails.get("status")).map(Object::toString).orElse(null);
        String statusDetail = Optional.ofNullable(mpDetails.get("status_detail")).map(Object::toString).orElse(null);
        BigDecimal amount = Optional.ofNullable(mpDetails.get("transaction_amount"))
                .map(Object::toString).map(BigDecimal::new).orElse(null);
        String currency = Optional.ofNullable(mpDetails.get("currency_id")).map(Object::toString).orElse(null);
        OffsetDateTime dateCreated = Optional.ofNullable(mpDetails.get("date_created"))
                .map(Object::toString).map(OffsetDateTime::parse).orElse(null);

        SubscriptionPayment payment = paymentRepository.findById(paymentId)
                .orElse(new SubscriptionPayment());
        payment.setStatus(status);
        payment.setStatusDetail(statusDetail);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setCreatedAt(dateCreated);

        if (mpDetails.get("metadata") != null) {
        	Map<String,Object> metadata = (Map<String,Object>) mpDetails.get("metadata");
        	if(metadata.containsKey("preapproval_id")) {
        		MercadoPagoPreapproval mpPreapproval = mercadoPagoPreapprovalRepository.findById(metadata.get("preapproval_id").toString())
						.orElseThrow(() -> new RuntimeException("No existe Subscription con id " + metadata.get("preapproval_id").toString()));
        		payment.setSubscription(mpPreapproval);
				
				if ("approved".equalsIgnoreCase(payment.getStatus())) {
					mpPreapproval.setMpStatus("active");
                } else if ("rejected".equalsIgnoreCase(payment.getStatus())) {
                	mpPreapproval.setMpStatus("past_due");
                }
				mercadoPagoPreapprovalRepository.save(mpPreapproval);
				
				Suscripcion sub = mpPreapproval.getSuscripcion();
				sub.setStatus(mapStatus(mpPreapproval.getMpStatus()));
				subscriptionRepo.save(sub);
        	}
        }

        paymentRepository.save(payment);
    }

    private SuscripcionStatus mapStatus(String mpStatus) {
        if (mpStatus == null) return SuscripcionStatus.unknown;
        switch (mpStatus.toLowerCase()) {
            case "pending": return SuscripcionStatus.pending;
            case "authorized":
            case "active": return SuscripcionStatus.active;
            case "paused": return SuscripcionStatus.paused;
            case "cancelled": return SuscripcionStatus.cancelled;
            default: return SuscripcionStatus.unknown;
        }
    }
}
