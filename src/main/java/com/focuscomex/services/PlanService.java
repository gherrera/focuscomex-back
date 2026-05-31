package com.focuscomex.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.focuscomex.dto.PlanDTO;
import com.focuscomex.dto.SuscripcionDTO;
import com.focuscomex.dto.UsuarioDTO;
import com.focuscomex.enums.SuscripcionSource;
import com.focuscomex.enums.SuscripcionStatus;
import com.focuscomex.enums.UserType;
import com.focuscomex.exceptions.ComexException;
import com.focuscomex.mapper.PlanMapper;
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

@Service
@RequiredArgsConstructor
public class PlanService {

	private final PlanRepository planRepository;
	private final UserService userService;
	private final SuscripcionRepository suscripcionRepository;
	private final MercadoPagoPreapprovalRepository mercadoPagoPreapprovalRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final MercadoPagoService mercadoPagoService;
	private final WalletService walletService;

	@Transactional
	public PlanDTO savePlan(PlanDTO planDTO) {
	    Plan p = null;
	    if(planDTO.getId() != null) {
	    	Plan plan = planRepository.findById(planDTO.getId())
	    			.orElseThrow(() -> new ComexException("Plan no existe: " + planDTO.getId()));
	    	
	    	plan.setCurrent(false);
	    	plan.setValidUntil(LocalDateTime.now());
		    planRepository.save(plan);
		    
		    p = new Plan();
		    p.setMasterPlanId(plan.getMasterPlanId());
		    p.setInheritedFrom(plan.getId());
		    p.setVersionNumber(plan.getVersionNumber() + 1);		    
	    }else {
		    p = new Plan();
		    p.setVersionNumber(1);
	    }
	    if(planDTO.isTrial()) {
	    	List<Plan> planes = planRepository.findAllByCurrentIsTrueOrderByNombreAsc();
	    	planes.stream().forEach(p1 -> {
	    		if(p1.isTrial()) {
	    			throw new ComexException("Ya existe un plan de prueba activo: " + p1.getNombre());
	    		}
	    	});
	    }
	    
	    p.setCreatedAt(LocalDateTime.now());
	    p.setValidFrom(LocalDateTime.now());
	    p.setNombre(planDTO.getNombre());
	    p.setNombreGrupo(planDTO.getNombreGrupo());
	    p.setPrice(planDTO.getPrice());
	    p.setCurrent(true);
	    p.setTrial(planDTO.isTrial());
	    p.setActive(planDTO.isActive());
	    p.setPrivatePlan(planDTO.isPrivatePlan());
	    p.setTokensIncluded(planDTO.getTokensIncluded());

	    planRepository.save(p);
	    if(planDTO.getId() == null) {
	    	p.setMasterPlanId(p.getId());
	    	planRepository.save(p);
	    }
	    return PlanMapper.get().mapToDTO(p);
	}

	public List<PlanDTO> getPlanes(boolean includePublic) {
		return planRepository.findAllByCurrentIsTrueOrderByNombreAsc().stream()
				.filter(p -> includePublic || !p.isPrivatePlan())
				.map(PlanMapper.get()::mapToDTO)
				.toList();
	}
	
	public void deletePlan(Long id) {
		Plan p = planRepository.findById(id)
				.orElseThrow(() -> new ComexException("Plan no existe: " + id));
		planRepository.delete(p);
	}
	
