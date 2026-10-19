package cl.duoc.backend_skates_app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import cl.duoc.backendskatesapp.BackendSkatesAppApplication;
import cl.duoc.backendskatesapp.repository.SkateRepository;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BackendSkatesAppApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class BackendSkatesAppApplicationTests {

	@Container
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	@Autowired
	private SkateRepository skateRepository;

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
	}

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
