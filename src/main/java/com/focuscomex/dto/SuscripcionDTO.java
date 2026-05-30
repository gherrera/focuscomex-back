package com.focuscomex.dto;

import java.time.OffsetDateTime;

import com.focuscomex.enums.SuscripcionSource;
import com.focuscomex.enums.SuscripcionStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuscripcionDTO {

	private Long id;
    private UsuarioDTO user;
    private PlanDTO plan;
	private OffsetDateTime startedAt = OffsetDateTime.now();
    private OffsetDateTime currentPeriodStart;
    private OffsetDateTime currentPeriodEnd;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt;
    private OffsetDateTime pausedAt;
    private OffsetDateTime resumedAt;
    private Long pausedDaysRemaining;
    private SuscripcionStatus status;
    private SuscripcionSource source;
    private boolean paid;
    private String externalProvider;
    private String externalSubscriptionId;
    private boolean cancelAtPeriodEnd;
    private OffsetDateTime canceledAt;
    private String grantedBy;
}
