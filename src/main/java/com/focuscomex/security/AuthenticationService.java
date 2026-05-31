package com.focuscomex.security;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.focuscomex.dto.JwtResponse;
import com.focuscomex.dto.PlanDTO;
import com.focuscomex.dto.UsuarioDTO;
import com.focuscomex.enums.UserType;
import com.focuscomex.exceptions.ComexException;
import com.focuscomex.model.User;
import com.focuscomex.repository.UserRepository;
import com.focuscomex.services.PlanService;
import com.focuscomex.util.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService implements UserDetailsService {

	private final UserRepository userRepository;
	private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final PlanService planService;

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
	
	public CurrentUser register(UsuarioDTO user) {
		if(userRepository.existsByUsername(user.getUsername())) {
			throw new ComexException("Usuario ya existe: " + user.getUsername());
		}
		
		List<PlanDTO> planes = planService.getPlanes(false);
		PlanDTO trialPlan = planes.stream().filter(p -> p.isTrial() && p.isActive()).findFirst().orElse(null);
		
		User newUser = null;
		if(trialPlan == null) {
			newUser = new User();
			newUser.setCreatedAt(LocalDateTime.now());
			newUser.setName(user.getName());
			newUser.setUsername(user.getUsername());
			newUser.setPassword(user.getPassword());
			newUser.setType(UserType.REGULAR);
			newUser.setCompany(user.getCompany());
			newUser.setEnabled(true);
			newUser.setLastLogin(LocalDateTime.now());
			
			userRepository.save(newUser);
		}else {
			Map<String, Object> data = planService.createUserWithPlan(trialPlan.getId(), user);
			if(data == null || !data.containsKey("user")) {
				throw new ComexException("Error al crear usuario con plan de prueba");
			}
			newUser = (User) data.get("user");
		}
		CurrentUser currentUser = new CurrentUser(newUser.getId(), newUser.getUsername(), newUser.getPassword(), true, false, 120*60, List.of(new SimpleGrantedAuthority("ROLE_" + newUser.getType().name())));
		
		return currentUser;
	}
}
