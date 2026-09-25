package uk.gov.justice.laa.datauserapi.client.ts.response;

import lombok.Getter;

@Getter
public class TechServicesApiResponse<T> {

    private final boolean success;
    private final T data;
    private final TechServicesErrorResponse error;

    private TechServicesApiResponse(boolean success, T data, TechServicesErrorResponse error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> TechServicesApiResponse<T> success(T data) {
        return new TechServicesApiResponse<>(true, data, null);
    }

    public static <T> TechServicesApiResponse<T> error(TechServicesErrorResponse error) {
        return new TechServicesApiResponse<>(false, null, error);
    }

}
