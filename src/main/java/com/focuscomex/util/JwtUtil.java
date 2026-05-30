package com.focuscomex.util;

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focuscomex.security.CurrentUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Service
public class JwtUtil {

    public static final String JWT_SECRET = "UkXp2s5v8y/B?E(G+KbPeShVmYq3t6w9z$C&F)J@McQfTjWnZr4u7x!A%D*G-KaP";
    private static TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
    };
    private static ObjectMapper mapper = new ObjectMapper();
    private String secret = Base64.getEncoder().encodeToString(JWT_SECRET.getBytes());
    private Base64.Decoder decoder = Base64.getUrlDecoder();

    public Object extractClaim(String token, String key) {
        final Map<String, Object> map = this.extractAllClaims(token);
        return map.get(key);
    }

    public String extractUsername(String token) {
        String[] chunks = token.split("\\.");
        //String header = new String(decoder.decode(chunks[0]));
        String payload = new String(decoder.decode(chunks[1]));
        return (String) this.extractClaim(payload, "sub");
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser().setSigningKey(secret). build().parseClaimsJws(token).getBody();
    }

    private Map<String, Object> extractAllClaims(String token) {
        try {
            return mapper.readValue(token, typeRef);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    //generate token for user
    public String generateToken(CurrentUser currentUser) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, currentUser.getUsername(), currentUser.getTimeout());
    }

    //while creating the token -
    //1. Define  claims of the token, like Issuer, Expiration, Subject, and the ID
    //2. Sign the JWT using the HS512 algorithm and secret key.
    //3. According to JWS Compact Serialization(https://tools.ietf.org/html/draft-ietf-jose-json-web-signature-41#section-3.1)
    //   compaction of the JWT to a URL-safe string
    @SuppressWarnings("deprecation")
    private String doGenerateToken(Map<String, Object> claims, String subject, int timeout) {
        return Jwts.builder()
        		.setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + timeout * 1000))
                .signWith(SignatureAlgorithm.HS512, secret).compact();
    }

    //validate token
    public Boolean validateToken(String token) {
    	try {
    		return (!isTokenExpired(token));
    	}catch (Exception e) {
    		return false;
    	}
    }
}
