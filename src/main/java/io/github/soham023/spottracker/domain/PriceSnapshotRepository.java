package io.github.soham023.spottracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long>,
        JpaSpecificationExecutor<PriceSnapshot> {

    Optional<PriceSnapshot> findBySourceAndMarketAndVarietyAndPriceTypeAndContractMonthAndPriceDate(
            String source, String market, String variety, PriceType priceType, String contractMonth, LocalDate priceDate);

    List<PriceSnapshot> findByCommodityAndPriceDateBetweenOrderByPriceDateAsc(
            String commodity, LocalDate from, LocalDate to);

    @Query("select max(p.priceDate) from PriceSnapshot p where p.commodity = :commodity")
    Optional<LocalDate> findLatestDate(String commodity);
}
