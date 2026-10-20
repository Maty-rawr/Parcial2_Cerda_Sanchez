package cl.duoc.backend_skates_app;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.backendskatesapp.model.ConsumerControl;
import cl.duoc.backendskatesapp.repository.ConsumerControlRepository;
import cl.duoc.backendskatesapp.service.ConsumerControlService;

@ExtendWith(MockitoExtension.class)
class ConsumerControlServiceTest {

    @Mock
    private ConsumerControlRepository repository;

    @Test
    void pauseStateCanBeReadByAnotherServiceUsingTheSharedRepository() {
        Map<String, ConsumerControl> storedControls = new HashMap<>();
        when(repository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(storedControls.get(invocation.getArgument(0))));
        when(repository.save(any(ConsumerControl.class))).thenAnswer(invocation -> {
            ConsumerControl control = invocation.getArgument(0);
            storedControls.put(control.getConsumerName(), control);
            return control;
        });
        ConsumerControlService firstInstance = new ConsumerControlService(repository);
        ConsumerControlService secondInstance = new ConsumerControlService(repository);

        firstInstance.setPaused("orders", true);

        assertTrue(secondInstance.isPaused("orders"));
    }
}
