package com.focuscomex.mapper;

import com.focuscomex.dto.NotificacionDTO;
import com.focuscomex.model.Notificacion;

public class NotificacionMapper implements Mapper<NotificacionDTO, Notificacion> {

    public static NotificacionMapper get() {
        return new NotificacionMapper();
    }

    @Override
    public NotificacionDTO mapToDTO(Notificacion entity) {
        if (entity == null) return null;
        NotificacionDTO dto = new NotificacionDTO();
        dto.setId(entity.getId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setRead(entity.isRead());
        dto.setAction(entity.getAction());

        return dto;
    }
}
