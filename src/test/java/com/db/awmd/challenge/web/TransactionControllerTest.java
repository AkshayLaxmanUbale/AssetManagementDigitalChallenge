package com.db.awmd.challenge.web;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transaction;
import com.db.awmd.challenge.domain.TransactionDetails;
import com.db.awmd.challenge.service.AccountsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
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

    public TransactionDetails fromJson(String json) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, TransactionDetails.class);
    }

    @Test
    public void makeTransaction() throws Exception {
        //Arrange and Act
        MvcResult result = this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":100}"))
                .andReturn();

        //Assert
        Assert.assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
        TransactionDetails details = fromJson(result.getResponse().getContentAsString());
        Assert.assertNotNull(details.getTransactionId());
        Assert.assertEquals("Transaction Successful!!", details.getMessage());
        Assert.assertEquals(new BigDecimal(100), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(300), this.accountsService.getAccount(ACC_ID_2).getBalance());
    }

    @Test
    public void makeTransactionMissingData() throws Exception {
        //Arrange, Act and Assert
        this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"2\",\"amount\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void makeTransactionInvalidAmount() throws Exception {
        //Arrange, Act and Assert
        this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":-100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void makeTransactionWithZeroAmount() throws Exception {
        //Arrange, Act and Assert
        this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void makeTransactionAmountGreaterThanBalance() throws Exception {
        //Arrange & Act
        MvcResult result =  this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":1000}"))
                .andReturn();

        //Assert
        Assert.assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
        TransactionDetails details = fromJson(result.getResponse().getContentAsString());
        Assert.assertEquals("Insufficient Account Balance.", details.getMessage());
    }

    @Test
    public void makeTransactionInvalidAccountId() throws Exception {
        //Arrange & Act
        MvcResult result = this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"4\",\"to\":\"2\",\"amount\":1000}"))
                .andReturn();

        //Assert
        Assert.assertEquals(HttpStatus.NOT_FOUND.value(), result.getResponse().getStatus());
        TransactionDetails details = fromJson(result.getResponse().getContentAsString());
        Assert.assertEquals("Account does not exists for id = 4", details.getMessage());
    }

    //Check Thread Safety
    @Test
    public void makeConcurrentTransactionCallsFromOneAccountToAnother() throws Exception {
        //Arrange
        int transactionAmount = 5;
        int expected1Balance = INITIAL_BAL - transactionAmount * MAX_CALLS;
        int expected2Balance = INITIAL_BAL + transactionAmount * MAX_CALLS;
        
        Collection<Callable<MvcResult>> calls = new ArrayList<>();
        IntStream.rangeClosed(1,MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":" +transactionAmount+ "}")).andReturn());
        });

        //Act
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<MvcResult>> results = executorService.invokeAll(calls);

        //Assert
        for(Future<MvcResult> result : results) {
            Assert.assertEquals(HttpStatus.OK.value(), result.get().getResponse().getStatus());
        }
        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
    }

    @Test
    public void makeConcurrentTransactionBalanceShouldNotBeLessThanZero() throws Exception {
        //Arrange
        int transactionAmount = 40;
        int expected1Balance = 0;
        int expected2Balance = INITIAL_BAL * 2;

        Collection<Callable<MvcResult>> calls = new ArrayList<>();
        IntStream.rangeClosed(1,MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":" +transactionAmount+ "}")).andReturn());
        });

        //Act
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<MvcResult>> results = executorService.invokeAll(calls);

        int count = 0;
        for(Future<MvcResult> result : results) {
            TransactionDetails detail = fromJson(result.get().getResponse().getContentAsString());
            if(detail.getMessage().equals("Insufficient Account Balance.")) {
                count++;
            }
        }

        //Assert
        Assert.assertEquals(5, count);
        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
    }

    @Test
    public void makeConcurrentTransactionsInvalidAccountId() throws Exception {
        //Arrange
        int transactionAmount = 40;
        int expected1Balance = INITIAL_BAL;

        Collection<Callable<MvcResult>> calls = new ArrayList<>();
        IntStream.rangeClosed(1,MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"4\",\"amount\":" +transactionAmount+ "}")).andReturn());
        });

        //Act
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<MvcResult>> results = executorService.invokeAll(calls);

        int count = 0;
        for(Future<MvcResult> result : results) {
            TransactionDetails detail = fromJson(result.get().getResponse().getContentAsString());
            if(detail.getMessage().equals("Account does not exists for id = 4")) {
                count++;
            }
        }

        //Assert
        Assert.assertEquals(MAX_CALLS, count);
        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
    }

    @Test
    public void makeConcurrentTransactionCallsFromDifferentAccountsToSameAccount() throws Exception {
        //Arrange
        int transactionAmount = 5;
        int expected1Balance = INITIAL_BAL + 2 * transactionAmount * MAX_CALLS;
        int expected2Balance = INITIAL_BAL - transactionAmount * MAX_CALLS;
        int expected3Balance = INITIAL_BAL - transactionAmount * MAX_CALLS;

        Collection<Callable<MvcResult>> calls = new ArrayList<>();
        IntStream.rangeClosed(1, MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"2\",\"to\":\"1\",\"amount\":"+transactionAmount+"}")).andReturn());
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"3\",\"to\":\"1\",\"amount\":"+transactionAmount+"}")).andReturn());
        });

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.invokeAll(calls);

        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
        Assert.assertEquals(new BigDecimal(expected3Balance), this.accountsService.getAccount(ACC_ID_3).getBalance());
    }

    @Test
    public void makeConcurrentTransactionCallsFromSingleAccountToDifferentAccounts() throws Exception {
        //Arrange
        int transactionAmount = 5;
        int expected1Balance = INITIAL_BAL - 2 * transactionAmount * MAX_CALLS;
        int expected2Balance = INITIAL_BAL + transactionAmount * MAX_CALLS;
        int expected3Balance = INITIAL_BAL + transactionAmount * MAX_CALLS;

        Collection<Callable<MvcResult>> calls = new ArrayList<>();
        IntStream.rangeClosed(1, MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":"+transactionAmount+"}")).andReturn());
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"3\",\"amount\":"+transactionAmount+"}")).andReturn());
        });

        //Act
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.invokeAll(calls);

        //Assert
        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
        Assert.assertEquals(new BigDecimal(expected3Balance), this.accountsService.getAccount(ACC_ID_3).getBalance());
    }

    @Test
    public void makeConcurrentTransactionCallsToAndFromAccounts() throws Exception {
        //Arrange
        int transactionAmount = 5;
        int expected1Balance = INITIAL_BAL;
        int expected2Balance = INITIAL_BAL;

        Collection<Callable<MvcResult>> calls = new ArrayList<>();
        IntStream.rangeClosed(1, MAX_CALLS).forEach(i -> {
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"1\",\"to\":\"2\",\"amount\":"+transactionAmount+"}")).andReturn());
            calls.add(() -> this.mockMvc.perform(post("/v1/transaction/transfer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"from\":\"2\",\"to\":\"1\",\"amount\":"+transactionAmount+"}")).andReturn());
        });

        //Act
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.invokeAll(calls);

        //Assert
        Assert.assertEquals(new BigDecimal(expected1Balance), this.accountsService.getAccount(ACC_ID_1).getBalance());
        Assert.assertEquals(new BigDecimal(expected2Balance), this.accountsService.getAccount(ACC_ID_2).getBalance());
    }
}