package com.delta.dms.ops.dbaccess.config;

import com.delta.dms.ops.dbaccess.model.Role;
import com.delta.dms.ops.dbaccess.model.User;
import com.delta.dms.ops.dbaccess.repository.RoleRepository;
import com.delta.dms.ops.dbaccess.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Initialize default data on application startup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.full-name}")
    private String adminFullName;

    @Override
    public void run(String... args) {
        initializeRoles();
        initializeDefaultAdmin();
    }

    private void initializeRoles() {
        for (Role.RoleName roleName : Role.RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                Role role = new Role();
                role.setName(roleName);
                role.setDescription(getDescriptionForRole(roleName));
                roleRepository.save(role);
                log.info("Created role: {}", roleName);
            }
        }
    }

    private void initializeDefaultAdmin() {
        if (!userRepository.existsByUsername(adminUsername)) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setEmail(adminEmail);
            admin.setFullName(adminFullName);
            admin.setEnabled(true);

            Role adminRole = roleRepository.findByName(Role.RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("Admin role not found"));
            admin.getRoles().add(adminRole);

            userRepository.save(admin);
            log.info("Created default admin user (username: {}, email: {})", adminUsername, adminEmail);
            log.warn("IMPORTANT: Please change the default admin password in production!");
        }
    }

    private String getDescriptionForRole(Role.RoleName roleName) {
        return switch (roleName) {
            case ROLE_ADMIN -> "Full system access including user and permission management";
            case ROLE_USER -> "Standard user with permission to request database access";
            case ROLE_VIEWER -> "Read-only access to view permissions and users";
        };
    }
}
