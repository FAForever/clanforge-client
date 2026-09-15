package com.faforever.clan_management.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("showLogin", false);
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("showLogin", true);
        return "index";
    }
}
