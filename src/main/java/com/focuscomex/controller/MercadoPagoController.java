package com.focuscomex.controller;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.focuscomex.enums.TransactionStatus;
import com.focuscomex.model.Wallet;
import com.focuscomex.model.WalletTransaction;
import com.focuscomex.model.WebhookEvent;
import com.focuscomex.repository.PagoRepository;
import com.focuscomex.repository.SubscriptionPaymentRepository;
import com.focuscomex.repository.WalletRepository;
import com.focuscomex.repository.WalletTransactionRepository;
import com.focuscomex.repository.WebhookEventRepository;
import com.focuscomex.services.MercadoPagoService;
import com.focuscomex.services.SubscriptionManagementService;
import com.focuscomex.services.SubscriptionPaymentService;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("api/mercadopago")
@RequiredArgsConstructor
@Log4j2
public class MercadoPagoController {

	//private final ReportService reportService;
	//private final ReporteRepository reporteRepository;
	private final PagoRepository pagoRepository;
	private final WebhookEventRepository webhookEventRepo;
	private final SubscriptionPaymentService subscriptionPaymentService;
	private final SubscriptionManagementService subscriptionManagementService;
	private final MercadoPagoService mercadoPagoService;
	private final SubscriptionPaymentRepository paymentRepository;
	private final WalletTransactionRepository walletTransactionRepository;
	private final WalletRepository walletRepository;

	@SuppressWarnings("rawtypes")
	@PostMapping("webhook")
    public ResponseEntity<?> webhook(@RequestHeader Map<String,String> headers, @RequestBody Map<String, Object> payload) {
        try {
            String requestId = headers.getOrDefault("x-request-id", UUID.randomUUID().toString());
            if (headers.containsKey("x-hook-secret")) {
                return ResponseEntity.ok().header("x-hook-secret", headers.get("x-hook-secret")).body("ok");
            }
            String type = (String) payload.getOrDefault("type", "");
            String action = (String) payload.getOrDefault("action", "");
            Map data = (Map) payload.get("data");
            String id = data != null && data.get("id") != null ? String.valueOf(data.get("id")) : payload.get("id") != null ? String.valueOf(payload.get("id")) : null;

            if (id == null) {
                // nothing to do
                return ResponseEntity.ok("no-id");
            }
            
            String eventKey = type + ":" + id + ":" + action;
            if (webhookEventRepo.existsByEventKey(eventKey)) {
                return ResponseEntity.ok("already_processed");
            }
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.setEventKey(eventKey);
            webhookEvent.setRequestId(requestId);
            webhookEvent.setReceivedAt(OffsetDateTime.now());
            webhookEvent.setProcessed(false);
            webhookEventRepo.save(webhookEvent);
            
            try {
	            // Solo procesar notificaciones de payment
	            if ("payment".equals(type)) {
		            // Consultar el pago en MercadoPago
		            PaymentClient paymentClient = new PaymentClient();
		            Payment payment = paymentClient.get(Long.valueOf(id));
		
		            if (payment != null && payment.getMetadata() != null && payment.getExternalReference() != null) {
		                // Obtener producto desde metadata si es necesario
		            	String product = (String)payment.getMetadata().get("product");
		                String externalReference = payment.getExternalReference();
		            	
		                if("reporte".equals(product)) {
		                    Long reporteId = Long.parseLong(externalReference);
		                    
		                    /*
		                    Reporte reporte = reporteRepository.findById(reporteId).orElse(null);
		
		                    if (reporte != null) {
		                    	Pago pago = reporte.getPago();
		                    	
		                        // Procesar según el estado del pago
		                        switch (payment.getStatus()) {
		                            case "approved":
		                                // Pago aprobado - habilitar acceso completo
		                                pago.setStatus(PaymentStatus.APPROVED);

		                                reporte.setPagado(true);
		                                //reporteRepository.save(reporte);
		                                
		                                //reportService.sendMailReporte(reporte);
		                                
		                                break;
		
		                            case "rejected":
		                                // Pago rechazado
		                            	pago.setStatus(PaymentStatus.REJECTED);
		                                break;
		
		                            case "pending":
		                                // Pago pendiente
		                            	pago.setStatus(PaymentStatus.PENDING);
		                                break;
		                        }
                                pagoRepository.save(pago);
		                    }
		                    */
		                }else if("wallet".equals(product)) {
		                	// Procesar recarga de wallet
		                	
		                	Long walletTransactionId = Long.parseLong(externalReference);
		                    WalletTransaction walletTransaction = walletTransactionRepository.findById(walletTransactionId).orElse(null);
		                    Wallet wallet = walletTransaction.getWallet();
		                    
		                    if(walletTransaction != null) {
		                    	// Procesar según el estado del pago
		                        switch (payment.getStatus()) {
		                            case "approved":
		                                // Pago aprobado
		                                walletTransaction.setStatus(TransactionStatus.COMPLETED);
		                                walletTransaction.setExternalPaymentId(String.valueOf(payment.getId()));
		                                walletTransactionRepository.save(walletTransaction);
		                                
		                                wallet.setTokens(wallet.getTokens() + walletTransaction.getTokens());
		                                walletRepository.save(wallet);
		                                
		                                break;
		
		                            case "rejected":
		                                // Pago rechazado
		                                walletTransaction.setStatus(TransactionStatus.FAILED);
		                                walletTransaction.setExternalPaymentId(String.valueOf(payment.getId()));
		                                walletTransactionRepository.save(walletTransaction);
		                                break;

		                        }
		                    }
		                }else if("subscription_upgrade".equals(product)) {
		                	// Procesar pago de upgrade de suscripción
		                	log.info("🔄 Procesando pago de upgrade de suscripción - Payment ID: {}", id);
		                	
		                	Long subscriptionId = Long.parseLong(externalReference);
	                        
	                        // 2) Verificar que el pago fue aprobado
	                        if ("approved".equals(payment.getStatus())) {
	                            try {
	                                // 3) Extraer metadata del pago para obtener los IDs necesarios
	                                Map<String, Object> metadata = payment.getMetadata();
	                                Long userId = (long)(double) metadata.get("user_id");
	                                Long previousPlanId = (long)(double) metadata.get("previous_plan_id");
	                                Long newPlanId = (long)(double) metadata.get("new_plan_id");
	                                
	                                log.info("🚀 Aplicando upgrade pagado - Suscripción: {}, Usuario: {}, Plan: {} → {}", 
	                                		subscriptionId, userId, previousPlanId, newPlanId);
	                                
	                                // 4) Llamar al service para aplicar el upgrade
	                                subscriptionManagementService.applyPaidUpgrade(subscriptionId, userId, previousPlanId, newPlanId);
	                                
	                                log.info("✅ Upgrade aplicado exitosamente via webhook - Payment ID: {}", id);
	                                
	                            } catch (Exception e) {
	                                log.error("❌ Error aplicando upgrade pagado - Payment ID: {}, Error: {}", id, e.getMessage(), e);
	                                // El error ya está loggeado, el webhook retornará error
	                                throw e;
	                            }
	                        } else {
	                            log.info("⏳ Pago de upgrade no aprobado aún - Status: {} - Payment ID: {}", payment.getStatus(), id);
	                        }
		                }
		            }
	            }
            }catch(Exception ex) {
				throw ex;
			}finally {
				webhookEvent.setProcessed(true);
				webhookEvent.setProcessedAt(OffsetDateTime.now());
				webhookEventRepo.save(webhookEvent);
			}
			return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Error procesando webhook de MercadoPago: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error procesando webhook");
        }
    }
	
