package com.focuscomex.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.focuscomex.dto.PlanDTO;
import com.focuscomex.dto.UsuarioDTO;
import com.focuscomex.services.PlanService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/public")
@RequiredArgsConstructor
public class PublicController {

	private final PlanService planService; 
	
	@GetMapping("planes")
	public List<PlanDTO> getPlanes() {
		try {
			return planService.getPlanes(false);
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@PostMapping("user/{planId}")
	public Map<String, String> createUser(@PathVariable Long planId, @RequestBody UsuarioDTO usuario) {
		try {
			Map<String, Object> data = planService.createUserWithPlan(planId, usuario);
			if(data.containsKey("initPoint")) {
				Map.of("initPoint", data.get("initPoint").toString());
			}
			return null;
		}catch(Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
}