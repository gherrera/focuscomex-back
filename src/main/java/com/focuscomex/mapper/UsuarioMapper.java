package com.focuscomex.mapper;

import com.focuscomex.dto.UsuarioDTO;
import com.focuscomex.model.User;

public class UsuarioMapper implements Mapper<UsuarioDTO, User> {

	private SuscripcionMapper suscripcionMapper = SuscripcionMapper.get().withPlan();
	
    public static UsuarioMapper get() {
        return new UsuarioMapper();
    }

    @Override
    public UsuarioDTO mapToDTO(User entity) {
        UsuarioDTO dto = new UsuarioDTO();

        dto.setId(entity.getId());
        dto.setUsername(entity.getUsername());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setCompany(entity.getCompany());

        dto.setLastLogin(entity.getLastLogin());
        dto.setEnabled(entity.isEnabled());
        
        if(entity.getCurrentSubscription() != null) {
			dto.setCurrentSubscription(suscripcionMapper.mapToDTO(entity.getCurrentSubscription()));
		}
       
        return dto;
    }
}