package com.userpermission.management.repository;

import com.userpermission.management.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Role entity
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(Role.RoleName name);

    boolean existsByName(Role.RoleName name);
}
