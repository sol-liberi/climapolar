package com.friopolar.controller;

import com.friopolar.dto.CategoriaDto;
import com.friopolar.dto.TrabajoDetalleDto;
import com.friopolar.dto.TrabajoPaginaDto;
import com.friopolar.model.CategoriaTrabajo;
import com.friopolar.service.TrabajoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/trabajos")
public class TrabajoApiController {
    private final TrabajoService service;

    public TrabajoApiController(TrabajoService service) {
        this.service = service;
    }

    @GetMapping
    public TrabajoPaginaDto listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "false") boolean destacado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return service.listarPagina(categoria, destacado, page, size);
    }

    @GetMapping("/{id}")
    public TrabajoDetalleDto detalle(@PathVariable Long id) {
        return service.buscarDetalleDto(id);
    }

    @GetMapping("/categorias")
    public List<CategoriaDto> categorias() {
        return Arrays.stream(CategoriaTrabajo.values()).map(CategoriaDto::desde).toList();
    }
}
