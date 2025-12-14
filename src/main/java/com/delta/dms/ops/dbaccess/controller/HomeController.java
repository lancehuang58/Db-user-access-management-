package com.delta.dms.ops.dbaccess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home controller for main pages
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/users";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
