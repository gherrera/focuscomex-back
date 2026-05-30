package com.focuscomex.mapper;

import com.focuscomex.dto.PlanDTO;
import com.focuscomex.model.Plan;

public class PlanMapper implements Mapper<PlanDTO, Plan> {
	
    public static PlanMapper get() {
        return new PlanMapper();
    }

    @Override
    public PlanDTO mapToDTO(Plan entity) {
        PlanDTO dto = new PlanDTO();
        dto.setId(entity.getId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setValidFrom(entity.getValidFrom());
        dto.setValidUntil(entity.getValidUntil());
        dto.setNombre(entity.getNombre());
        dto.setNombreGrupo(entity.getNombreGrupo());
        dto.setActive(entity.isActive());
        dto.setPrice(entity.getPrice());
        dto.setVersionNumber(entity.getVersionNumber());
        dto.setInheritedFrom(entity.getInheritedFrom());
        dto.setMasterPlanId(entity.getMasterPlanId());
        dto.setCurrent(entity.isCurrent());
        dto.setTrial(entity.isTrial());
        dto.setPrivatePlan(entity.isPrivatePlan());
        dto.setTokensIncluded(entity.getTokensIncluded());
        return dto;
    }
}