package com.focuscomex.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.focuscomex.dto.CreatePreferenceRequest;
import com.focuscomex.dto.TransactionDTO;
import com.focuscomex.dto.TransactionSummaryDTO;
import com.focuscomex.dto.WalletDTO;
import com.focuscomex.enums.TransactionStatus;
import com.focuscomex.enums.TransactionType;
import com.focuscomex.mapper.TransactionMapper;
import com.focuscomex.model.User;
import com.focuscomex.model.Wallet;
import com.focuscomex.model.WalletTransaction;
import com.focuscomex.repository.WalletRepository;
import com.focuscomex.repository.WalletTransactionRepository;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final MercadoPagoService mercadoPagoService;
    
    public WalletDTO getWalletByUserId(User user) {
        Wallet wallet = getOrCreateWallet(user);
        Integer tokenPrice = getTokenPrice();
        
        return new WalletDTO(wallet.getTokens(), tokenPrice);
    }
    
    public Map<String, String> createTokenRechargePreference(User user, Integer amountInPesos, Integer tokensExpected) throws MPException, MPApiException {
        validateRechargeAmount(amountInPesos);
        
        Integer tokenPrice = getTokenPrice();
        Integer calculatedTokens = (new BigDecimal(amountInPesos)).divide(new BigDecimal(tokenPrice), 0, RoundingMode.DOWN).intValue();
        
        if (!calculatedTokens.equals(tokensExpected)) {
            throw new IllegalArgumentException("Los tokens calculados no coinciden con los esperados");
        }
        
        Wallet wallet = getOrCreateWallet(user);
        
        // Crear transacción pendiente
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(TransactionType.RECHARGE);
        transaction.setAmountInPesos(amountInPesos);
        transaction.setTokens(tokensExpected);
        transaction.setTokenPriceAtTime(tokenPrice);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setDescription("Recarga de " + tokensExpected + " tokens");
        
        transaction = transactionRepository.save(transaction);
        
        CreatePreferenceRequest request = new CreatePreferenceRequest();
        request.setId("wallet-recharge");
		request.setTitle("Recarga Billetera");
		request.setQuantity(tokensExpected);
		request.setUnitPrice(tokenPrice);
		request.setDescription("Recarga Billetera");
		request.setPayerEmail(user.getUsername());
		request.setExternalReference(transaction.getId().toString());
		request.setMetadata(Map.of(
			"product", "wallet",
			"walletId", wallet.getId().toString()
		));

        // Crear preferencia en MercadoPago
        return mercadoPagoService.createPreference(request, "/billetera");
    }
    
    public boolean consumeTokens(User user, Integer tokenAmount, String description) {
        Wallet wallet = getOrCreateWallet(user);
        
        if(wallet.getTokens() < tokenAmount) {
			return false;
		}
        int rowsAffected = walletRepository.consumeTokens(wallet.getId(), tokenAmount);
        
        if (rowsAffected > 0) {
            // Registrar consumo
            WalletTransaction transaction = new WalletTransaction();
            transaction.setWallet(wallet);
            transaction.setType(TransactionType.TOKEN_CONSUMPTION);
            transaction.setTokens(-tokenAmount);
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setDescription(description);
            transactionRepository.save(transaction);
            
            return true;
        }
        
        return false;
    }
    
    public Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUserId(user.getId())
            .orElseGet(() -> {
                Wallet newWallet = new Wallet(user);
                return walletRepository.save(newWallet);
            });
    }
    
    public Integer getTokenPrice() {
    	return 1000;
//        return configRepository.findValueByKey("TOKEN_PRICE")
//            .map(BigDecimal::new)
//            .orElse(new BigDecimal("100")); // Precio por defecto: $100
    }
    
    private void validateRechargeAmount(Integer amount) {
        if (amount.compareTo(100) < 0) {
            throw new IllegalArgumentException("Monto mínimo es $100");
        }
        if (amount.compareTo(500000) > 0) {
            throw new IllegalArgumentException("Monto máximo es $500.000");
        }
    }
    
    /**
     * Obtiene el historial de transacciones de un usuario
     * @param userId ID del usuario
     * @param type Filtro por tipo de transacción (opcional)
     * @param pageable Parámetros de paginación
     * @return Página de transacciones del usuario
     */
    public Page<TransactionDTO> getTransactionHistory(Long userId, TransactionType type, Pageable pageable) {
        Page<WalletTransaction> transactions;
        
        if (type != null) {
            // Filtrar por tipo específico
            transactions = transactionRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
        } else {
            // Todas las transacciones del usuario
            transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        
        // Convertir entidades a DTOs
        return transactions.map(TransactionMapper.get()::mapToDTO);
    }
    
    /**
     * Obtiene estadísticas del historial de transacciones
     */
    public TransactionSummaryDTO getTransactionSummary(Long userId) {
        List<Object[]> summary = transactionRepository.getTransactionSummaryByUserId(userId);
        
        BigDecimal totalSpent = BigDecimal.ZERO;
        Integer totalTokensPurchased = 0;
        Integer totalTokensConsumed = 0;
        Long totalTransactions = 0L;
        
        for (Object[] row : summary) {
            TransactionType type = (TransactionType) row[0];
            BigDecimal amountSum = (BigDecimal) row[1];
            Integer tokensSum = (Integer) row[2];
            Long count = (Long) row[3];
            
            totalTransactions += count;
            
            if (type == TransactionType.RECHARGE) {
                totalSpent = totalSpent.add(amountSum != null ? amountSum : BigDecimal.ZERO);
                totalTokensPurchased += (tokensSum != null ? tokensSum : 0);
            } else if (type == TransactionType.TOKEN_CONSUMPTION) {
                totalTokensConsumed += Math.abs(tokensSum != null ? tokensSum : 0);
            }
        }
        
        return TransactionSummaryDTO.builder()
            .totalSpent(totalSpent)
            .totalTokensPurchased(totalTokensPurchased)
            .totalTokensConsumed(totalTokensConsumed)
            .totalTransactions(totalTransactions)
            .build();
    }
    
    public void grantFreeTokens(User user, Integer tokenAmount, String reason) {
		Wallet wallet = getOrCreateWallet(user);
		
		walletRepository.addTokens(wallet.getId(), tokenAmount);
		
		// Registrar transacción de recarga gratuita
		WalletTransaction transaction = new WalletTransaction();
		transaction.setWallet(wallet);
		transaction.setType(TransactionType.RECHARGE);
		transaction.setTokens(tokenAmount);
		transaction.setStatus(TransactionStatus.COMPLETED);
		transaction.setDescription("Recarga gratuita: " + reason);
		transactionRepository.save(transaction);
	}
}
