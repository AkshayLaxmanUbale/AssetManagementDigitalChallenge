package com.db.awmd.challenge.domain;

import com.db.awmd.challenge.exception.InsufficientBalanceException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

public class Account {

  @Getter
  @NotNull
  @NotEmpty
  private final String accountId;

  @Getter
  @Setter
  @NotNull
  @Min(value = 0, message = "Initial balance must be positive.")
  private BigDecimal balance;

  @JsonIgnore
  private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

  public Account(String accountId) {
    this.accountId = accountId;
    this.balance = BigDecimal.ZERO;
  }

  @JsonCreator
  public Account(@JsonProperty("accountId") String accountId,
    @JsonProperty("balance") BigDecimal balance) {
    this.accountId = accountId;
    this.balance = balance;
  }

  public void credit(BigDecimal amount) {
    readWriteLock.writeLock().lock();

    try{
      balance = balance.add(amount);
    } finally {
      readWriteLock.writeLock().unlock();
    }
  }

  public void debit(BigDecimal amount) {
    readWriteLock.writeLock().lock();

    try{
      if(balance.compareTo(amount) < 0) {
        throw new InsufficientBalanceException("Insufficient Account Balance.");
      }
      balance = balance.subtract(amount);
    } finally {
      readWriteLock.writeLock().unlock();
    }
  }
}
