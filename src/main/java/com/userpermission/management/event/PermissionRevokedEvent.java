package com.userpermission.management.event;

import com.userpermission.management.model.Permission;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a permission is revoked
 */
@Getter
public class PermissionRevokedEvent extends ApplicationEvent {

    private final Permission permission;
    private final String revokedBy;

    public PermissionRevokedEvent(Object source, Permission permission, String revokedBy) {
        super(source);
        this.permission = permission;
        this.revokedBy = revokedBy;
    }
}
