package com.delta.dms.ops.dbaccess.event;

import com.delta.dms.ops.dbaccess.model.Permission;
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
