package uk.gov.justice.laa.datauserapi.shared;

@FunctionalInterface
public interface QueryHandler <QUERY, READ_MODEL> {
    READ_MODEL handle(QUERY query);
}
