package cl.duoc.backendskatesapp.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.backendskatesapp.model.ConsumerControl;
import cl.duoc.backendskatesapp.repository.ConsumerControlRepository;

@Service
public class ConsumerControlService {

    private final ConsumerControlRepository repository;

    public ConsumerControlService(ConsumerControlRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isPaused(String consumerName) {
        return repository.findById(consumerName)
                .map(ConsumerControl::isPaused)
                .orElse(false);
    }

    @Transactional
    public ConsumerControl setPaused(String consumerName, boolean paused) {
        ConsumerControl control = repository.findById(consumerName)
                .orElseGet(() -> new ConsumerControl(consumerName, paused));
        control.setPaused(paused);
        return repository.save(control);
    }
}