	public SuscripcionDTO saveSuscripcion(SuscripcionDTO suscripcion) {
		User user = userService.getCurrentUser();
		
		Plan plan;
		User suscriptor;
		if(suscripcion.getPlan() == null) {
			throw new ComexException("Plan es requerido para crear suscripción");
		}else {
			plan = planRepository.findById(suscripcion.getPlan().getId())
					.orElseThrow(() -> new ComexException("Plan no existe: " + suscripcion.getPlan().getId()));
		}
		if(suscripcion.getUser() == null) {
			throw new ComexException("Usuario es requerido para crear suscripción");
		}else {
			if(user.getType().equals(UserType.REGULAR)) {
				if(!user.getId().equals(suscripcion.getUser().getId())) {
					throw new ComexException("Usuario no tiene permisos para crear suscripción a otro usuario");
				}
			}
			suscriptor = userRepository.findById(suscripcion.getUser().getId())
					.orElseThrow(() -> new ComexException("Usuario no existe: " + suscripcion.getUser().getId()));
		}
		
		Suscripcion currentSuscripcion = suscriptor.getCurrentSubscription();
		if(currentSuscripcion == null) {
			currentSuscripcion = new Suscripcion();
		}
		
		currentSuscripcion.setCurrentPeriodStart(suscripcion.getCurrentPeriodStart());
		currentSuscripcion.setCurrentPeriodEnd(suscripcion.getCurrentPeriodEnd());
		currentSuscripcion.setGrantedBy(user.getUsername());
		currentSuscripcion.setPlan(plan);
		currentSuscripcion.setUser(suscriptor);
		currentSuscripcion.setSource(SuscripcionSource.backoffice);
		currentSuscripcion.setStatus(suscripcion.getStatus());
		currentSuscripcion.setStartedAt(OffsetDateTime.now());
		
		suscripcionRepository.save(currentSuscripcion);
		
		suscriptor.setCurrentSubscription(currentSuscripcion);
		userRepository.save(suscriptor);
		
		return SuscripcionMapper.get()
				.withPlan()
				.mapToDTO(currentSuscripcion);
	}
	
	@SuppressWarnings("unchecked")
	@Transactional
	public Map<String, Object> createUserWithPlan(Long planId, UsuarioDTO usuarioDTO) {
		Plan plan = planRepository.findById(planId).orElseThrow(() -> new ComexException("Plan no existe: " + planId));
		if(userRepository.existsByUsername(usuarioDTO.getUsername())) {
			throw new ComexException("Usuario ya existe");
		}
		
	    String encoded = passwordEncoder.encode(usuarioDTO.getPassword());

		User user = new User();
		user.setName(usuarioDTO.getName());
		user.setUsername(usuarioDTO.getUsername());
		user.setPassword(encoded);
		user.setType(UserType.REGULAR);
		user.setEnabled(true);
		user.setCreatedAt(LocalDateTime.now());
		user.setCompany(usuarioDTO.getCompany());
		userRepository.save(user);

		Suscripcion suscripcion = new Suscripcion();

		OffsetDateTime start = OffsetDateTime.now();
		start = start.withHour(0).withMinute(0).withSecond(0).withNano(0);
		OffsetDateTime end = start.plusMonths(1).minusSeconds(1);
		if(plan.isTrial()) {
			end = start.plusDays(7).minusSeconds(1);
			suscripcion.setSource(SuscripcionSource.trial);
			suscripcion.setStatus(SuscripcionStatus.trialing);
			
			if(plan.getTokensIncluded() != null && plan.getTokensIncluded() > 0) {
				user.setTokensFree(plan.getTokensIncluded());
				walletService.grantFreeTokens(user, plan.getTokensIncluded(), "Tokens incluidos en plan " + plan.getNombre());
			}
		}else {
			suscripcion.setExternalProvider("mercadopago");
			suscripcion.setSource(SuscripcionSource.mercadopago);
			suscripcion.setStatus(SuscripcionStatus.pending);
		}
		suscripcion.setPaid(false);
		suscripcion.setPlan(plan);
		suscripcion.setUser(user);
		suscripcion.setStartedAt(OffsetDateTime.now());
		suscripcion.setCurrentPeriodStart(start);
		suscripcion.setCurrentPeriodEnd(end);
		suscripcionRepository.saveAndFlush(suscripcion);
		
		user.setCurrentSubscription(suscripcion);
		userRepository.save(user);
		
		if(!plan.isTrial()) {
			Map<String, Object> mp = mercadoPagoService.createSubscription(
					user.getUsername(), 
					plan.getNombre(), 
					plan.getPrice().doubleValue(), 
					suscripcion.getStartedAt(), 
					suscripcion.getId()
			);
			
			MercadoPagoPreapproval mpDetails = new MercadoPagoPreapproval();
			mpDetails.setId((String) mp.get("id"));
			mpDetails.setSuscripcion(suscripcion);
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
			
			suscripcion.setExternalSubscriptionId((String) mp.get("id"));
			suscripcion.setMpPreapprovalDetails(mpDetails);
			suscripcionRepository.save(suscripcion);
			
			return Map.of("initPoint", mp.get("init_point").toString());
		}
		return Map.of("user", user);
	}
}
