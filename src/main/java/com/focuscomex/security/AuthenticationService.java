package com.focuscomex.security;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.focuscomex.dto.JwtResponse;
import com.focuscomex.model.User;
import com.focuscomex.repository.UserRepository;
import com.focuscomex.util.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService implements UserDetailsService {

	private final UserRepository userRepository;
	private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        CurrentUser currentUser;
        List<GrantedAuthority> grantList= new ArrayList<GrantedAuthority>();
        
        // Timeout en minutos
        int timeout = 120;
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User " + username + " was not found in the database"));
    	
    	GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getType().name());
        grantList.add(authority);

        boolean enabled = user.isEnabled();
        boolean locked = loginAttemptService.isBlocked(username);
        
        //String passwd = "{SHA-256}"+user.getPassword();
    	currentUser = new CurrentUser(user.getId(), username, user.getPassword(), enabled, locked, timeout*60, grantList);
        return currentUser;
    }
	
	public JwtResponse authenticate(CurrentUser currentUser) {
        String token = jwtUtil.generateToken(currentUser);

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no existe" + currentUser.getId()));
        
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        
        JwtResponse response = new JwtResponse(token);
        response.setTimeout(currentUser.getTimeout());

        return response;
    }
}
