package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transaction;
import com.db.awmd.challenge.domain.TransactionDetails;
import com.db.awmd.challenge.exception.InsufficientBalanceException;
import com.db.awmd.challenge.exception.InvalidAccountException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TransactionServiceImplTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountsService accountsService;

    @Mock
    private NotificationService notificationService;

    private final String ACC_ID_1 = "1";
    private final String ACC_ID_2 = "2";
    private final String ACC_ID_3 = "3";

    private final String INVALID_ID = "4";

    @Before
    public void setUp() throws Exception {
        this.accountsService.createAccount(new Account(ACC_ID_1, new BigDecimal(2000)));
        this.accountsService.createAccount(new Account(ACC_ID_2, new BigDecimal(2000)));
        this.accountsService.createAccount(new Account(ACC_ID_3, new BigDecimal(2000)));
    }

    @After
    public void tearDown() throws Exception {
        this.accountsService.getAccountsRepository().clearAccounts();
    }

    @Test
    public void depositAmountSuccess() throws Exception {
        //Arrange
        BigDecimal expected = new BigDecimal(2100);
        BigDecimal depositAmt = new BigDecimal(100);

        //Act
        this.accountsService.getAccount(ACC_ID_1).credit(depositAmt);

        //Assert
        Assert.assertEquals(expected, this.accountsService.getAccount(ACC_ID_1).getBalance());
    }

    @Test
    public void withdrawAmountSuccess() throws Exception {
        //Arrange
        BigDecimal expected = new BigDecimal(1900);
        BigDecimal withdrawAmt = new BigDecimal(100);

        //Act
        this.accountsService.getAccount(ACC_ID_1).debit(withdrawAmt);

        //Assert
        Assert.assertEquals(expected, this.accountsService.getAccount(ACC_ID_1).getBalance());
    }

    @Test(expected = InsufficientBalanceException.class)
    public void withdrawAmountInsufficientBalanceNoWithdraw() throws Exception {
        //Arrange
        BigDecimal withdrawAmt = new BigDecimal(2500);

        //Act
        this.accountsService.getAccount(ACC_ID_1).debit(withdrawAmt);
    }

    @Test
    public void transferSuccess() throws Exception {
        //Arrange
        BigDecimal expected1 = new BigDecimal(1900);
        BigDecimal expected2 = new BigDecimal(2100);
        Transaction transaction = new Transaction(ACC_ID_1, ACC_ID_2, new BigDecimal(100));

        //Act
        this.transactionService.transferAmount(transaction);

        //Assert
        Assert.assertEquals(expected1, this.accountsService.getAccount("1").getBalance());
        Assert.assertEquals(expected2, this.accountsService.getAccount("2").getBalance());
        Mockito.verify(notificationService , Mockito.atMost(2)).notifyAboutTransfer(Mockito.any(), Mockito.any());
    }

    @Test(expected = InvalidAccountException.class)
    public void transferWithInvalidAccountId() {
        //Arrange
        Transaction transaction = new Transaction(INVALID_ID, ACC_ID_2, new BigDecimal(100));

        //Act
        this.transactionService.transferAmount(transaction);
    }

    @Test
    public void transferConcurrentWithSameSenderAndSameReceivers() throws Exception {
        //Arrange
        Transaction transaction1 = new Transaction(ACC_ID_1, ACC_ID_2, new BigDecimal(100));
        Transaction transaction2 = new Transaction(ACC_ID_1, ACC_ID_2, new BigDecimal(100));

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Collection<Callable<TransactionDetails>> calls = new ArrayList<>();
        calls.add(() -> this.transactionService.transferAmount(transaction1));
        calls.add(() -> this.transactionService.transferAmount(transaction2));

        //Act
        List<Future<TransactionDetails>> results = executorService.invokeAll(calls);

        //Assert
        for(Future<TransactionDetails> detail : results) {
            Assert.assertNotNull(detail.get());
            Assert.assertNotNull(detail.get().getTransactionId());
        }
        Assert.assertEquals(this.accountsService.getAccount(ACC_ID_1).getBalance(), new BigDecimal(1800));
        Assert.assertEquals(this.accountsService.getAccount(ACC_ID_2).getBalance(), new BigDecimal(2200));
    }

    @Test
    public void transferConcurrentSenderToReceiverAndViceVersa() throws Exception {
        //Arrange
        Transaction transaction1 = new Transaction(ACC_ID_1, ACC_ID_2, new BigDecimal(100));
        Transaction transaction2 = new Transaction(ACC_ID_2, ACC_ID_1, new BigDecimal(100));

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Collection<Callable<TransactionDetails>> calls = new ArrayList<>();
        calls.add(() -> this.transactionService.transferAmount(transaction1));
        calls.add(() -> this.transactionService.transferAmount(transaction2));

        //Act
        List<Future<TransactionDetails>> results = executorService.invokeAll(calls);

        //Assert
        for(Future<TransactionDetails> detail : results) {
            Assert.assertNotNull(detail.get());
            Assert.assertNotNull(detail.get().getTransactionId());
        }
        Assert.assertEquals(this.accountsService.getAccount(ACC_ID_1).getBalance(), new BigDecimal(2000));
        Assert.assertEquals(this.accountsService.getAccount(ACC_ID_2).getBalance(), new BigDecimal(2000));
    }
}