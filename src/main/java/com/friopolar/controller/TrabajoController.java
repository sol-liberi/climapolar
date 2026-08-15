package com.friopolar.controller;

import com.friopolar.service.TrabajoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/trabajos")
public class TrabajoController {
    private final TrabajoService service;
    public TrabajoController(TrabajoService service) { this.service = service; }
    @GetMapping
    public String listar(Model model) { model.addAttribute("trabajos", service.listarTodos()); return "trabajos"; }
    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) { model.addAttribute("trabajo", service.buscarPorId(id)); return "trabajo-detalle"; }
}
