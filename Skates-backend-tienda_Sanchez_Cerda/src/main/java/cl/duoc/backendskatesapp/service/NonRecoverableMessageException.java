package cl.duoc.backendskatesapp.service;

public class NonRecoverableMessageException extends RuntimeException {

    public NonRecoverableMessageException(String message) {
        super(message);
    }
}
