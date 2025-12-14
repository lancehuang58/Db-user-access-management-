package com.userpermission.management.config;

import com.userpermission.management.model.Role;
import com.userpermission.management.model.User;
import com.userpermission.management.repository.RoleRepository;
import com.userpermission.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@example.com");
            admin.setFullName("System Administrator");
            admin.setEnabled(true);

            Role adminRole = roleRepository.findByName(Role.RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("Admin role not found"));
            admin.getRoles().add(adminRole);

            userRepository.save(admin);
            log.info("Created default admin user (username: admin, password: admin123)");
            log.warn("IMPORTANT: Please change the default admin password!");
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
