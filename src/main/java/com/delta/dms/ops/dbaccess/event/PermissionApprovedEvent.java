package com.delta.dms.ops.dbaccess.event;

import com.delta.dms.ops.model.Permission;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a permission is approved
 */
@Getter
public class PermissionApprovedEvent extends ApplicationEvent {

    private final Permission permission;
    private final String approvedBy;

    public PermissionApprovedEvent(Object source, Permission permission, String approvedBy) {
        super(source);
        this.permission = permission;
        this.approvedBy = approvedBy;
    }
}
