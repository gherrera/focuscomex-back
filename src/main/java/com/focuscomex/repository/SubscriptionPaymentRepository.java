package com.focuscomex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.focuscomex.model.SubscriptionPayment;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, String> {

	boolean existsById(String id);

}