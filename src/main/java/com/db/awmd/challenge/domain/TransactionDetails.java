package com.db.awmd.challenge.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransactionDetails {
    private String transactionId;
    private Transaction transaction;
    private String message;
}
