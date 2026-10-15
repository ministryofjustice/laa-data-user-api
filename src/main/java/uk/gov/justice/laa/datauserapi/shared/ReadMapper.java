package uk.gov.justice.laa.datauserapi.shared;

@FunctionalInterface
@SuppressWarnings("checkstyle:InterfaceTypeParameterName")
public interface ReadMapper<ENTITY, READ_MODEL> {
    READ_MODEL map(ENTITY argument);
}
