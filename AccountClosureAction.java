import com.consol.citrus.context.TestContext;
import com.consol.citrus.dsl.actions.DelegatingTestAction;
import com.consol.citrus.http.client.HttpClient;
import com.consol.citrus.message.MessageType;
import com.consol.citrus.validation.json.JsonMappingValidationCallback;
import io.qameta.allure.Attachment;
import io.qameta.allure.Step;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class AccountClosureAction extends AbstractTestBehavior {

    private final TestContext executionContext;
    private FailureCode failureCode;
    private String operationMethod = "";
    private String failureMessage = "";
    private String failureReason = "";
    private String closureDescription = "";
    private String closureCode = "";
    private String userPhoneNumber = "";
    private OperationResult operationResult = SUCCESS;
    private OperationFailure operationFailure;
    private boolean isInitiatedByIssuer = false;

    public AccountClosureAction(TestContext executionContext) {
        this.executionContext = executionContext;
    }

    public AccountClosureAction failureCode(FailureCode failureCode) {
        this.failureCode = failureCode;
        return this;
    }

    public AccountClosureAction failureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
        return this;
    }

    public AccountClosureAction failureReason(String failureReason) {
        this.failureReason = failureReason;
        return this;
    }

    public AccountClosureAction closureCode(String closureCode) {
        this.closureCode = closureCode;
        return this;
    }

    public AccountClosureAction closureDescription(String closureDescription) {
        this.closureDescription = closureDescription;
        return this;
    }

    public AccountClosureAction operationResult(OperationResult operationResult) {
        this.operationResult = operationResult;
        return this;
    }

    public AccountClosureAction initiatedByIssuer() {
        this.isInitiatedByIssuer = true;
        return this;
    }

    public AccountClosureAction operationFailure(String errorType, String errorTitle, int statusCode, 
            String errorDetail, String errorParameterName, String errorParameterValue) {
        operationFailure = new OperationFailure();
        operationFailure.setErrorType(errorType);
        operationFailure.setErrorTitle(errorTitle);
        operationFailure.setStatusCode(statusCode);
        operationFailure.setErrorDetail(errorDetail);
        FailureDetails failureDetails = new FailureDetails();
        failureDetails.setAdditionalProperty(errorParameterName, errorParameterValue);
        operationFailure.setFailureDetails(failureDetails);
        return this;
    }

    @Override
    public void apply() {
        // Prepare request
        operationMethod = isInitiatedByIssuer ? "issuerClosure.execute" : "closure.execute";
        sendClosureRequest();
        // Process response
        if (operationFailure != null) {
            receiveErrorResponse();
        } else {
            receiveSuccessResponse();
        }
    }

    @Attachment
    private String createClosureRequest() {
        ClosureRequest request = new ClosureRequest();
        if (executionContext.getVariables().containsKey("sourceBindingId")) {
            request.setBindingIdentifier(executionContext.getVariable("sourceBindingId"));
        }
        if (executionContext.getVariables().containsKey("userId")) {
            request.setUserIdentifier(executionContext.getVariable("userId"));
        }
        if (!closureCode.isEmpty()) {
            request.setClosureReasonCode(closureCode);
        }
        if (!closureDescription.isEmpty()) {
            request.setClosureDescription(closureDescription);
        }
        return JsonUtils.toJsonString(request);
    }

    @Step("Execute account closure operation")
    private void sendClosureRequest() {
        http(action -> action
                .client("accountManagementRestClient")
                .send()
                .post("/${accountManagement.version}/${accountManagement.partner}/" + operationMethod)
                .header("Accept-Encoding", "gzip,deflate")
                .header("Content-Encoding", "UTF-8")
                .header("Content-Type", "application/json")
                .header("X-", "${accountManagement.partner}")
                .payload(createClosureRequest())
        );
    }

    private void receiveSuccessResponse() {
        http(action -> action
                .client("accountManagementRestClient")
                .receive()
                .response(HttpStatus.OK)
                .messageType(MessageType.JSON)
                .validationCallback(new JsonMappingValidationCallback<ClosureResponse>(ClosureResponse.class) {
                    @Override
                    public void validate(ClosureResponse payload, Map<String, Object> headers, TestContext testContext) {
                        assertEquals(payload.getOperationResult(), operationResult);
                        assertEquals(payload.getUserIdentifier(), executionContext.getVariable("userId"));
                        assertEquals(payload.getBindingIdentifier(), executionContext.getVariable("sourceBindingId"));
                        if (payload.getOperationResult().equals(FAILED)) {
                            assertEquals(payload.getOperationFailure().getFailureCode(), failureCode);
                            assertEquals(payload.getOperationFailure().getFailureMessage(), failureMessage);
                            assertEquals(payload.getOperationFailure().getFailureReason(), failureReason);
                        }
                    }
                })
        );
    }

    private void receiveErrorResponse() {
        http(action -> action
                .client("accountManagementRestClient")
                .receive()
                .response(HttpStatus.OK)
                .messageType(MessageType.JSON)
                .validationCallback(new JsonMappingValidationCallback<OperationFailure>(OperationFailure.class) {
                    @Override
                    public void validate(OperationFailure payload, Map<String, Object> headers, TestContext testContext) {
                        assertEquals(payload.getErrorType(), operationFailure.getErrorType());
                        assertEquals(payload.getErrorTitle(), operationFailure.getErrorTitle());
                        assertEquals(payload.getStatusCode(), operationFailure.getStatusCode());
                        assertEquals(payload.getErrorDetail(), operationFailure.getErrorDetail());
                        assertEquals(payload.getFailureDetails(), operationFailure.getFailureDetails());
                        assertNotNull(payload.getTraceIdentifier());
                    }
                })
        );
    }

    // Private HTTP action method placeholder
    private void http(HttpActionBuilder action) {
        action.execute();
    }

    // Enums and supporting classes
    public enum FailureCode {
        INVALID_REQUEST, INSUFFICIENT_PERMISSIONS, ACCOUNT_NOT_FOUND
    }

    public enum OperationResult {
        SUCCESS, FAILED
    }

    public enum OperationStatus {
        SUCCEEDED, FAILED
    }

    static class OperationResult {
        private static final OperationResult SUCCEEDED = new OperationStatus("SUCCEEDED");
        private static final OperationResult FAILED = new OperationStatus("FAILED");
    }
}

