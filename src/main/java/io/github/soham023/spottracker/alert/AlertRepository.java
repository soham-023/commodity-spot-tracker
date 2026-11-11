package io.github.soham023.spottracker.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    Page<Alert> findByAlertDateGreaterThanEqualOrderByAlertDateDescIdDesc(LocalDate from, Pageable pageable);
}
