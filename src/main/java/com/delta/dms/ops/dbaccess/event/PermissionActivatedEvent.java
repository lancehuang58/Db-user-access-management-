package com.delta.dms.ops.dbaccess.event;

import com.delta.dms.ops.dbaccess.model.Permission;
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
