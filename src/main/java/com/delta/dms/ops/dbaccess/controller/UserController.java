package com.delta.dms.ops.dbaccess.controller;

import com.delta.dms.ops.dbaccess.model.User;
import com.delta.dms.ops.dbaccess.repository.RoleRepository;
import com.delta.dms.ops.dbaccess.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller for user management UI
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RoleRepository roleRepository;

    @GetMapping
    public String listUsers(Model model) {
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        return "users/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", roleRepository.findAll());
        return "users/form";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        User user = userService.getUserById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("roles", roleRepository.findAll());
        return "users/form";
    }

    @PostMapping
    public String createUser(@ModelAttribute User user,
                            @RequestParam(required = false) List<Long> roleIds,
                            RedirectAttributes redirectAttributes) {
        try {
            User createdUser = userService.createUser(user);
            if (roleIds != null && !roleIds.isEmpty()) {
                userService.updateUserRoles(createdUser.getId(), roleIds);
            }
            redirectAttributes.addFlashAttribute("success", "User created successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create user: " + e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}")
    public String updateUser(@PathVariable Long id,
                            @ModelAttribute User user,
                            @RequestParam(required = false) List<Long> roleIds,
                            @RequestParam(required = false) String newPassword,
                            RedirectAttributes redirectAttributes) {
        try {
            userService.updateUser(id, user, newPassword);
            if (roleIds != null && !roleIds.isEmpty()) {
                userService.updateUserRoles(id, roleIds);
            }
            redirectAttributes.addFlashAttribute("success", "User updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update user: " + e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("success", "User deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete user: " + e.getMessage());
        }
        return "redirect:/users";
    }
}
