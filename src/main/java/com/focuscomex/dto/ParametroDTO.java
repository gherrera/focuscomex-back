package com.focuscomex.dto;

import java.time.LocalDateTime;

import com.focuscomex.enums.ParamName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParametroDTO {

    private Long id;
    private ParamName name;
	private String value;
    private LocalDateTime updatedAt;

}