package com.focuscomex.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.focuscomex.dto.NotificacionDTO;
import com.focuscomex.dto.UsuarioDTO;
import com.focuscomex.services.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService; 

	@PostMapping("user")
	public UsuarioDTO updateUser(@RequestBody UsuarioDTO usuario) {
		try {
			return userService.updateUser(usuario);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@GetMapping("user")
	public UsuarioDTO getCurrentUser() {
		try {
			return userService.getCurrentUserDTO();
		}catch(Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@GetMapping("user/notifications")
	public List<NotificacionDTO> getUserNotifications() {
		try {
			return userService.getUserNotifications();
		}catch(Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@GetMapping("user/notifications/{notificationId}/read")
	public NotificacionDTO readNotification(@PathVariable Long notificationId) {
		try {
			return userService.readNotification(notificationId);
		}catch(Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/**
	 * ✅ NUEVO: Marcar todas las notificaciones como leídas
	 */
	@PostMapping("user/notifications/read-all")
	public List<NotificacionDTO> readAllNotifications() {
		try {
			return userService.readAllNotifications();
		}catch(Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@GetMapping("adm/users")
	public List<UsuarioDTO> getUsers() {
		try {
			return userService.getUsers();
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@GetMapping("adm/user/{userId}")
	public UsuarioDTO getUser(@PathVariable Long userId) {
		try {
			return userService.getUserDTOById(userId);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@PostMapping("adm/user")
	public UsuarioDTO saveUser(@RequestBody UsuarioDTO usuario) {
		try {
			return userService.saveUser(usuario);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@PostMapping("adm/user/{userId}/tokens")
	public UsuarioDTO addTokensToUser(@PathVariable Long userId, @RequestBody UsuarioDTO usuario) {
		try {
			return userService.addTokensToUser(userId, usuario.getTokens());
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	

}
