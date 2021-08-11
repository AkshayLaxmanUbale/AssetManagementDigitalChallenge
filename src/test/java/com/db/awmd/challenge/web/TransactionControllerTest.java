package com.db.awmd.challenge.web;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.service.AccountsService;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import javax.validation.constraints.Max;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@WebAppConfiguration
public class TransactionControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final String ACC_ID_1 = "1";
    private final String ACC_ID_2 = "2";
    private final String ACC_ID_3 = "3";
    private final int MAX_CALLS = 10;
    private final int INITIAL_BAL = 200;

    @Before
    public void prepareMockMvc() {
        this.mockMvc = webAppContextSetup(this.webApplicationContext).build();
        this.accountsService.createAccount(new Account(ACC_ID_1, new BigDecimal(200)));
        this.accountsService.createAccount(new Account(ACC_ID_2, new BigDecimal(200)));
        this.accountsService.createAccount(new Account(ACC_ID_3, new BigDecimal(200)));
    }

    @After
    public void tearDown() throws Exception {
        this.accountsService.getAccountsRepository().clearAccounts();
    }

    @Test
    public void makeTransaction() throws Exception {
        //Arrange and Act
        this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":100}")).andExpect(status().isOk());

        //Assert
        Assert.assertEquals(new BigDecimal(100), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(300), this.accountsService.getAccount(ACC_ID_2).getBalance());
    }

    @Test
    public void makeTransactionMissingData() throws Exception {
        this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"2\",\"amount\":100}")).andExpect(status().isBadRequest());
    }

    @Test
    public void makeTransactionInvalidAmount() throws Exception {
        this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":-100}")).andExpect(status().isBadRequest());
    }

    @Test
    public void makeTransactionAmountGreaterThanBalance() throws Exception {
        this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Insufficient Account Balance."));
    }

    @Test
    public void makeTransactionInvalidAccountId() throws Exception {
        this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"4\",\"to\":\"2\",\"amount\":1000}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Account does not exists for id = 4"));
    }

    //Check Thread Safety
    @Test
    public void makeConcurrentTransactionCallsFromOneAccountToAnother() throws Exception {
        int transactionAmount = 5;
        int expected1Balance = INITIAL_BAL - transactionAmount * MAX_CALLS;
        int expected2Balance = INITIAL_BAL + transactionAmount * MAX_CALLS;
        
        Collection<Callable<ResultActions>> calls = new ArrayList<>();
        IntStream.rangeClosed(1,MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":" +transactionAmount+ "}")));
        });

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.invokeAll(calls);

        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
    }

    @Test
    public void makeConcurrentTransactionBalanceShouldNotBeLessThanZero() throws Exception {
        int transactionAmount = 40;
        int expected1Balance = 0;
        int expected2Balance = INITIAL_BAL * 2;

        Collection<Callable<ResultActions>> calls = new ArrayList<>();
        IntStream.rangeClosed(1,MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":" +transactionAmount+ "}")));
        });

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<ResultActions>> results = executorService.invokeAll(calls);

        int count = 0;
        for(Future<ResultActions> action : results) {
            if(action.get().andReturn().getResponse().getContentAsString().equals("Insufficient Account Balance.")) {
                count++;
            }
        }
        Assert.assertEquals(5, count);
        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
    }

    @Test
    public void makeConcurrentTransactionsInvalidAccountId() throws Exception {
        int transactionAmount = 40;
        int expected1Balance = INITIAL_BAL;

        Collection<Callable<ResultActions>> calls = new ArrayList<>();
        IntStream.rangeClosed(1,MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"4\",\"amount\":" +transactionAmount+ "}")));
        });

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<ResultActions>> results = executorService.invokeAll(calls);

        int count = 0;
        for(Future<ResultActions> action : results) {
            if(action.get().andReturn().getResponse().getContentAsString().equals("Account does not exists for id = 4")) {
                count++;
            }
        }
        Assert.assertEquals(MAX_CALLS, count);
        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
    }

    @Test
    public void makeConcurrentTransactionCallsFromDifferentAccountsToSameAccount() throws Exception {
        int transactionAmount = 5;
        int expected1Balance = INITIAL_BAL + 2 * transactionAmount * MAX_CALLS;
        int expected2Balance = INITIAL_BAL - transactionAmount * MAX_CALLS;
        int expected3Balance = INITIAL_BAL - transactionAmount * MAX_CALLS;

        Collection<Callable<ResultActions>> calls = new ArrayList<>();
        IntStream.rangeClosed(1, MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"2\",\"to\":\"1\",\"amount\":"+transactionAmount+"}")));
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"3\",\"to\":\"1\",\"amount\":"+transactionAmount+"}")));
        });

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.invokeAll(calls);

        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
        Assert.assertEquals(new BigDecimal(expected3Balance), this.accountsService.getAccount(ACC_ID_3).getBalance());
    }

    @Test
    public void makeConcurrentTransactionCallsFromSingleAccountToDifferentAccounts() throws Exception {
        int transactionAmount = 5;
        int expected1Balance = INITIAL_BAL - 2 * transactionAmount * MAX_CALLS;
        int expected2Balance = INITIAL_BAL + transactionAmount * MAX_CALLS;
        int expected3Balance = INITIAL_BAL + transactionAmount * MAX_CALLS;

        Collection<Callable<ResultActions>> calls = new ArrayList<>();
        IntStream.rangeClosed(1, MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":"+transactionAmount+"}")));
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"3\",\"amount\":"+transactionAmount+"}")));
        });

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.invokeAll(calls);

        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
        Assert.assertEquals(new BigDecimal(expected3Balance), this.accountsService.getAccount(ACC_ID_3).getBalance());
    }

    @Test
    public void makeConcurrentTransactionCallsToAndFromAccounts() throws Exception {
        int transactionAmount = 5;
        int expected1Balance = INITIAL_BAL;
        int expected2Balance = INITIAL_BAL;

        Collection<Callable<ResultActions>> calls = new ArrayList<>();
        IntStream.rangeClosed(1, MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":"+transactionAmount+"}")));
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"2\",\"to\":\"1\",\"amount\":"+transactionAmount+"}")));
        });

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.invokeAll(calls);

        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
    }
}