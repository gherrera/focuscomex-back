package com.focuscomex.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "webhook_events",
			indexes = {
		        @Index(name = "idx_webhook_event_event_key", columnList = "event_key"),
		        @Index(name = "idx_webhook_event_processed", columnList = "processed")
		    }
		)
public class WebhookEvent {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	
	@Column(name = "event_key", length = 255, nullable = false, unique = true)
    private String eventKey;

	@Column(name = "request_id", length = 255)
    private String requestId;
	
	@Column(name = "processed", nullable = false)
    private boolean processed = false;
	
	@Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
    
	@Column(name = "processed_at")
    private OffsetDateTime processedAt;
   
}
