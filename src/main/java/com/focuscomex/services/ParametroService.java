package com.focuscomex.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.focuscomex.dto.ParametroDTO;
import com.focuscomex.enums.ParamName;
import com.focuscomex.mapper.ParametroMapper;
import com.focuscomex.model.Parametro;
import com.focuscomex.repository.ParametroRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParametroService {

	private final ParametroRepository parametroRepository;
	
	public List<ParametroDTO> getAll() {
		List<Parametro> parametros = parametroRepository.findAll();
		return parametros.stream().map(ParametroMapper.get()::mapToDTO).toList();
	}
	
	public List<ParametroDTO> save(List<ParametroDTO> parametrosDTO) {
		List<Parametro> parametros = parametrosDTO.stream().map(dto -> {
			Parametro parametro = parametroRepository.findByName(dto.getName()).orElse(new Parametro());
			parametro.setName(dto.getName());
			parametro.setValue(dto.getValue());
			parametro.setUpdatedAt(LocalDateTime.now());
			return parametro;
		}).toList();
		
		List<Parametro> savedParametros = parametroRepository.saveAll(parametros);
		return savedParametros.stream().map(ParametroMapper.get()::mapToDTO).toList();
	}
	
	public String getValorByName(ParamName name) {
		return parametroRepository.findByName(name).map(Parametro::getValue).orElse(null);
	}
}
