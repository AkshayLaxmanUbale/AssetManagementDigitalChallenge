package com.db.awmd.challenge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class Transaction {

    @NotNull
    @NotEmpty
    private String senderId;

    @NotNull
    @NotEmpty
    private String receiverId;

    @NotNull
    @Min(value = 0, message = "Transfer amount should be greater than 0.")
    private BigDecimal amount;

    public Transaction() {
        amount = BigDecimal.ZERO;
    }

    @JsonCreator
    public Transaction(@JsonProperty("from") String senderId,
                       @JsonProperty("to") String receiverId,
                       @JsonProperty("amount") BigDecimal amount) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amount = amount;
    }
}
