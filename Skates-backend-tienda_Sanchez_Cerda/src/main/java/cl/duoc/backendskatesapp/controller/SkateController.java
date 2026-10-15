package cl.duoc.backendskatesapp.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import cl.duoc.backendskatesapp.model.Skate;
import cl.duoc.backendskatesapp.service.SkateService;

import java.util.List;

@RestController
@RequestMapping("/api/skate")
public class SkateController {

    private final SkateService skateService;

    public SkateController(SkateService skateService) {
        this.skateService = skateService;
    }

    @GetMapping
    public List<Skate> listar() {
        return skateService.listarTodos();
    }

    @GetMapping("/modelos")
    public List<String> listarModelos() {
        return skateService.listarModelos();
    }

    @GetMapping("/modelo/{modelo}")
    public List<Skate> listarPorModelo(@PathVariable String modelo) {
        return skateService.listarPorModelo(modelo);
    }

    @GetMapping("/{id}")
    public Skate obtener(@PathVariable Long id) {
        return skateService.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Skate crear(@Valid @RequestBody Skate nuevo) {
        return skateService.crear(nuevo);
    }

    @PutMapping("/{id}")
    public Skate actualizar(@PathVariable Long id, @Valid @RequestBody Skate datos) {
        return skateService.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        skateService.eliminar(id);
    }
}