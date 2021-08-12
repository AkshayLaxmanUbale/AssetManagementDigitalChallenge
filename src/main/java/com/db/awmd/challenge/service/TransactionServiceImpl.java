package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transaction;
import com.db.awmd.challenge.domain.TransactionDetails;
import com.db.awmd.challenge.exception.InsufficientBalanceException;
import com.db.awmd.challenge.exception.InvalidAccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountsService accountsService;

    private final NotificationService notificationService;

    @Override
    public TransactionDetails transferAmount(Transaction transaction) throws InsufficientBalanceException {
        //Validate accounts
        Account sender = accountsService.getAccount(transaction.getSenderId());
        if(sender == null) {
            throw new InvalidAccountException(String.format("Account does not exists for id = %s", transaction.getSenderId()));
        }

        Account receiver = accountsService.getAccount(transaction.getReceiverId());
        if(receiver == null) {
            throw new InvalidAccountException(String.format("Account does not exists for id = %s", transaction.getReceiverId()));
        }

        //Same account transfer not allowed.
        if(sender.getAccountId().equals(receiver.getAccountId())) {
            throw new InvalidAccountException("Same Account transfer not supported.");
        }

        sender.debit(transaction.getAmount());

        receiver.credit(transaction.getAmount());

        //Create random transactionId
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .transactionId(UUID.randomUUID().toString())
                .transaction(transaction)
                .message("Transaction Successful!!")
                .build();

        //Notify
        notificationService.notifyAboutTransfer(sender, String.format("Debited %s amount!!", transaction.getAmount()));
        notificationService.notifyAboutTransfer(receiver, String.format("Credited %s amount!!", transaction.getAmount()));

        return transactionDetails;
    }
}
