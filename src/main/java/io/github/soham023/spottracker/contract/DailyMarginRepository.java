package io.github.soham023.spottracker.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyMarginRepository extends JpaRepository<DailyMargin, Long> {

    Optional<DailyMargin> findBySymbolAndMarginDateAndFileTypeAndExpiryDate(
            String symbol, LocalDate marginDate, String fileType, LocalDate expiryDate);

    /** Newest first; within a day the most recently fetched file comes first. */
    List<DailyMargin> findBySymbolAndMarginDateBetweenOrderByMarginDateDescFetchedAtDescIdDesc(
            String symbol, LocalDate from, LocalDate to);

    @Query("select max(m.marginDate) from DailyMargin m where m.symbol = :symbol")
    Optional<LocalDate> findLatestMarginDate(String symbol);
}
