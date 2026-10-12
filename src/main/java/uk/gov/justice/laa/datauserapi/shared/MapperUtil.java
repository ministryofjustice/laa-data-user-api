package uk.gov.justice.laa.datauserapi.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import uk.gov.justice.laa.datauserapi.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MapperUtil {

    public static PageRequest pageDataToPageRequest(Integer pageNumber, Integer pageSize, String[] sortBy) {
        List<Sort.Order> sortOrders = new ArrayList<>();
        if(sortBy != null) {
            for (String sort : sortBy) {
                if (sort == null || sort.isBlank()) {
                    continue;
                }
                String[] sortParts = sort.split(",");
                String property = sortParts[0];
                Sort.Direction direction = Sort.Direction.ASC;
                if (sortParts.length > 1) {
                    direction = Sort.Direction.fromString(sortParts[1]);
                }
                sortOrders.add(new Sort.Order(direction, property));
            }
        }
        return PageRequest.of(
                pageNumber != null ? pageNumber : 0,
                pageSize != null && pageSize > 0 ? pageSize : 10,
                Sort.by(sortOrders)
        );
    }

    public static <E extends Enum<E>> E requestFieldToEnum(String value, Class<E> enumClass) {
        return requestFieldToEnum(value, enumClass, "Provided value '%s' is not a valid %s", value, enumClass.getSimpleName());
    }

    public static <E extends Enum<E>> E requestFieldToEnum(String value, Class<E> enumClass, String errorMessage, Object... args) {
        if(value == null) {
            return null;
        } else {
            try {
                return Enum.valueOf(enumClass, value);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(errorMessage, args);
            }
        }
    }

    public static UUID requestStringToUUID(String value) {
        if(value == null) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Provided value '%s' is not a valid UUID", value);
        }
        return uuid;
    }

}
