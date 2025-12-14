package com.userpermission.management.event;

import com.userpermission.management.model.Permission;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a permission is activated
 */
@Getter
public class PermissionActivatedEvent extends ApplicationEvent {

    private final Permission permission;

    public PermissionActivatedEvent(Object source, Permission permission) {
        super(source);
        this.permission = permission;
    }
}
