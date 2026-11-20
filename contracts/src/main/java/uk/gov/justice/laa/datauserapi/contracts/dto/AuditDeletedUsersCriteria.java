package uk.gov.justice.laa.datauserapi.contracts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.BindParam;

public record AuditDeletedUsersCriteria(
        @BindParam("page_number")
        @Min(value = 0, message = "page_number must be >= 0")
        Integer pageNumber,

        @BindParam("page_size")
        @Min(value = 1, message = "page_size must be >= 1")
        @Max(value = 100, message = "page_size must be <= 100")
        Integer pageSize,

        @BindParam("email_query")
        @Size(max = 255, message = "email_query must be <= 255 characters")
        String emailQuery
) {}
