package com.focuscomex.model;

import java.time.LocalDateTime;

import com.focuscomex.enums.ParamName;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
		name = "parametros",
		uniqueConstraints = {
				@UniqueConstraint(
					name= "uk_parametros",
					columnNames = {"name"}
				)
			}
	)
public class Parametro {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
	@Column(nullable = false)
    private ParamName name;
	
    @Column(nullable = false, length = 1_024_000)
    private String value;
    
    @Column(name="updated_at")
    private LocalDateTime updatedAt;
}
