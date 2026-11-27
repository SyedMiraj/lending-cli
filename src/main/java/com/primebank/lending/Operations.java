package com.primebank.lending;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

/** The eight Retail menu operations, plus the values remembered between them. */
final class Operations {

    static final String BINDING_PATH = "/now-lending/api/merchant/v1/customer/profile/post/binding";
    static final String QUESTIONS_PATH = "/now-lending/api/merchant/v1/get/questions";
    static final String ANSWERS_PATH = "/now-lending/api/merchant/v1/post/answers";
    static final String ELIGIBILITY_PATH = "/now-lending/api/merchant/v1/loan/get/eligibility";
    static final String INVOICES_PATH = "/now-lending/api/merchant/v1/post/invoices";
    static final String DISBURSEMENT_INFO_PATH = "/now-lending/api/merchant/v1/loan/get/disbursement-info";
    static final String LOAN_DETAILS_PATH = "/now-lending/api/merchant/v1/loan/get/details";
    static final String SETTLEMENT_PATH = "/now-lending/api/merchant/v1/loan/post/settlement";

    private static final String ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final Config.Merchant merchant;
    private final ApiClient api;
    private final Prompt prompt;

    // Values carried from one operation to the next within a merchant session.
    private JsonNode lastBindingResponse;
    private JsonNode lastAnswersResponse;
    private String lastAccountNumber;
    private String lastAnswerResponseId;
    private String lastInvoiceNumber;
    private BigDecimal lastInvoiceAmount;
    private String lastLoanId;
    private BigDecimal lastSettlementAmount;

    Operations(Config.Merchant merchant, ApiClient api, Prompt prompt) {
        this.merchant = merchant;
        this.api = api;
        this.prompt = prompt;
    }

    void customerBinding() {
        header("Customer Binding");
        String mobileNo = prompt.withDefault("Mobile No", merchant.def("mobileNo"));
        String nid = prompt.withDefault("NID", merchant.def("nid"));

        ObjectNode body = baseRequest("serviceCode");
        body.put("mobileNo", mobileNo);
        body.put("nid", nid);
        putIfPresent(body, "clientCode", merchant.def("bindingClientCode"));

        HttpResponse<String> resp = send(BINDING_PATH, body);
        if (resp != null && resp.statusCode() == 200) {
            lastBindingResponse = objectOrNull(resp.body());
        }
    }

    void questionsList() {
        header("Questions List");
        String loanSegment = prompt.withDefault("Loan Segment", merchant.def("loanSegment"));
        String encKey = prompt.withDefault("Encryption Key (encKey)", merchant.def("encKey"));

        ObjectNode body = baseRequest("questionServiceCode");
        body.putObject("payload")
                .put("loanSegment", loanSegment)
                .put("encKey", encKey);
        putIfPresent(body, "clientCode", merchant.def("clientCode"));

        send(QUESTIONS_PATH, body);
    }

    void creditAssessment() {
        header("Credit Assessment");
        String accountNumber = prompt.withDefault("Account Number", defaultAccountNumber());

        ObjectNode body = baseRequest("answerServiceCode");
        body.putObject("payload")
                .put("loanSegment", merchant.def("loanSegment"))
                .put("accountNumber", accountNumber)
                .put("merchantCode", merchantCode())
                .set("answerCategories", merchant.answerCategories.deepCopy());
        putIfPresent(body, "clientCode", merchant.def("clientCode"));

        HttpResponse<String> resp = send(ANSWERS_PATH, body);
        if (resp != null) {
            lastAccountNumber = accountNumber;
            if (resp.statusCode() == 200) {
                lastAnswersResponse = objectOrNull(resp.body());
            }
        }
    }

    void checkEligibility() {
        header("Check Eligibility");
        String defaultAnswerId = firstNonNull(
                ApiClient.firstText(lastAnswersResponse, "answerResponseId", "responseId", "answer_response_id"),
                ApiClient.firstText(dataOf(lastAnswersResponse), "answerResponseId", "responseId", "answer_response_id"),
                lastAnswerResponseId,
                merchant.def("answerResponseId"));

        String accountNumber = prompt.withDefault("Account Number", defaultAccountNumber());
        String answerResponseId = prompt.withDefault("Answer Response ID", defaultAnswerId);

        ObjectNode body = baseRequest("eligibilityServiceCode");
        body.putObject("payload")
                .put("loanSegment", merchant.def("loanSegment"))
                .put("accountNumber", accountNumber)
                .put("answerResponseId", answerResponseId)
                .put("merchantCode", merchantCode());
        putIfPresent(body, "clientCode", merchant.def("clientCode"));

        if (send(ELIGIBILITY_PATH, body) != null) {
            lastAccountNumber = accountNumber;
            lastAnswerResponseId = answerResponseId;
        }
    }

    void submitInvoice() {
        header("Submit Invoice");
        BigDecimal defaultAmount = lastInvoiceAmount != null
                ? lastInvoiceAmount : merchant.defaults.path("totalAmount").decimalValue();
        LocalDate today = LocalDate.now();

        String accountNumber = prompt.withDefault("Account Number", defaultAccountNumber());
        String invoiceNumber = prompt.withDefault("Invoice Number", defaultInvoiceNumber());
        BigDecimal totalAmount = parseAmount(prompt.withDefault("Amount", plain(defaultAmount)), defaultAmount);

        ObjectNode body = baseRequest("invoiceServiceCode");
        ObjectNode payload = body.putObject("payload")
                .put("merchantCode", merchantCode())
                .put("accountNumber", accountNumber)
                .put("invoiceNumber", invoiceNumber)
                .put("issueDate", today.minusDays(7).toString())
                .put("dueDate", today.plusMonths(1).toString())
                .put("description", firstNonNull(merchant.def("description"), "Food Items"));
        JsonNode quantity = merchant.defaults.get("quantity");
        payload.set("quantity", quantity != null ? quantity : payload.numberNode(10));
        payload.put("totalAmount", totalAmount);
        putIfPresent(body, "clientCode", merchant.def("clientCode"));

        if (send(INVOICES_PATH, body) != null) {
            lastAccountNumber = accountNumber;
            lastInvoiceNumber = invoiceNumber;
            lastInvoiceAmount = totalAmount;
        }
    }

