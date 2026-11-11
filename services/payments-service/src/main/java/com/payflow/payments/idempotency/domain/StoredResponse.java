package com.payflow.payments.idempotency.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record StoredResponse(int status, JsonNode body, String location) {}
