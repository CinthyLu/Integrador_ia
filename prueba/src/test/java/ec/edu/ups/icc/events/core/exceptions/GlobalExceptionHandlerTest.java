package ec.edu.ups.icc.events.core.exceptions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();

        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturn400WhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("La solicitud contiene campos inválidos"))
                .andExpect(jsonPath("$.path")
                        .value("/test/validation"))
                .andExpect(jsonPath("$.errors.name")
                        .value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400ForBadRequestException() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("La solicitud no es válida"))
                .andExpect(jsonPath("$.path")
                        .value("/test/bad-request"));
    }

    @Test
    void shouldReturn401ForUnauthorizedException() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message")
                        .value("Debe iniciar sesión"))
                .andExpect(jsonPath("$.path")
                        .value("/test/unauthorized"));
    }

    @Test
    void shouldReturn403ForForbiddenException() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message")
                        .value("No tiene permisos"))
                .andExpect(jsonPath("$.path")
                        .value("/test/forbidden"));
    }

    @Test
    void shouldReturn404WhenResourceDoesNotExist() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code")
                        .value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("El recurso no existe"))
                .andExpect(jsonPath("$.path")
                        .value("/test/not-found"));
    }

    @Test
    void shouldReturn409WhenBusinessRuleIsViolated()
            throws Exception {

        mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code")
                        .value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message")
                        .value("La operación viola una regla de negocio"))
                .andExpect(jsonPath("$.path")
                        .value("/test/business-rule"));
    }

    @Test
    void shouldReturn429WhenRateLimitIsExceeded()
            throws Exception {

        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code")
                        .value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message")
                        .value("Demasiadas solicitudes"))
                .andExpect(jsonPath("$.path")
                        .value("/test/rate-limit"));
    }

    @Test
    void shouldReturn500ForUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Ocurrió un error interno inesperado"))
                .andExpect(jsonPath("$.path")
                        .value("/test/unexpected"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @PostMapping("/validation")
        void validateRequest(
                @Valid @RequestBody TestRequest request
        ) {
            // Solo se utiliza para comprobar Bean Validation.
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException(
                    "La solicitud no es válida"
            );
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException(
                    "Debe iniciar sesión"
            );
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException(
                    "No tiene permisos"
            );
        }

        @GetMapping("/not-found")
        void resourceNotFound() {
            throw new ResourceNotFoundException(
                    "El recurso no existe"
            );
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException(
                    "La operación viola una regla de negocio"
            );
        }

        @GetMapping("/rate-limit")
        void rateLimit() {
            throw new RateLimitExceededException(
                    "Demasiadas solicitudes"
            );
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException(
                    "Error simulado"
            );
        }
    }

    record TestRequest(
            @NotBlank(message = "El nombre es obligatorio")
            String name
    ) {
    }
}