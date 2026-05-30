package com.focuscomex.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwtResponse {

    private String token;
    private int timeout;

    public JwtResponse(String token) {
        this.token = token;
    }
}
