package com.focuscomex.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.focuscomex.dto.RechargeRequestDTO;
import com.focuscomex.dto.TransactionDTO;
import com.focuscomex.dto.TransactionSummaryDTO;
import com.focuscomex.dto.WalletDTO;
import com.focuscomex.enums.TransactionType;
import com.focuscomex.model.User;
import com.focuscomex.security.SecurityUtils;
import com.focuscomex.services.UserService;
import com.focuscomex.services.WalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/wallet")
@PreAuthorize("hasRole('REGULAR')")
public class WalletController {

	private final WalletService walletService;
	private final UserService userService;

	@GetMapping("balance")
    public ResponseEntity<WalletDTO> getWalletBalance() {
        try {
            User user = userService.getCurrentUser();
            WalletDTO wallet = walletService.getWalletByUserId(user);
            return ResponseEntity.ok(wallet);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
	
	@PostMapping("recharge-tokens")
    public ResponseEntity<?> createTokenRechargePreference(@Valid @RequestBody RechargeRequestDTO request) {
        
        try {
            User user = userService.getCurrentUser();
            Map<String, String> response = walletService.createTokenRechargePreference(
                user, 
                request.getAmountInPesos(),
                request.getTokensExpected()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno: " + e.getMessage());
        }
    }

	@GetMapping("transactions")
    public ResponseEntity<Page<TransactionDTO>> getTransactions(
            @RequestParam(required = false) TransactionType type,
            Pageable pageable) {
        
        try {
            User user = userService.getCurrentUser();
            Page<TransactionDTO> transactions = walletService.getTransactionHistory(user.getId(), type, pageable);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
	
	@GetMapping("transactions/summary")
	public ResponseEntity<TransactionSummaryDTO> getTransactionSummary() {
	    try {
	        Long userId = SecurityUtils.getCurrentUser().getId();
	        TransactionSummaryDTO summary = walletService.getTransactionSummary(userId);
	        return ResponseEntity.ok(summary);
	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	    }
	}
	
}
