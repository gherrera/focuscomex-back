package com.focuscomex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.focuscomex.enums.ParamName;
import com.focuscomex.model.Parametro;

public interface ParametroRepository extends JpaRepository<Parametro, Long> {

	Optional<Parametro> findByName(ParamName name);
}