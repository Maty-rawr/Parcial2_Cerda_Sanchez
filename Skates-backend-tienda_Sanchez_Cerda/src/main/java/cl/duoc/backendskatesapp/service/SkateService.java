package cl.duoc.backendskatesapp.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.backendskatesapp.model.Skate;
import cl.duoc.backendskatesapp.repository.SkateRepository;

import java.util.List;

@Service
public class SkateService {

    private final SkateRepository skateRepository;

    public SkateService(SkateRepository skateRepository) {
        this.skateRepository = skateRepository;
    }

    @PostConstruct
    @Transactional
    public void cargarDatosDeEjemplo() {
        if (skateRepository.count() > 0) {
            return;
        }

        crear(new Skate(null, "Street", "DC", 7.5, null, 8));
        crear(new Skate(null, "Street", "DC", 8.0, null, 8));
        crear(new Skate(null, "Street", "DC", 8.5, null, 8));
        crear(new Skate(null, "Street", "Maui & Sons", 7.5, null, 8));
        crear(new Skate(null, "Street", "Maui & Sons", 8.0, null, 8));
        crear(new Skate(null, "Street", "Maui & Sons", 8.5, null, 8));
        crear(new Skate(null, "Street", "Polemic", 7.5, null, 8));
        crear(new Skate(null, "Street", "Polemic", 8.0, null, 8));
        crear(new Skate(null, "Street", "Polemic", 8.5, null, 8));
        crear(new Skate(null, "Longboard", "Loaded", null, 20.22, 8));
        crear(new Skate(null, "Longboard", "Loaded", null, 22.24, 8));
        crear(new Skate(null, "Longboard", "Loaded", null, 24.26, 8));
        crear(new Skate(null, "Longboard", "Loaded", null, 26.0, 8));
        crear(new Skate(null, "Longboard", "Arbor", null, 20.22, 8));
        crear(new Skate(null, "Longboard", "Arbor", null, 22.24, 8));
        crear(new Skate(null, "Longboard", "Arbor", null, 24.26, 8));
        crear(new Skate(null, "Longboard", "Arbor", null, 26.0, 8));
        crear(new Skate(null, "Longboard", "Landyachtz", null, 20.22, 8));
        crear(new Skate(null, "Longboard", "Landyachtz", null, 22.24, 8));
        crear(new Skate(null, "Longboard", "Landyachtz", null, 24.26, 8));
        crear(new Skate(null, "Downhill", "Landyachtz", null, 20.0, 8));
        crear(new Skate(null, "Downhill", "Landyachtz", null, 22.0, 8));
        crear(new Skate(null, "Downhill", "Landyachtz", null, 24.0, 8));
        crear(new Skate(null, "Downhill", "Landyachtz", null, 26.0, 8));
        crear(new Skate(null, "Downhill", "Rayne", null, 20.0, 8));
        crear(new Skate(null, "Downhill", "Rayne", null, 22.0, 8));
        crear(new Skate(null, "Downhill", "Rayne", null, 24.0, 8));
        crear(new Skate(null, "Downhill", "Rayne", null, 26.0, 8));
        crear(new Skate(null, "Downhill", "Madrid", null, 20.0, 8));
        crear(new Skate(null, "Downhill", "Madrid", null, 22.0, 8));
        crear(new Skate(null, "Downhill", "Madrid", null, 24.0, 8));
        crear(new Skate(null, "Downhill", "Madrid", null, 26.0, 8));
    }

    public List<Skate> listarTodos() {
        return skateRepository.findAllByOrderByIdAsc();
    }

    public List<Skate> listarPorModelo(String modelo) {
        return skateRepository.findByModeloIgnoreCaseOrderByIdAsc(modelo);
    }

    public List<String> listarModelos() {
        return skateRepository.findModelos();
    }

    public Skate buscarPorId(Long id) {
        return skateRepository.findById(id)
                .orElseThrow(() -> new SkateNoEncontradoException(id));
    }

    @Transactional
    public Skate crear(Skate nuevo) {
        nuevo.setId(null);
        return skateRepository.save(nuevo);
    }

    @Transactional
    public Skate actualizar(Long id, Skate datos) {
        Skate existente = buscarPorId(id);
        existente.setModelo(datos.getModelo());
        existente.setMarca(datos.getMarca());
        existente.setMedida(datos.getMedida());
        existente.setWheelbase(datos.getWheelbase());
        existente.setStock(datos.getStock());
        return skateRepository.save(existente);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!skateRepository.existsById(id)) {
            throw new SkateNoEncontradoException(id);
        }
        skateRepository.deleteById(id);
    }
}
