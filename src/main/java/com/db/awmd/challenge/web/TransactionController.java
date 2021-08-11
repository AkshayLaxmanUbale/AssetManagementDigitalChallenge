package com.db.awmd.challenge.web;

import com.db.awmd.challenge.domain.Transaction;
import com.db.awmd.challenge.exception.InsufficientBalanceException;
import com.db.awmd.challenge.exception.InvalidAccountException;
import com.db.awmd.challenge.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/transaction")
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @Autowired
    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> transact(@RequestBody @Valid Transaction transaction) {
        log.info("Starting transaction {}", transaction);

        try {
            this.transactionService.transfer(transaction);
        } catch (InvalidAccountException iae) {
            log.error("Error for transaction {} : {}", transaction, iae);
            return new ResponseEntity<>(iae.getMessage(), HttpStatus.NOT_FOUND);
        } catch (InsufficientBalanceException ibe) {
            log.error("Error for transaction {} : {}", transaction, ibe);
            return new ResponseEntity<>(ibe.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            log.error("Error for transaction {} : {}", transaction, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("Transaction Completed Successfully : {}", transaction);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
