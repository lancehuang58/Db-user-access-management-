package com.delta.dms.ops.dbaccess.controller;

import com.delta.dms.ops.dbaccess.dto.BulkPermissionRequest;
import com.delta.dms.ops.dbaccess.dto.MariaDBUserInfo;
import com.delta.dms.ops.dbaccess.model.Permission;
import com.delta.dms.ops.dbaccess.model.Permission.PermissionStatus;
import com.delta.dms.ops.dbaccess.model.Permission.PermissionType;
import com.delta.dms.ops.dbaccess.service.MariaDBEventService;
import com.delta.dms.ops.dbaccess.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller for managing temporary database permissions
 * Provides a streamlined interface for granting time-limited database access
 */
@Controller
@RequestMapping("/temp-permissions")
@RequiredArgsConstructor
@Slf4j
public class TemporaryDbPermissionController {

    private final PermissionService permissionService;
    private final MariaDBEventService mariaDBEventService;

    /**
     * Display the temporary permission management page
     */
    @GetMapping
    public String showTempPermissionPage(
            @RequestParam(required = false) String mariadbUsername,
            @RequestParam(required = false) String status,
            Model model) {

        log.info("Accessing temporary permission management page");

        // Get all MariaDB users for selection
        List<MariaDBUserInfo> mariadbUsers = mariaDBEventService.listMariaDBUsers();
        model.addAttribute("mariadbUsers", mariadbUsers);

        // Get permissions based on filters
        List<Permission> permissions;
        if (mariadbUsername != null && !mariadbUsername.isEmpty()) {
            permissions = permissionService.getPermissionsByMariaDBUsername(mariadbUsername);
        } else if (status != null && !status.isEmpty()) {
            permissions = permissionService.getPermissionsByStatus(PermissionStatus.valueOf(status));
        } else {
            permissions = permissionService.getAllPermissions();
        }

        model.addAttribute("permissions", permissions);
        model.addAttribute("selectedUsername", mariadbUsername);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("bulkRequest", new BulkPermissionRequest());

        return "temp-permissions/manage";
    }

    /**
     * Grant bulk permissions with preset duration
     */
    @PostMapping("/grant-bulk")
    public String grantBulkPermissions(
            @Valid @ModelAttribute("bulkRequest") BulkPermissionRequest request,
            BindingResult result,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "請檢查輸入資料");
            return "redirect:/temp-permissions";
        }

        try {
            String currentUsername = authentication.getName();
            String mariadbUsername = request.getMariadbUsername();
            String mariadbHost = request.getMariadbHost();

            if (mariadbUsername == null || mariadbUsername.isEmpty()) {
                throw new IllegalArgumentException("MariaDB username is required");
            }
            if (mariadbHost == null || mariadbHost.isEmpty()) {
                throw new IllegalArgumentException("MariaDB host is required");
            }

            LocalDateTime startTime = LocalDateTime.now();
            LocalDateTime endTime = calculateEndTime(startTime, request.getDurationDays());

            log.info("Granting bulk permissions to MariaDB user '{}@{}' for {} days",
                    mariadbUsername, mariadbHost, request.getDurationDays());

            int createdCount = 0;

            // Create permission for each selected database/permission type combination
            for (String resourceName : request.getResourceNames()) {
                for (String permissionType : request.getPermissionTypes()) {
                    Permission permission = new Permission();
                    permission.setMariadbUsername(mariadbUsername);
                    permission.setMariadbHost(mariadbHost);
                    permission.setResourceName(resourceName);
                    permission.setType(PermissionType.valueOf(permissionType));
                    permission.setStartTime(startTime);
                    permission.setEndTime(endTime);
                    permission.setStatus(PermissionStatus.PENDING);
                    permission.setDescription(String.format("臨時權限 - %d天有效期", request.getDurationDays()));

                    permissionService.createPermission(permission, currentUsername);

                    // Auto-approve and activate immediately
                    Permission created = permissionService.getPermissionsByMariaDBUsername(mariadbUsername)
                            .stream()
                            .filter(p -> p.getResourceName().equals(resourceName)
                                    && p.getType().toString().equals(permissionType)
                                    && p.getMariadbHost().equals(mariadbHost))
                            .findFirst()
                            .orElse(null);

                    if (created != null) {
                        permissionService.approvePermission(created.getId(), currentUsername);
                        createdCount++;
                    }
                }
            }

            redirectAttributes.addFlashAttribute("success",
                    String.format("成功授予 %d 個權限給用戶 %s@%s，有效期 %d 天",
                            createdCount, mariadbUsername, mariadbHost, request.getDurationDays()));

        } catch (Exception e) {
            log.error("Error granting bulk permissions", e);
            redirectAttributes.addFlashAttribute("error", "授予權限失敗: " + e.getMessage());
        }

        return "redirect:/temp-permissions";
    }

    /**
     * Extend permission expiration time
     */
    @PostMapping("/{id}/extend")
    public String extendPermission(
            @PathVariable Long id,
            @RequestParam int additionalDays,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            Permission permission = permissionService.getPermissionById(id);
            LocalDateTime newEndTime = permission.getEndTime().plusDays(additionalDays);

            permissionService.extendPermission(id, newEndTime, authentication.getName());

            redirectAttributes.addFlashAttribute("success",
                    String.format("權限已延長 %d 天", additionalDays));

        } catch (Exception e) {
            log.error("Error extending permission", e);
            redirectAttributes.addFlashAttribute("error", "延長權限失敗: " + e.getMessage());
        }

        return "redirect:/temp-permissions";
    }

    /**
     * Cancel/revoke permission immediately
     */
    @PostMapping("/{id}/cancel")
    public String cancelPermission(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            permissionService.revokePermission(id, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "權限已成功撤銷");

        } catch (Exception e) {
            log.error("Error canceling permission", e);
            redirectAttributes.addFlashAttribute("error", "撤銷權限失敗: " + e.getMessage());
        }

        return "redirect:/temp-permissions";
    }

    /**
     * Get active permissions for a specific MariaDB user (AJAX endpoint)
     */
    @GetMapping("/user/{mariadbUsername}/active")
    @ResponseBody
    public List<Permission> getActivePermissions(@PathVariable String mariadbUsername) {
        return permissionService.getActivePermissionsByMariaDBUsername(mariadbUsername);
    }

    /**
     * Calculate end time based on duration in days
     */
    private LocalDateTime calculateEndTime(LocalDateTime startTime, int durationDays) {
        return startTime.plusDays(durationDays);
    }
}
