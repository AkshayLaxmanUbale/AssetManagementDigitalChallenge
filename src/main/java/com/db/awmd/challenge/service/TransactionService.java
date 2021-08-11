package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Transaction;

import java.math.BigDecimal;

public interface TransactionService {

    void transfer(Transaction transaction);

}
