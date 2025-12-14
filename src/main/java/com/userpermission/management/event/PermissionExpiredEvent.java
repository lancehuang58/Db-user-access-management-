package com.userpermission.management.event;

import com.userpermission.management.model.Permission;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a permission expires
 */
@Getter
public class PermissionExpiredEvent extends ApplicationEvent {

    private final Permission permission;

    public PermissionExpiredEvent(Object source, Permission permission) {
        super(source);
        this.permission = permission;
    }
}
