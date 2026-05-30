package com.focuscomex.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.focuscomex.dto.ParametroDTO;
import com.focuscomex.services.ParametroService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/adm/parametros")
@RequiredArgsConstructor
public class ParametroController {

	private final ParametroService parametroService;
	
	@GetMapping
	@ResponseBody
	public List<ParametroDTO> getAll() {
		try {
			return parametroService.getAll();
		}catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}
	
	@PutMapping
	public List<ParametroDTO> save(@RequestBody List<ParametroDTO> parametros) {
		try {
			return parametroService.save(parametros);
		}catch(Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

}
