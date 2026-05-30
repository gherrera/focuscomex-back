package com.focuscomex.services;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.focuscomex.dto.NotificacionDTO;
import com.focuscomex.dto.UsuarioDTO;
import com.focuscomex.enums.SuscripcionStatus;
import com.focuscomex.enums.UserType;
import com.focuscomex.exceptions.ComexException;
import com.focuscomex.mapper.NotificacionMapper;
import com.focuscomex.mapper.UsuarioMapper;
import com.focuscomex.model.Notificacion;
import com.focuscomex.model.Suscripcion;
import com.focuscomex.model.User;
import com.focuscomex.model.Wallet;
import com.focuscomex.repository.NotificacionRepository;
import com.focuscomex.repository.SuscripcionRepository;
import com.focuscomex.repository.UserRepository;
import com.focuscomex.security.CurrentUser;
import com.focuscomex.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final SuscripcionRepository suscripcionRepository;
	private final NotificacionRepository notificacionRepository;
	private final WalletService walletService;

	public UsuarioDTO saveUser(UsuarioDTO userDTO) {
	    User u = null;
	    if(userDTO.getId() != null) {
	    	u = userRepository.findById(userDTO.getId())
	    			.orElseThrow(() -> new ComexException("Usuario no existe: " + userDTO.getId()));
	    	if(userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
			    String encoded = passwordEncoder.encode(userDTO.getPassword());
			    u.setPassword(encoded);
	    	}
		    u.setUpdatedAt(LocalDateTime.now());
	    }else {
		    u = new User();
		    String encoded = passwordEncoder.encode(userDTO.getPassword());
		    u.setPassword(encoded);
		    u.setCreatedAt(LocalDateTime.now());
		    u.setType(UserType.REGULAR);
	    }
	    u.setUsername(userDTO.getUsername());
	    u.setName(userDTO.getName());
	    u.setEnabled(userDTO.isEnabled());

	    userRepository.save(u);
	    return UsuarioMapper.get().mapToDTO(u);
	}
	
	public UsuarioDTO addTokensToUser(Long userId, Integer tokens) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ComexException("Usuario no existe: " + userId));
		
		walletService.grantFreeTokens(user, tokens, "Recibe " + tokens + " tokens de regalo");
		
	    return UsuarioMapper.get().mapToDTO(user);
	}
	
	public User getCurrentUser() {
		CurrentUser currentUser = SecurityUtils.getCurrentUser();
		User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no existe" + currentUser.getId()));
		
		return user;
	}
	
	public boolean isActiveSubscription(Suscripcion suscripcion) {
		if(suscripcion != null && (SuscripcionStatus.active.equals(suscripcion.getStatus()) || SuscripcionStatus.trialing.equals(suscripcion.getStatus()))) {
			if(suscripcion.getCurrentPeriodEnd() == null || (suscripcion.getCurrentPeriodEnd().isAfter(OffsetDateTime.now()) && suscripcion.getCurrentPeriodStart().isBefore(OffsetDateTime.now()))) {
				return true;
			}
		}
		return false;
	}
	
	public UsuarioDTO getUserDTOById(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("Usuario no existe" + userId));
		
		UsuarioDTO userDTO = UsuarioMapper.get().mapToDTO(user);
		
		if(user.getType().equals(UserType.REGULAR) && user.getCurrentSubscription() != null && user.getCurrentSubscription().getPlan() != null) {
			Suscripcion suscripcion = user.getCurrentSubscription();
			if(suscripcion.getCurrentPeriodEnd() != null && (SuscripcionStatus.trialing.equals(suscripcion.getStatus()) || SuscripcionStatus.active.equals(suscripcion.getStatus()))) {
				if(suscripcion.getCurrentPeriodEnd().isBefore(OffsetDateTime.now())) {
					suscripcion.setStatus(SuscripcionStatus.expired);
					
					suscripcionRepository.save(suscripcion);
					
					userDTO = UsuarioMapper.get().mapToDTO(user);
				}
			}
			
			Wallet wallet = walletService.getOrCreateWallet(user);
			userDTO.setTokens(wallet.getTokens());
		}
	    return userDTO;
	}
	
	public UsuarioDTO getCurrentUserDTO() {
		CurrentUser user = SecurityUtils.getCurrentUser();
	    return getUserDTOById(user.getId());
		
	}
	
	public UsuarioDTO updateUser(UsuarioDTO userDTO) {
		User user = getCurrentUser();
		user.setName(userDTO.getName());
		if(userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
		    String encoded = passwordEncoder.encode(userDTO.getPassword());
			user.setPassword(encoded);
		}
		userRepository.save(user);
	    return UsuarioMapper.get().mapToDTO(user);
	}
	
	public List<NotificacionDTO> getUserNotifications() {
		User user = getCurrentUser();
		NotificacionMapper mapper = NotificacionMapper.get();
		return notificacionRepository.findTop100ByUsuarioIdOrderByIdDesc(user.getId()).stream()
				.map(mapper::mapToDTO)
				.toList();
	}

	public NotificacionDTO readNotification(Long notificationId) {
		User user = getCurrentUser();
		var notificacion = notificacionRepository.findById(notificationId)
				.orElseThrow(() -> new ComexException("Notificación no existe: " + notificationId));
		if(!notificacion.getUsuario().getId().equals(user.getId())) {
			throw new ComexException("Notificación no pertenece al usuario: " + notificationId);
		}
		if(notificacion.isRead()) {
			throw new ComexException("Notificación ya leida: " + notificationId);
		}
		notificacion.setRead(true);
		notificacion.setUpdatedAt(new Date());
		notificacionRepository.save(notificacion);
	    return NotificacionMapper.get().mapToDTO(notificacion);
	}
	
	/**
	 * ✅ NUEVO: Marcar todas las notificaciones no leídas como leídas
	 */
	public List<NotificacionDTO> readAllNotifications() {
		User user = getCurrentUser();
		Date now = new Date();
		
		// Buscar todas las notificaciones no leídas del usuario
		List<Notificacion> unreadNotifications = notificacionRepository
			.findByUsuarioIdAndReadFalseOrderByIdDesc(user.getId());
		
		if (unreadNotifications.isEmpty()) {
			return List.of(); // No hay notificaciones no leídas
		}
		
		// Marcar todas como leídas
		unreadNotifications.forEach(notificacion -> {
			notificacion.setRead(true);
			notificacion.setUpdatedAt(now);
		});
		
		// Guardar en lote
		List<Notificacion> updatedNotifications = notificacionRepository.saveAll(unreadNotifications);
		
		// Retornar DTOs actualizados
		NotificacionMapper mapper = NotificacionMapper.get();
		return updatedNotifications.stream()
				.map(mapper::mapToDTO)
				.toList();
	}
	
	public List<UsuarioDTO> getUsers() {
		return userRepository.findAllByOrderByUsernameAsc().stream().filter(u -> u.getType().equals(UserType.REGULAR))
				.map(UsuarioMapper.get()::mapToDTO)
				.toList();
	}

}
