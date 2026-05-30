package com.focuscomex.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "planes")
public class Plan {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

	@Column(name="created_at")
    private LocalDateTime createdAt;
	
	@Column(name="valid_from", nullable = false, updatable = false)
    private LocalDateTime validFrom;

	@Column(name="valid_until")
    private LocalDateTime validUntil;

	@Column(name="nombre", nullable = false, updatable = false)
    private String nombre;
	
	@Column(name="nombre_grupo", nullable = false, updatable = false)
    private String nombreGrupo;
	
	@Column(name="precio", nullable = false, updatable = false)
    private Integer price;
    
    @Column(name="activo", nullable = false, updatable = false)
    private boolean active;
	
	@Column(name="version_number", nullable = false, updatable = false)
    private Integer versionNumber;
	
	@Column(name="inherited_from")
    private Long inheritedFrom;

	@Column(name="master_plan_id")
    private Long masterPlanId;
	
	@Column(name="is_current", nullable = false)
    private boolean current;
	
	@Column(name="is_trial", nullable = false, updatable = false)
    private boolean trial;
	
	@Column(name="is_private", nullable = false, updatable = false)
    private boolean privatePlan;

	@Column(name="tokens_included")
    private Integer tokensIncluded;

}
