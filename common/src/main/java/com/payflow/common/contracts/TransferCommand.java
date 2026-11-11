package com.payflow.common.contracts;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record TransferCommand(
    @NotNull UUID transferId,
    @NotNull UUID fromAccount,
    @NotNull UUID toAccount,
    @NotBlank String amount,
    @NotBlank String currency) {}