	@SuppressWarnings({ "rawtypes" })
	@PostMapping("webhook/subs")
    public ResponseEntity<?> webhookSubs(@RequestHeader Map<String,String> headers, @RequestBody Map<String, Object> payload) {
        try {
            String requestId = headers.getOrDefault("x-request-id", UUID.randomUUID().toString());
            if (headers.containsKey("x-hook-secret")) {
                return ResponseEntity.ok().header("x-hook-secret", headers.get("x-hook-secret")).body("ok");
            }
            String type = (String) payload.getOrDefault("type", "");
            String action = (String) payload.getOrDefault("action", "");
            Integer version = (Integer) payload.getOrDefault("version", 0);
            Map data = (Map) payload.get("data");
            String id = data != null && data.get("id") != null ? String.valueOf(data.get("id")) : payload.get("id") != null ? String.valueOf(payload.get("id")) : null;
            
            if (id == null) {
                // nothing to do
                return ResponseEntity.ok("no-id");
            }
            
            String eventKey = type + ":" + id + ":" + action + ":" + version;
            if (webhookEventRepo.existsByEventKey(eventKey)) {
                return ResponseEntity.ok("already_processed");
            }
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.setEventKey(eventKey);
            webhookEvent.setRequestId(requestId);
            webhookEvent.setReceivedAt(OffsetDateTime.now());
            webhookEvent.setProcessed(false);
            webhookEventRepo.save(webhookEvent);
            
            try {
	            if ("subscription_preapproval".equals(type) || "preapproval".equals(type) || "preapproval.updated".equals(type) || "preapproval.cancelled".equals(type)) {
	            	Map<String, Object> mpDetails = mercadoPagoService.getPreapproval(id);
	            	subscriptionPaymentService.processPreapprovalData(id, mpDetails);
	            }else if ("subscription_authorized_payment".equals(type) || "authorized_payment".equals(type)) {
	            	// responder rápido (ack) y procesar (puedes encolar)
	                new Thread(() -> {
	                    try {
	                    	// 1) Idempotencia: si ya existe el pago, devolvemos
	                        if (paymentRepository.existsById(id)) {
	                            log.info("Authorized payment {} ya procesado, ignoring", id);
	                            return;
	                        }
	    	            	Map<String, Object> mpDetails = mercadoPagoService.getAuthorizedPayment(id);
	                        subscriptionPaymentService.processAuthorizedPaymentData(id, mpDetails);
	                    } catch (Exception e) {
	                        log.error("Error processing authorized_payment async: {}", e.getMessage(), e);
	                    }
	                }).start();
	                return ResponseEntity.ok("ack");
	            }
            }catch(Exception ex) {
				throw ex;
			}finally {
				webhookEvent.setProcessed(true);
				webhookEvent.setProcessedAt(OffsetDateTime.now());
				webhookEventRepo.save(webhookEvent);
			}
			return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Error procesando webhook de MercadoPago: ", e);
            return ResponseEntity.internalServerError().body("Error procesando webhook");
        }
    }

}
