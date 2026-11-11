package uk.gov.justice.laa.datauserapi.application.query.mapper;

import java.util.Arrays;
import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.model.AppRoleUserType;

/** Maps app role restriction arrays to the values exposed on {@code AppRoleView}. */
public final class AppRoleRestrictionMapper {

    private AppRoleRestrictionMapper() {
    }

    /** A role restricted to a single user type exposes that type; anything else is {@code BOTH}. */
    public static String toUserTypeRestriction(UserType[] restrictions) {
        if (restrictions == null || Arrays.stream(restrictions).distinct().count() != 1) {
            return AppRoleUserType.BOTH.name();
        }
        return AppRoleUserType.valueOf(restrictions[0].name()).name();
    }

    public static List<String> toFirmTypeRestrictions(FirmType[] restrictions) {
        if (restrictions == null) {
            return List.of();
        }
        return Arrays.stream(restrictions).map(Enum::name).toList();
    }
}
