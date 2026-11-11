package io.github.soham023.spottracker.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractExpiryRepository extends JpaRepository<ContractExpiry, Long> {

    Optional<ContractExpiry> findBySymbolAndExpiryDate(String symbol, LocalDate expiryDate);

    List<ContractExpiry> findBySymbolOrderByExpiryDateAsc(String symbol);
}
