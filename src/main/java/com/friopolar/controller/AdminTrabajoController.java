package com.friopolar.controller;

import com.friopolar.model.Trabajo;
import com.friopolar.service.TrabajoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/trabajos")
public class AdminTrabajoController {
    private final TrabajoService service;
    public AdminTrabajoController(TrabajoService service) { this.service = service; }

    @GetMapping
    public String listar(Model model) { model.addAttribute("trabajos", service.listarTodos()); return "admin/trabajos"; }
    @GetMapping("/nuevo")
    public String nuevo(Model model) { model.addAttribute("trabajo", new Trabajo()); model.addAttribute("tituloPagina", "Nuevo trabajo"); return "admin/trabajo-form"; }
    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model) { model.addAttribute("trabajo", service.buscarPorId(id)); model.addAttribute("tituloPagina", "Editar trabajo"); return "admin/trabajo-form"; }
    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute Trabajo trabajo, BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) { model.addAttribute("tituloPagina", trabajo.getId() == null ? "Nuevo trabajo" : "Editar trabajo"); return "admin/trabajo-form"; }
        service.guardar(trabajo); redirect.addFlashAttribute("mensaje", "Trabajo guardado correctamente."); return "redirect:/admin/trabajos";
    }
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirect) { service.eliminar(id); redirect.addFlashAttribute("mensaje", "Trabajo eliminado."); return "redirect:/admin/trabajos"; }
}
