package io.github.soham023.spottracker.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductQuoteRepository extends JpaRepository<ProductQuote, Long> {

    Optional<ProductQuote> findBySymbolAndQuoteDate(String symbol, LocalDate quoteDate);

    Optional<ProductQuote> findFirstBySymbolOrderByQuoteDateDesc(String symbol);
}
