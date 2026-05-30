package com.focuscomex.model;

import java.time.LocalDateTime;

import com.focuscomex.enums.UserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuarios")
public class User {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Column(name="last_login")
    private LocalDateTime lastLogin;

    @Column(name="created_at")
    private LocalDateTime createdAt;

    @Column(name="update_at")
    private LocalDateTime updatedAt;
    
    @Column(nullable = false)
    private boolean enabled;
    
    @Enumerated(EnumType.STRING)
	@Column(name="type", nullable = false)
    private UserType type;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_suscripcion")
    private Suscripcion currentSubscription;
    
    @Column(name="tokens_free")
    private Integer tokensFree;

    @Column(name="tokens_plan")
    private Integer tokensPlan;
}
