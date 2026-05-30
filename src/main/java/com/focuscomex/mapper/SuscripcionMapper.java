package com.focuscomex.mapper;

import com.focuscomex.dto.SuscripcionDTO;
import com.focuscomex.model.Suscripcion;

public class SuscripcionMapper implements Mapper<SuscripcionDTO, Suscripcion> {

	private UsuarioMapper usuarioMapper = null;
	private PlanMapper planMapper = null;
	
    public static SuscripcionMapper get() {
        return new SuscripcionMapper();
    }
    
    public SuscripcionMapper withUser() {
    	usuarioMapper = UsuarioMapper.get();
		return this;
	}
    
    public SuscripcionMapper withPlan() {
		planMapper = PlanMapper.get();
		return this;
    }

    @Override
    public SuscripcionDTO mapToDTO(Suscripcion entity) {
        if (entity == null) {
            return null;
        }
        SuscripcionDTO dto = new SuscripcionDTO();
        dto.setId(entity.getId());
        if(usuarioMapper != null) {
            dto.setUser(usuarioMapper.mapToDTO(entity.getUser()));
        }
        if(planMapper != null) {
			dto.setPlan(planMapper.mapToDTO(entity.getPlan()));
		}
        dto.setGrantedBy(entity.getGrantedBy());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCurrentPeriodStart(entity.getCurrentPeriodStart());
        dto.setCurrentPeriodEnd(entity.getCurrentPeriodEnd());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setPausedAt(entity.getPausedAt());
        dto.setResumedAt(entity.getResumedAt());
        dto.setPausedDaysRemaining(entity.getPausedDaysRemaining());
        dto.setStatus(entity.getStatus());
        dto.setSource(entity.getSource());
        dto.setPaid(entity.isPaid());
        dto.setExternalProvider(entity.getExternalProvider());
        dto.setExternalSubscriptionId(entity.getExternalSubscriptionId());
        dto.setCancelAtPeriodEnd(entity.isCancelAtPeriodEnd());
        dto.setCanceledAt(entity.getCanceledAt());
        return dto;
    }
}
