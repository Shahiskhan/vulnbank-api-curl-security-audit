package com.example.vulnbank.dto;

import java.math.BigDecimal;

public record TransactionResponse(Long id, Long fromUserId, Long toUserId, BigDecimal amount, String note) {}
