package com.focuscomex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.focuscomex.model.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
	
	List<Notificacion> findTop100ByUsuarioIdOrderByIdDesc(Long usuarioId);
	
	// ✅ NUEVO: Buscar notificaciones no leídas de un usuario
	List<Notificacion> findByUsuarioIdAndReadFalseOrderByIdDesc(Long usuarioId);
}