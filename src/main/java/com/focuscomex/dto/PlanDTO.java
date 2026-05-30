package com.focuscomex.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanDTO {

    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String nombre;
    private String nombreGrupo;
    private Integer price;
    private boolean active;
    private Integer versionNumber;
    private Long inheritedFrom;
    private Long masterPlanId;
    private boolean current;
    private boolean trial;
    private boolean privatePlan;
    private Integer tokensIncluded;

}