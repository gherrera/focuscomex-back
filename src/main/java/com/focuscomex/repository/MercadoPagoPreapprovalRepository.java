package com.focuscomex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.focuscomex.model.MercadoPagoPreapproval;

public interface MercadoPagoPreapprovalRepository extends JpaRepository<MercadoPagoPreapproval, String> {

	Optional<MercadoPagoPreapproval> findByExternalReference(String externalReference);
	
}