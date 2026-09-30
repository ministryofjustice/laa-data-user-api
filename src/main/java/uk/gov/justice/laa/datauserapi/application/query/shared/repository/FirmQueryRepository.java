package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchView;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmView;
import uk.gov.justice.laa.datauserapi.entity.Firm;

@org.springframework.stereotype.Repository
public interface FirmQueryRepository extends Repository<Firm, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.FirmView(
            f.code, f.name, f.type, pf.code, f.enabled
        )
        FROM Firm f
        LEFT JOIN f.parentFirm pf
        WHERE f.code = :code
        """)
    Optional<FirmView> findViewByCode(@Param("code") String code);

    @Query("SELECT COUNT(f) > 0 FROM Firm f WHERE f.code = :code")
    boolean existsByCode(@Param("code") String code);

    @Query(value = """
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.FirmView(
            f.code, f.name, f.type, pf.code, f.enabled
        )
        FROM Firm f
        LEFT JOIN f.parentFirm pf
        WHERE :term IS NULL
            OR LOWER(f.name) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(f.code) LIKE LOWER(CONCAT('%', :term, '%'))
        """,
        countQuery = """
        SELECT COUNT(f)
        FROM Firm f
        WHERE :term IS NULL
            OR LOWER(f.name) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(f.code) LIKE LOWER(CONCAT('%', :term, '%'))
        """)
    Page<FirmView> search(@Param("term") String term, Pageable pageable);

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchView(
            f.code, f.name
        )
        FROM Firm f
        WHERE (:term IS NULL
            OR LOWER(f.name) LIKE LOWER(CONCAT('%', :term, '%'))
            OR LOWER(f.code) LIKE LOWER(CONCAT('%', :term, '%')))
            AND (:allFirms = true OR f.code = :firmCode)
        ORDER BY f.name
        """)
    List<FirmSearchView> searchTypeAhead(
            @Param("term") String term,
            @Param("allFirms") boolean allFirms,
            @Param("firmCode") String firmCode,
            Pageable pageable);
}
