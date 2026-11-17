package uk.gov.justice.laa.datauserapi.contracts.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

public class ExternalUserTypeValidator implements ConstraintValidator<ExternalUserType, UserType> {
    @Override
    public boolean isValid(UserType value, ConstraintValidatorContext context) {
        return value == UserType.EXTERNAL;
    }
}
