package com.delta.dms.ops.dbaccess.event;

import com.delta.dms.ops.dbaccess.model.Permission;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a new permission is created
 */
@Getter
public class PermissionCreatedEvent extends ApplicationEvent {

    private final Permission permission;
    private final String createdBy;

    public PermissionCreatedEvent(Object source, Permission permission, String createdBy) {
        super(source);
        this.permission = permission;
        this.createdBy = createdBy;
    }
}
