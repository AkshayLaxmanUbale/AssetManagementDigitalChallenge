package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Transaction;
import com.db.awmd.challenge.domain.TransactionDetails;

import java.math.BigDecimal;

public interface TransactionService {
    TransactionDetails transferAmount(Transaction transaction);
}
