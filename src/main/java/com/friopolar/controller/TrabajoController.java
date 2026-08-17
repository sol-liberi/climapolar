package com.friopolar.controller;

import com.friopolar.model.CategoriaTrabajo;
import com.friopolar.service.TrabajoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/trabajos")
public class TrabajoController {
    private final TrabajoService service;
    public TrabajoController(TrabajoService service) { this.service = service; }
    @GetMapping
    public String listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        String categoriaActiva = categoria == null || categoria.isBlank()
                ? ""
                : CategoriaTrabajo.buscarPorCodigo(categoria)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría inválida"))
                        .getCodigo();
        model.addAttribute("paginaTrabajos", service.listarPagina(categoriaActiva, false, page, 9));
        model.addAttribute("categoriaActiva", categoriaActiva);
        return "trabajos";
    }
    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) { model.addAttribute("trabajo", service.buscarPorId(id)); return "trabajo-detalle"; }
}
