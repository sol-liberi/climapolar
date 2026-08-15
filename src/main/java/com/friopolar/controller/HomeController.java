package com.friopolar.controller;

import com.friopolar.service.TrabajoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final TrabajoService service;
    public HomeController(TrabajoService service) { this.service = service; }
    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("trabajos", service.listarDestacados());
        return "index";
    }
}
