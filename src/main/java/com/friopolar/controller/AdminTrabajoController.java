package com.friopolar.controller;

import com.friopolar.dto.TrabajoForm;
import com.friopolar.model.CategoriaTrabajo;
import com.friopolar.service.MediaStorageException;
import com.friopolar.service.TrabajoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/admin/trabajos")
public class AdminTrabajoController {
    private static final String MENSAJE_CONFLICTO_AL_GUARDAR =
            "Este trabajo fue modificado por otra sesión. Recargá la página antes de guardar tus cambios.";

    private final TrabajoService service;

    public AdminTrabajoController(TrabajoService service) {
        this.service = service;
    }

    @ModelAttribute("categorias")
    public List<CategoriaTrabajo> categorias() {
        return Arrays.asList(CategoriaTrabajo.values());
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("trabajos", service.listarTodos());
        return "admin/trabajos";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        prepararFormulario(model, new TrabajoForm(), "Nuevo trabajo");
        return "admin/trabajo-form";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model) {
        var trabajo = service.buscarPorId(id);
        prepararFormulario(model, TrabajoForm.desde(trabajo), "Editar trabajo");
        model.addAttribute("mediosExistentes", trabajo.getMedios());
        return "admin/trabajo-form";
    }

    @PostMapping("/guardar")
    public String guardar(
            @Valid @ModelAttribute("trabajo") TrabajoForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect
    ) {
        if (!result.hasErrors()) {
            try {
                service.guardar(form);
            } catch (MediaStorageException ex) {
                result.reject("medios", ex.getMessage());
            } catch (OptimisticLockingFailureException ex) {
                // El incremento forzado puede detectar el conflicto al confirmar
                // la transacción, después de que el método del servicio retornó.
                result.reject("concurrencia", MENSAJE_CONFLICTO_AL_GUARDAR);
            }
        }

        if (result.hasErrors()) {
            prepararFormulario(model, form, form.getId() == null ? "Nuevo trabajo" : "Editar trabajo");
            if (form.getId() != null) {
                model.addAttribute("mediosExistentes", service.buscarPorId(form.getId()).getMedios());
            }
            return "admin/trabajo-form";
        }

        redirect.addFlashAttribute("mensaje", form.getId() == null
                ? "Trabajo publicado correctamente."
                : "Trabajo actualizado correctamente.");
        return "redirect:/admin/trabajos";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(
            @PathVariable Long id,
            @RequestParam Long version,
            RedirectAttributes redirect
    ) {
        try {
            service.eliminar(id, version);
            redirect.addFlashAttribute("mensaje", "Trabajo eliminado.");
        } catch (MediaStorageException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/trabajos";
    }

    private void prepararFormulario(Model model, TrabajoForm form, String tituloPagina) {
        if (!model.containsAttribute("trabajo")) model.addAttribute("trabajo", form);
        if (!model.containsAttribute("mediosExistentes")) model.addAttribute("mediosExistentes", List.of());
        model.addAttribute("tituloPagina", tituloPagina);
    }
}
