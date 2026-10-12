package uk.gov.justice.laa.datauserapi.shared;

@FunctionalInterface
public interface Mapper<ARGUMENT, RESULT> {
    RESULT map(ARGUMENT argument);
}
