package uk.gov.hmcts.opal.controllers.advice;

import lombok.AllArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import uk.gov.hmcts.opal.annotation.JsonSchemaValidated;
import uk.gov.hmcts.opal.exception.JsonSchemaValidationException;
import uk.gov.hmcts.opal.service.opal.JsonSchemaValidationService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@ControllerAdvice
@AllArgsConstructor
public class JsonSchemaValidationAdvice extends RequestBodyAdviceAdapter {

    private final JsonSchemaValidationService jsonSchemaValidationService;
    private static final Set<String> DRAFT_ACCOUNT_REQUEST_SCHEMAS = Set.of(
        "addDraftAccountRequest",
        "replaceDraftAccountRequest",
        "updateDraftAccountRequest"
    );
    private static final Set<String> TOKEN_DERIVED_DRAFT_ACCOUNT_FIELDS = Set.of(
        "submitted_by",
        "submitted_by_name",
        "validated_by",
        "validated_by_name"
    );

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.hasParameterAnnotation(JsonSchemaValidated.class);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage,
                                           MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        byte[] bodyBytes = inputMessage.getBody().readAllBytes();
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        JsonSchemaValidated annotation = parameter.getParameterAnnotation(JsonSchemaValidated.class);
        if (annotation != null) {
            String schemaPath = annotation.schemaPath();
            rejectTimelineDataForDraftAccountRequests(body, schemaPath);
            rejectTokenDerivedFieldsForDraftAccountRequests(body, schemaPath);
            jsonSchemaValidationService.validateOrError(body, schemaPath);
        }

        return new HttpInputMessage() {
            @Override
            public HttpHeaders getHeaders() {
                return inputMessage.getHeaders();
            }

            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(bodyBytes);
            }
        };
    }

    private static void rejectTimelineDataForDraftAccountRequests(String body, String schemaPath) {
        if (schemaPath.contains("draft-account")
            && DRAFT_ACCOUNT_REQUEST_SCHEMAS.stream().anyMatch(schemaPath::contains)
            && body.contains("\"timeline_data\"")) {
            throw new JsonSchemaValidationException("timeline_data is not allowed in draft account requests");
        }
    }

    private static void rejectTokenDerivedFieldsForDraftAccountRequests(String body, String schemaPath) {
        if (schemaPath.contains("draft-account")
            && DRAFT_ACCOUNT_REQUEST_SCHEMAS.stream().anyMatch(schemaPath::contains)
            && TOKEN_DERIVED_DRAFT_ACCOUNT_FIELDS.stream().anyMatch(field -> body.contains("\"" + field + "\""))) {
            throw new JsonSchemaValidationException(
                "submitted_by, submitted_by_name, validated_by and validated_by_name are not allowed in draft account "
                    + "requests"
            );
        }
    }
}
