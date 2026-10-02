package uk.gov.justice.laa.datauserapi.application.query.shared.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.entity.Office;

@org.springframework.stereotype.Repository
public interface OfficeQueryRepository extends Repository<Office, UUID> {

    @Query("""
        SELECT new uk.gov.justice.laa.datauserapi.application.query.dto.OfficeView(
            o.code, f.code, o.address.postcode, o.address.addressLine1, o.address.addressLine2, o.address.addressLine3, o.address.city
        )
        FROM Office o
        JOIN o.firm f
        WHERE f.code = :firmCode
        ORDER BY o.code
        """)
    List<OfficeView> findByFirmCode(@Param("firmCode") String firmCode);
}
