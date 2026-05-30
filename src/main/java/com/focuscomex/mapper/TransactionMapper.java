package com.focuscomex.mapper;

import com.focuscomex.dto.TransactionDTO;
import com.focuscomex.model.WalletTransaction;

public class TransactionMapper implements Mapper<TransactionDTO, WalletTransaction> {
	
    public static TransactionMapper get() {
        return new TransactionMapper();
    }

    @Override
    public TransactionDTO mapToDTO(WalletTransaction entity) {
    	TransactionDTO dto = new TransactionDTO();

    	dto.setId(entity.getId());
        dto.setType(entity.getType());
        dto.setAmountInPesos(entity.getAmountInPesos());
        dto.setTokens(entity.getTokens());
        dto.setTokenPriceAtTime(entity.getTokenPriceAtTime());
        dto.setDescription(entity.getDescription());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setExternalPaymentId(entity.getExternalPaymentId());
       
        return dto;
    }
}