package com.focuscomex.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurrentUser extends User {

	private static final long serialVersionUID = 1866333806427274774L;
	private Long id;
    private int timeout;

	public CurrentUser(Long id, String username, String password, boolean enabled, boolean locked, int timeout, Collection<? extends GrantedAuthority> authorities) {
		super(username, password, enabled, true, true, !locked, authorities);
		this.id = id;
		this.timeout = timeout;
	}
}
