package com.delta.dms.ops.dbaccess.controller;

import com.delta.dms.ops.model.Permission;
import com.delta.dms.ops.model.User;
import com.delta.dms.ops.repository.PermissionEventRepository;
import com.delta.dms.ops.service.PermissionService;
import com.delta.dms.ops.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller for permission management UI
 */
@Controller
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final UserService userService;
    private final PermissionEventRepository permissionEventRepository;

    @GetMapping
    public String listPermissions(Model model) {
        List<Permission> permissions = permissionService.getAllPermissions();
        model.addAttribute("permissions", permissions);
        return "permissions/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("permission", new Permission());
        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("permissionTypes", Permission.PermissionType.values());
        return "permissions/form";
    }

    @GetMapping("/{id}")
    public String viewPermission(@PathVariable Long id, Model model) {
        Permission permission = permissionService.getPermissionById(id);
        model.addAttribute("permission", permission);
        model.addAttribute("events", permissionEventRepository.findByPermissionId(id));
        return "permissions/view";
    }

    @PostMapping
    public String createPermission(@ModelAttribute Permission permission,
                                   @RequestParam Long userId,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            User user = userService.getUserById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            permission.setUser(user);
            String createdBy = authentication != null ? authentication.getName() : "SYSTEM";
            permissionService.createPermission(permission, createdBy);

            redirectAttributes.addFlashAttribute("success",
                "Permission request created successfully. Awaiting approval.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to create permission: " + e.getMessage());
        }
        return "redirect:/permissions";
    }

    @PostMapping("/{id}/approve")
    public String approvePermission(@PathVariable Long id,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            String approvedBy = authentication != null ? authentication.getName() : "SYSTEM";
            permissionService.approvePermission(id, approvedBy);
            redirectAttributes.addFlashAttribute("success",
                "Permission approved and MariaDB event scheduled for auto-revoke");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to approve permission: " + e.getMessage());
        }
        return "redirect:/permissions/" + id;
    }

    @PostMapping("/{id}/revoke")
    public String revokePermission(@PathVariable Long id,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            String revokedBy = authentication != null ? authentication.getName() : "SYSTEM";
            permissionService.revokePermission(id, revokedBy);
            redirectAttributes.addFlashAttribute("success",
                "Permission revoked and MariaDB access removed");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to revoke permission: " + e.getMessage());
        }
        return "redirect:/permissions/" + id;
    }

    @PostMapping("/{id}/extend")
    public String extendPermission(@PathVariable Long id,
                                  @RequestParam String newEndTime,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            LocalDateTime endTime = LocalDateTime.parse(newEndTime);
            String modifiedBy = authentication != null ? authentication.getName() : "SYSTEM";
            permissionService.extendPermission(id, endTime, modifiedBy);
            redirectAttributes.addFlashAttribute("success", "Permission extended successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to extend permission: " + e.getMessage());
        }
        return "redirect:/permissions/" + id;
    }
}
