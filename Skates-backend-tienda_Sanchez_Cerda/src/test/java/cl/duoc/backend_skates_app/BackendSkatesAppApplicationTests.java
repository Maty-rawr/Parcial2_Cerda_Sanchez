package cl.duoc.backend_skates_app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import cl.duoc.backendskatesapp.BackendSkatesAppApplication;
import cl.duoc.backendskatesapp.repository.SkateRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BackendSkatesAppApplication.class)
class BackendSkatesAppApplicationTests {

	@Autowired
	private SkateRepository skateRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void cargaDatosDePruebaParaElFrontend() {
		assertEquals(32, skateRepository.count());
		assertEquals(3, skateRepository.findModelos().size());
		assertTrue(skateRepository.findModelos().contains("Street"));
		assertTrue(skateRepository.findModelos().contains("Longboard"));
		assertTrue(skateRepository.findModelos().contains("Downhill"));
	}

}
