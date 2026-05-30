package com.focuscomex.mapper;

import com.focuscomex.dto.ParametroDTO;
import com.focuscomex.model.Parametro;

public class ParametroMapper implements Mapper<ParametroDTO, Parametro> {

    public static ParametroMapper get() {
        return new ParametroMapper();
    }

    @Override
    public ParametroDTO mapToDTO(Parametro entity) {
    	ParametroDTO dto = new ParametroDTO();

        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setValue(entity.getValue());
        dto.setUpdatedAt(entity.getUpdatedAt());
       
        return dto;
    }
}