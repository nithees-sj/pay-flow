package com.payflow.common.contracts;

import java.util.UUID;

public record TransferOutcome(UUID transferId, String status, String failureCode) {}
