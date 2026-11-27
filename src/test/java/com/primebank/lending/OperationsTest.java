package com.primebank.lending;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsTest {

    @Test
    void requestIdHasPrefixAndFixedLength() {
        String id = Operations.requestId("wgr", 10);
        assertEquals(10, id.length());
        assertTrue(id.matches("wgr[A-Z0-9]{7}"), id);
    }

    @Test
    void accountIsDerivedFromLoanId() {
        assertEquals("01782433366201", Operations.accountFromLoanId("01782433366101"));
        assertEquals("201", Operations.accountFromLoanId("101"));
    }

    @Test
    void amountsParseLikeThePythonCli() {
        BigDecimal fallback = new BigDecimal("1");
        assertEquals("40000", Operations.parseAmount("40,000.00", fallback).toPlainString());
        assertEquals("5201.99", Operations.parseAmount("5201.99", fallback).toPlainString());
        assertEquals(fallback, Operations.parseAmount("abc", fallback));
    }

    @Test
    void tokenIsFoundInBodyOrNestedData() {
        assertEquals("abc", ApiClient.extractToken("{\"access_token\":\"abc\"}"));
        assertEquals("xyz", ApiClient.extractToken("{\"data\":{\"token\":\"xyz\"}}"));
        assertEquals("raw-token", ApiClient.extractToken(" raw-token "));
    }
}
