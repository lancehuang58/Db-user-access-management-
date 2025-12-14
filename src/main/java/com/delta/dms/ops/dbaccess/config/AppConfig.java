package com.delta.dms.ops.dbaccess.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Application configuration properties
 */
@Configuration
@ConfigurationProperties(prefix = "app.permission")
@EnableRetry
@Getter
@Setter
public class AppConfig {

    /**
     * Interval in milliseconds to check for expired permissions
     */
    private Long checkInterval = 300000L; // 5 minutes default

    /**
     * Whether to automatically cleanup expired permissions
     */
    private Boolean cleanupExpired = true;

    /**
     * Grace period in milliseconds before revoking expired permissions
     */
    private Long gracePeriod = 60000L; // 1 minute default
}
