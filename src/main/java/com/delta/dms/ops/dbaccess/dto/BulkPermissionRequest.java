package com.delta.dms.ops.dbaccess.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * DTO for bulk permission assignment requests
 */
@Data
public class BulkPermissionRequest {

    @NotNull(message = "請選擇MariaDB用戶")
    @NotEmpty(message = "請選擇MariaDB用戶")
    private String mariadbUsername;

    @NotNull(message = "請選擇Host")
    @NotEmpty(message = "請選擇Host")
    private String mariadbHost;

    @NotEmpty(message = "請至少選擇一個資料庫或資源")
    private List<String> resourceNames;

    @NotEmpty(message = "請至少選擇一種權限類型")
    private List<String> permissionTypes;

    @NotNull(message = "請選擇有效期")
    @Min(value = 1, message = "有效期至少為1天")
    private Integer durationDays;

    private String description;
}
