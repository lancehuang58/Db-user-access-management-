package com.delta.dms.ops.dbaccess.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration for the target MariaDB database connection.
 * This datasource is used for managing users and permissions on the target MariaDB.
 */
@Configuration
public class MariaDBConfig {

    @Bean(name = "mariadbDataSource")
    @ConfigurationProperties(prefix = "mariadb.datasource")
    public DataSource mariadbDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "mariadbJdbcTemplate")
    public JdbcTemplate mariadbJdbcTemplate(@Qualifier("mariadbDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
