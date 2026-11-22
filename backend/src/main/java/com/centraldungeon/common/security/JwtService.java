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
 * Signs and verifies CentralDungeon's own access and refresh JWTs. Discord's access token never reaches
 * this class: it is used once during the login callback and then dropped.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";

    private final JwtProperties properties;
    private final MACSigner signer;
    private final MACVerifier verifier;

    /**
     * @param properties the signing key and the two lifetimes, bound from {@code app.jwt.*}
     */
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

    /**
     * Issues the token sent as {@code Authorization: Bearer} on every call.
     *
     * <p>It carries the subject and the token type, and <b>no roles</b>: the JWT asserts identity,
     * not authorization, so what the person may do is read from the database on each request (ADR-05 §3).
     *
     * @param userId who the token identifies
     * @return the signed token
     */
    public String issueAccessToken(String userId) {
        return issue(userId, TokenType.ACCESS, properties.accessTokenTtl());
    }

    /**
     * Signs a long-lived refresh token, the one that travels in an httpOnly cookie.
     *
     * <p>Typed as a refresh token so it can never be replayed as an access token, which is what the
     * type claim is for.
     *
     * <p><b>Signing is not issuing.</b> The refresh endpoint only accepts tokens the server recorded, so
     * a session is opened through {@code AuthService.openSession}, which calls this and records the
     * result; a token from here alone is refused at renewal.
     *
     * @param userId who the token identifies
     * @return the signed token
     */
    public String issueRefreshToken(String userId) {
        return issue(userId, TokenType.REFRESH, properties.refreshTokenTtl());
    }

    /**
     * How long an access token lasts.
     *
     * @return the configured lifetime. Short on purpose: it is the window a stolen bearer token is
     *         worth anything in (ADR-05 §2)
     */
    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
    }

    /**
     * How long a refresh token lasts.
     *
     * @return the configured lifetime, which is also the max age of the cookie carrying it
     */
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

    /**
     * Verifies a token and returns who it identifies.
     *
     * @param token        the serialized JWT
     * @param expectedType which of the two tokens it must be
     * @return the subject, the local user id
     * @throws InvalidJwtException if the token is malformed, unsigned, expired, or of the wrong type
     */
    public String verifyAndGetSubject(String token, TokenType expectedType) {
        return verifiedClaims(token, expectedType).getSubject();
    }

    /**
     * Verifies a refresh token and returns what the server needs to find it among the ones it issued.
     *
     * <p>A valid signature is necessary but not enough: the caller still has to check that the server
     * recorded this token and has not revoked it.
     *
     * @param token the serialized refresh JWT
     * @return its subject, id and expiry
     * @throws InvalidJwtException if the token is malformed, unsigned, expired, not a refresh token, or has no id
     */
    public RefreshTokenClaims verifyRefreshToken(String token) {
        JWTClaimsSet claims = verifiedClaims(token, TokenType.REFRESH);
        String jti = claims.getJWTID();
        if (jti == null) {
            throw new InvalidJwtException("Refresh token has no id");
        }
        return new RefreshTokenClaims(claims.getSubject(), jti, claims.getExpirationTime().toInstant());
    }

    /** Signature, expiry, type and subject; the claims once all four hold. */
    private JWTClaimsSet verifiedClaims(String token, TokenType expectedType) {
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

        if (claims.getSubject() == null) {
            throw new InvalidJwtException("Token has no subject");
        }
        return claims;
    }
}
