package com.userpermission.management.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    /**
     * Secret key for JWT token signing
     */
    private String secret;

    /**
     * Token expiration time in milliseconds
     */
    private Long expiration;
}
