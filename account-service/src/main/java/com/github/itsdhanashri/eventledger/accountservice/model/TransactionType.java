package com.github.itsdhanashri.eventledger.accountservice.model;

public enum TransactionType {
    CREDIT,
    DEBIT;

    public static TransactionType from(String value) {
        return TransactionType.valueOf(value.toUpperCase());
    }
}
