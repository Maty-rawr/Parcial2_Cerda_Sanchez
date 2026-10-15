package cl.duoc.backendskatesapp.service;

public class SkateNoEncontradoException extends RuntimeException {
    public SkateNoEncontradoException(Long id) {
        super("No existe un Skate con id " + id);
    }
}