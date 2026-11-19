package com.example.vulnbank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long toUserId,
        @NotNull @Positive @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @Size(max = 200) String note
) {}
