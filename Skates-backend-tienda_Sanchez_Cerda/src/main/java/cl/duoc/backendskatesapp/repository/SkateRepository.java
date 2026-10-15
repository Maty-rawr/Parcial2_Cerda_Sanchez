package cl.duoc.backendskatesapp.repository;

import cl.duoc.backendskatesapp.model.Skate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;

public interface SkateRepository extends JpaRepository<Skate, Long> {

    List<Skate> findAllByOrderByIdAsc();

    List<Skate> findByModeloIgnoreCaseOrderByIdAsc(String modelo);

    @Query("select distinct s.modelo from Skate s order by s.modelo")
    List<String> findModelos();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Skate s where s.id = :id")
    java.util.Optional<Skate> findByIdForUpdate(Long id);
}