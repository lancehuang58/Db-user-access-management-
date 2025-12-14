package com.userpermission.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application for Database User Access Management
 * This application manages user permissions with time-based access control
 */
@SpringBootApplication
@EnableScheduling
public class UserAccessManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserAccessManagementApplication.class, args);
    }
}
