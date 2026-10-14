package com.example.aml.account.dto;

import com.example.aml.account.Account;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@NotNull Account.Status status) {}
