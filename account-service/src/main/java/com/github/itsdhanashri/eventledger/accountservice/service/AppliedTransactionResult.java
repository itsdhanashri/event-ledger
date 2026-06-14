package com.github.itsdhanashri.eventledger.accountservice.service;

import com.github.itsdhanashri.eventledger.accountservice.dto.response.TransactionResponse;

public record AppliedTransactionResult(TransactionResponse response, boolean duplicate) {
}
