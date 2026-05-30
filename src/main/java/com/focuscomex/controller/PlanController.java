package com.focuscomex.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.focuscomex.dto.PlanDTO;
import com.focuscomex.services.PlanService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api")
@RequiredArgsConstructor
public class PlanController {

	private final PlanService planService; 

	@PostMapping("adm/plan")
	public PlanDTO savePlan(@RequestBody PlanDTO plan) {
		try {
			return planService.savePlan(plan);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@GetMapping("adm/planes")
	public List<PlanDTO> getPlanes() {
		try {
			return planService.getPlanes(true);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@DeleteMapping("adm/plan/{planId}")
	public void deletePlan(@PathVariable Long planId) {
		try {
			planService.deletePlan(planId);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	

}
