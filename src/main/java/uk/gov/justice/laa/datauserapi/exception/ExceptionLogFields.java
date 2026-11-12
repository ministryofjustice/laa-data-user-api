package uk.gov.justice.laa.datauserapi.exception;

import net.logstash.logback.argument.StructuredArgument;

import java.util.Locale;

import static net.logstash.logback.argument.StructuredArguments.kv;

public final class ExceptionLogFields {

    private ExceptionLogFields() {
    }

    public static StructuredArgument exceptionGroup(Throwable exception) {
        return kv("exception_group", groupName(exception));
    }

    private static String groupName(Throwable exception) {
        if (exception instanceof ExceptionGroup grouped) {
            return grouped.groupName();
        }
        Class<?> type = exception.getClass();
        while (type.getSimpleName().isEmpty()) {
            type = type.getSuperclass();
        }
        String name = type.getSimpleName();
        if (name.endsWith("Exception") && name.length() > "Exception".length()) {
            name = name.substring(0, name.length() - "Exception".length());
        }
        return name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }

    public static StructuredArgument exceptionReason(Throwable exception) {
        String reason = exception instanceof LaaDataApiException<?> failure
                ? failure.reason().name().toLowerCase(Locale.ROOT)
                : groupName(exception);
        return kv("exception_reason", reason);
    }
}
