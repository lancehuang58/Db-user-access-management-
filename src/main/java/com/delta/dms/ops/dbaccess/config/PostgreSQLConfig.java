package com.delta.dms.ops.dbaccess.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuration for the primary PostgreSQL database connection.
 * This datasource is used for JPA entities (User, Role, Permission, PermissionEvent).
 */
@Configuration
public class PostgreSQLConfig {

    /**
     * Primary datasource for the application (PostgreSQL).
     * Used by JPA/Hibernate for entity persistence.
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
}