// Supporting classes
class ClosureRequest {
    private String bindingIdentifier;
    private String userIdentifier;
    private String closureReasonCode;
    private String closureDescription;

    // Getters and setters
    public String getBindingIdentifier() { return bindingIdentifier; }
    public void setBindingIdentifier(String bindingIdentifier) { this.bindingIdentifier = bindingIdentifier; }
    public String getUserIdentifier() { return userIdentifier; }
    public void setUserIdentifier(String userIdentifier) { this.userIdentifier = userIdentifier; }
    public String getClosureReasonCode() { return closureReasonCode; }
    public void setClosureReasonCode(String closureReasonCode) { this.closureReasonCode = closureReasonCode; }
    public String getClosureDescription() { return closureDescription; }
    public void setClosureDescription(String closureDescription) { this.closureDescription = closureDescription; }
}

class ClosureResponse {
    private OperationResult operationResult;
    private String userIdentifier;
    private String bindingIdentifier;
    private OperationFailure operationFailure;

    // Getters and setters
    public OperationResult getOperationResult() { return operationResult; }
    public void setOperationResult(OperationResult operationResult) { this.operationResult = operationResult; }
    public String getUserIdentifier() { return userIdentifier; }
    public void setUserIdentifier(String userIdentifier) { this.userIdentifier = userIdentifier; }
    public String getBindingIdentifier() { return bindingIdentifier; }
    public void setBindingIdentifier(String bindingIdentifier) { this.bindingIdentifier = bindingIdentifier; }
    public OperationFailure getOperationFailure() { return operationFailure; }
    public void setOperationFailure(OperationFailure operationFailure) { this.operationFailure = operationFailure; }
}

class OperationFailure {
    private String errorType;
    private String errorTitle;
    private int statusCode;
    private String errorDetail;
    private FailureDetails failureDetails;
    private String traceIdentifier;

    // Getters and setters
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getErrorTitle() { return errorTitle; }
    public void setErrorTitle(String errorTitle) { this.errorTitle = errorTitle; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
    public FailureDetails getFailureDetails() { return failureDetails; }
    public void setFailureDetails(FailureDetails failureDetails) { this.failureDetails = failureDetails; }
    public String getTraceIdentifier() { return traceIdentifier; }
    public void setTraceIdentifier(String traceIdentifier) { this.traceIdentifier = traceIdentifier; }
}

class FailureDetails {
    private Map<String, Object> additionalProperties;

    public void setAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}

class HttpActionBuilder {
    public HttpActionBuilder client(String clientName) { return this; }
    public HttpActionBuilder send() { return this; }
    public HttpActionBuilder post(String endpoint) { return this; }
    public HttpActionBuilder header(String name, String value) { return this; }
    public HttpActionBuilder payload(String content) { return this; }
    public HttpActionBuilder receive() { return this; }
    public HttpActionBuilder response(HttpStatus status) { return this; }
    public HttpActionBuilder messageType(MessageType type) { return this; }
    public HttpActionBuilder validationCallback(JsonMappingValidationCallback callback) { return this; }
    public void execute() {}
}

abstract class AbstractTestBehavior {
    public abstract void apply();
}

class JsonUtils {
    public static String toJsonString(Object obj) {
        // Implementation for JSON conversion
        return "{}";
    }
}
