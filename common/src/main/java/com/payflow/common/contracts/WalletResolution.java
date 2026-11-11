package com.payflow.common.contracts;

import java.util.UUID;

public record WalletResolution(UUID userId, UUID accountId, String currency, String status) {}
