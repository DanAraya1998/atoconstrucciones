package com.daraya.atoconstrucciones.identity;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    @GetMapping("/auth/login")
    public String login() { return "redirect:/oauth2/authorization/ato"; }
    @GetMapping("/auth/register")
    public String register() { return "redirect:/oauth2/authorization/ato?registration=true"; }
}
