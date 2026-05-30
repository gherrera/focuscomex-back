package com.focuscomex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.focuscomex.model.Plan;

public interface PlanRepository extends JpaRepository<Plan, Long> {

	List<Plan> findAllByCurrentIsTrueOrderByNombreAsc();

}