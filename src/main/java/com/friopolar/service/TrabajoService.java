package com.friopolar.service;

import com.friopolar.model.Trabajo;
import com.friopolar.repository.TrabajoRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TrabajoService {
    private final TrabajoRepository repository;
    public TrabajoService(TrabajoRepository repository) { this.repository = repository; }
    public List<Trabajo> listarTodos() { return repository.findAllByOrderByFechaDesc(); }
    public List<Trabajo> listarDestacados() { return repository.findByDestacadoTrueOrderByFechaDesc(); }
    public Trabajo buscarPorId(Long id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Trabajo no encontrado: " + id)); }
    public Trabajo guardar(Trabajo trabajo) { return repository.save(trabajo); }
    public void eliminar(Long id) { repository.delete(buscarPorId(id)); }
}
