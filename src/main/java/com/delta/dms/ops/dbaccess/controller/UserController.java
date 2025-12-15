package com.delta.dms.ops.dbaccess.controller;

import com.delta.dms.ops.dbaccess.dto.MariaDBUserInfo;
import com.delta.dms.ops.dbaccess.service.MariaDBEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller for MariaDB user management UI
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final MariaDBEventService mariaDBEventService;

    @Value("${mariadb.user-management.default-host:%}")
    private String defaultHost;

    @GetMapping
    public String listUsers(Model model) {
        List<MariaDBUserInfo> users = mariaDBEventService.listMariaDBUsers();
        model.addAttribute("users", users);
        return "users/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        MariaDBUserInfo user = new MariaDBUserInfo();
        user.setHost(defaultHost);
        model.addAttribute("user", user);
        return "users/form";
    }

    @GetMapping("/{username}/{host}/edit")
    public String showEditForm(@PathVariable String username,
                               @PathVariable String host,
                               Model model) {
        MariaDBUserInfo user = mariaDBEventService.getUserInfo(username, host);
        model.addAttribute("user", user);
        return "users/form";
    }

    @PostMapping
    public String createUser(@RequestParam String username,
                            @RequestParam String host,
                            @RequestParam String password,
                            RedirectAttributes redirectAttributes) {
        try {
            mariaDBEventService.createMariaDBUser(username, host, password);
            redirectAttributes.addFlashAttribute("success",
                String.format("MariaDB user '%s'@'%s' created successfully", username, host));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to create user: " + e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{username}/{host}")
    public String updateUser(@PathVariable String username,
                            @PathVariable String host,
                            @RequestParam(required = false) String newPassword,
                            RedirectAttributes redirectAttributes) {
        try {
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                mariaDBEventService.alterUserPassword(username, host, newPassword);
                redirectAttributes.addFlashAttribute("success",
                    String.format("Password for '%s'@'%s' updated successfully", username, host));
            } else {
                redirectAttributes.addFlashAttribute("info", "No changes made");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to update user: " + e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{username}/{host}/delete")
    public String deleteUser(@PathVariable String username,
                            @PathVariable String host,
                            RedirectAttributes redirectAttributes) {
        try {
            mariaDBEventService.dropMariaDBUser(username, host);
            redirectAttributes.addFlashAttribute("success",
                String.format("MariaDB user '%s'@'%s' deleted successfully", username, host));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to delete user: " + e.getMessage());
        }
        return "redirect:/users";
    }

    @GetMapping("/{username}/{host}/permissions")
    public String viewUserPermissions(@PathVariable String username,
                                     @PathVariable String host,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        try {
            List<String> grants = mariaDBEventService.listUserPermissions(username, host);
            model.addAttribute("username", username);
            model.addAttribute("host", host);
            model.addAttribute("grants", grants);
            return "users/permissions";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                "Failed to retrieve permissions: " + e.getMessage());
            return "redirect:/users";
        }
    }
}