    void loanInformation() {
        header("Loan Information");
        String invoiceNo = prompt.withDefault("Invoice Number", defaultInvoiceNumber());

        ObjectNode body = baseRequest("disbursementInfoServiceCode");
        body.put("invoiceNo", invoiceNo);
        body.put("merchantCode", merchantCode());
        putIfPresent(body, "clientCode", merchant.def("clientCode"));

        if (send(DISBURSEMENT_INFO_PATH, body) != null) {
            lastInvoiceNumber = invoiceNo;
        }
    }

    void settlementRequest() {
        header("Settlement Request");
        String loanId = prompt.withDefault("Loan ID", defaultLoanId());

        ObjectNode body = baseRequest("loanDetailsServiceCode");
        body.putObject("payload")
                .put("merchantCode", merchantCode())
                .put("loanId", loanId);
        putIfPresent(body, "clientCode", merchant.def("clientCode"));

        if (send(LOAN_DETAILS_PATH, body) != null) {
            lastLoanId = loanId;
        }
    }

    void settlement() {
        header("Settlement");
        BigDecimal defaultAmount = lastSettlementAmount != null
                ? lastSettlementAmount : merchant.defaults.path("settlementAmount").decimalValue();

        String loanId = prompt.withDefault("Loan ID", defaultLoanId());
        BigDecimal amount = parseAmount(prompt.withDefault("Settlement Amount", plain(defaultAmount)), defaultAmount);
        String accountNumber = accountFromLoanId(loanId);

        ObjectNode body = baseRequest("settlementServiceCode");
        body.putObject("payload")
                .put("loanId", loanId)
                .put("merchantCode", merchantCode())
                .put("accountNumber", accountNumber)
                .put("settlementAmount", amount);
        putIfPresent(body, "clientCode", merchant.def("clientCode"));

        if (send(SETTLEMENT_PATH, body) != null) {
            lastLoanId = loanId;
            lastAccountNumber = accountNumber;
            lastSettlementAmount = amount;
        }
    }

    // ---- helpers -------------------------------------------------------------

    private void header(String title) {
        System.out.println();
        System.out.println("--- " + title + " (" + merchant.name + ") ---");
        System.out.println("(Press Enter to keep the default value)");
    }

    /** Common envelope fields shared by every request, in the order the API expects. */
    private ObjectNode baseRequest(String serviceCodeField) {
        ObjectNode body = Config.JSON.createObjectNode();
        body.put("requesterId", merchant.def("requesterId"));
        body.put("requestId", requestId(firstNonNull(merchant.def("requestIdPrefix"), "mrcn"), 10));
        body.put("clientToken", merchant.def("clientToken"));
        body.put("serviceCode", merchant.def(serviceCodeField));
        body.put("clientChannel", merchant.def("clientChannel"));
        return body;
    }

    private HttpResponse<String> send(String path, ObjectNode body) {
        HttpResponse<String> resp = api.post(path, body);
        if (resp != null) {
            ApiClient.printResponse(resp);
        }
        return resp;
    }

    private String merchantCode() {
        return firstNonNull(merchant.def("merchantCode"), merchant.key);
    }

    private String defaultAccountNumber() {
        String[] keys = {"accountNumber", "accountNo", "account_number"};
        return firstNonNull(
                ApiClient.firstText(lastBindingResponse, keys),
                ApiClient.firstText(dataOf(lastBindingResponse), keys),
                lastAccountNumber,
                merchant.def("accountNumber"));
    }

    private String defaultInvoiceNumber() {
        return firstNonNull(lastInvoiceNumber, merchant.def("invoiceNumber"));
    }

    private String defaultLoanId() {
        return firstNonNull(lastLoanId, merchant.def("loanId"));
    }

    private static JsonNode dataOf(JsonNode node) {
        return node == null ? null : node.get("data");
    }

    private static JsonNode objectOrNull(String body) {
        JsonNode json = ApiClient.parse(body);
        return json != null && json.isObject() ? json : null;
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** Request ID of exactly {@code length} characters: prefix + random A-Z/0-9. */
    static String requestId(String prefix, int length) {
        String clean = prefix.length() > length ? prefix.substring(0, length) : prefix;
        StringBuilder id = new StringBuilder(clean);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (id.length() < length) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }

    /** Drops the last 3 characters of the loan ID and appends "201". */
    static String accountFromLoanId(String loanId) {
        return (loanId.length() > 3 ? loanId.substring(0, loanId.length() - 3) : "") + "201";
    }

    /** Parses an amount like "40,000" or "5201.99"; falls back to the default on bad input. */
    static BigDecimal parseAmount(String raw, BigDecimal fallback) {
        try {
            return normalize(new BigDecimal(raw.replace(",", "").trim()));
        } catch (NumberFormatException e) {
            System.out.println("[-] Invalid amount '" + raw + "', using default " + plain(fallback) + ".");
            return fallback;
        }
    }

    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static String plain(BigDecimal value) {
        return normalize(value).toPlainString();
    }
}
