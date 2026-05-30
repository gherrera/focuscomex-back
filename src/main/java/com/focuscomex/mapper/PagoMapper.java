package com.focuscomex.mapper;

import com.focuscomex.dto.PagoDTO;
import com.focuscomex.model.Pago;

public class PagoMapper implements Mapper<PagoDTO, Pago> {
    
	public static PagoMapper get() {
        return new PagoMapper();
    }

    @Override
    public PagoDTO mapToDTO(Pago entity) {
        if (entity == null) return null;
        PagoDTO dto = new PagoDTO();
        dto.setId(entity.getId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setMonto(entity.getMonto());
        dto.setStatus(entity.getStatus());
        dto.setMedioPago(entity.getMedioPago());
        return dto;
    }

}
