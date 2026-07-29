package ec.edu.ups.icc.events.core.dtos;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiErrorResponse(
        boolean success,
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> errors
) {

    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String path
    ) {
        return new ApiErrorResponse(
                false,
                LocalDateTime.now(),
                status,
                code,
                message,
                path,
                null
        );
    }

    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            Map<String, String> errors
    ) {
        return new ApiErrorResponse(
                false,
                LocalDateTime.now(),
                status,
                code,
                message,
                path,
                errors
        );
    }
}