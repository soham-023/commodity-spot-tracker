package io.github.soham023.spottracker.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
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

    @Query("select min(p.priceDate) from PriceSnapshot p where p.commodity = :commodity")
    Optional<LocalDate> findEarliestDate(String commodity);

    @Query("select max(p.fetchedAt) from PriceSnapshot p where p.commodity = :commodity")
    Optional<Instant> findLastFetchedAt(String commodity);

    long countByCommodity(String commodity);

    @Query("select distinct p.contractMonth from PriceSnapshot p where p.commodity = :commodity "
            + "and p.priceType = io.github.soham023.spottracker.domain.PriceType.FUTURES order by p.contractMonth")
    List<String> findContractMonths(String commodity);

    List<PriceSnapshot> findByCommodityAndPriceDateBetween(String commodity, LocalDate from, LocalDate to,
                                                          org.springframework.data.domain.Sort sort);
}
