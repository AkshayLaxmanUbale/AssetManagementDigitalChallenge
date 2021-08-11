package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transaction;
import com.db.awmd.challenge.exception.InsufficientBalanceException;
import com.db.awmd.challenge.exception.InvalidAccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountsService accountsService;

    private final NotificationService notificationService;

    @Override
    public void transfer(Transaction transaction) throws InsufficientBalanceException {
        //Validate accounts
        Account sender = accountsService.getAccount(transaction.getSenderId());
        if(sender == null) {
            throw new InvalidAccountException(String.format("Account does not exists for id = %s", transaction.getSenderId()));
        }

        Account receiver = accountsService.getAccount(transaction.getReceiverId());
        if(receiver == null) {
            throw new InvalidAccountException(String.format("Account does not exists for id = %s", transaction.getReceiverId()));
        }

        sender.debit(transaction.getAmount());

        receiver.credit(transaction.getAmount());

        //Notify
        notificationService.notifyAboutTransfer(sender, String.format("Debited %s amount!!", transaction.getAmount()));
        notificationService.notifyAboutTransfer(receiver, String.format("Credited %s amount!!", transaction.getAmount()));
    }
}
