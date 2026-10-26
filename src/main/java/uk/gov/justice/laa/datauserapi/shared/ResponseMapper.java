package uk.gov.justice.laa.datauserapi.shared;

@FunctionalInterface
@SuppressWarnings("checkstyle:InterfaceTypeParameterName")
public interface ResponseMapper<READ_MODEL, RESPONSE> {
    RESPONSE map(READ_MODEL argument);
}
