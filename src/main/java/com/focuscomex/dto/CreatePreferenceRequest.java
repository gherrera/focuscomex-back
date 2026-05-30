package com.focuscomex.dto;

import java.util.Map;

import lombok.Data;

@Data
public class CreatePreferenceRequest {

	private String id;
	private String title;
	private Integer quantity;
	private Integer unitPrice;
	private String description;
	private String payerEmail;
	private String externalReference;
	private Map<String, Object> metadata;
}
