package com.centraldungeon.common.security;

import com.centraldungeon.common.config.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies CentralDungeon's own access/refresh JWTs (arquitectura.md 2.6, decisiones.md #125).
 * The Discord access token is never persisted or reused here - it is discarded once
 * DiscordOAuth2UserService finishes the login callback.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";

    private final JwtProperties properties;
    private final MACSigner signer;
    private final MACVerifier verifier;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        try {
            this.signer = new MACSigner(secretBytes);
            this.verifier = new MACVerifier(secretBytes);
        } catch (JOSEException e) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes (256 bits) for HS256", e);
        }
    }

    public String issueAccessToken(String userId) {
        return issue(userId, TokenType.ACCESS, properties.accessTokenTtl());
    }

    public String issueRefreshToken(String userId) {
        return issue(userId, TokenType.REFRESH, properties.refreshTokenTtl());
    }

    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return properties.refreshTokenTtl();
    }

    private String issue(String userId, TokenType type, Duration ttl) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim(CLAIM_TYPE, type.name())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return jwt.serialize();
    }

    /** @throws InvalidJwtException if the token is malformed, unsigned, expired, or of the wrong type. */
    public String verifyAndGetSubject(String token, TokenType expectedType) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new InvalidJwtException("Malformed token", e);
        }

        try {
            if (!jwt.verify(verifier)) {
                throw new InvalidJwtException("Invalid signature");
            }
        } catch (JOSEException e) {
            throw new InvalidJwtException("Could not verify signature", e);
        }

        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new InvalidJwtException("Malformed claims", e);
        }

        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date())) {
            throw new InvalidJwtException("Token expired");
        }

        String type = claims.getClaim(CLAIM_TYPE) instanceof String value ? value : null;
        if (!expectedType.name().equals(type)) {
            throw new InvalidJwtException("Unexpected token type: " + type);
        }

        String subject = claims.getSubject();
        if (subject == null) {
            throw new InvalidJwtException("Token has no subject");
        }
        return subject;
    }
}
