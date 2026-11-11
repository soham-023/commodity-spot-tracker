package io.github.soham023.spottracker.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PremiumDiscountRepository extends JpaRepository<PremiumDiscount, Long> {

    Optional<PremiumDiscount> findBySymbolAndExpiryDateAndLocationAndAsOfDate(
            String symbol, LocalDate expiryDate, String location, LocalDate asOfDate);

    @Query("select max(p.asOfDate) from PremiumDiscount p where p.symbol = :symbol")
    Optional<LocalDate> findLatestAsOf(String symbol);

    List<PremiumDiscount> findBySymbolAndAsOfDateOrderByExpiryDateAscIdAsc(String symbol, LocalDate asOfDate);
}
