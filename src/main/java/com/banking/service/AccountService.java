package com.banking.service;

import com.banking.dto.AccountRequest;
import com.banking.entity.Account;
import com.banking.entity.Transaction;
import com.banking.entity.User;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Transactional
    public Account createAccount(String username, AccountRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getVerified()) {
            throw new RuntimeException("User account is not verified");
        }

        if (accountRepository.existsByUserAndActive(user, true)) {
            throw new RuntimeException("Each user can only have one active account.");
        }

        // Check if account number already exists
        if (accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new RuntimeException("Account number already exists. Please choose a different number.");
        }

        Account account = new Account();
        account.setAccountNumber(request.getAccountNumber());
        account.setAccountType(request.getAccountType());
        account.setBalance(request.getInitialDeposit());
        account.setPin(request.getPin()); // Store PIN (in production, this should be hashed)
        account.setUser(user);
        account.setActive(true);

        account = accountRepository.save(account);

        // Create initial deposit transaction if amount > 0
        if (request.getInitialDeposit().compareTo(BigDecimal.ZERO) > 0) {
            Transaction transaction = new Transaction();
            transaction.setType(Transaction.TransactionType.DEPOSIT);
            transaction.setAmount(request.getInitialDeposit());
            transaction.setBalanceAfter(account.getBalance());
            transaction.setAccount(account);
            transaction.setDescription("Initial deposit");
            transaction.setStatus(Transaction.TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);
        }

        return account;
    }

    public List<Account> getUserAccounts(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return accountRepository.findByUserAndActive(user, true);
    }

    public Account getAccountByNumber(String accountNumber, String username) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // Verify the account belongs to the user
        if (!account.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Unauthorized access to account");
        }

        return account;
    }

    @Transactional
    public String deleteAccount(String accountNumber, String username) {
        Account account = getAccountByNumber(accountNumber, username);

        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Cannot delete account with non-zero balance");
        }

        account.setActive(false);
        accountRepository.save(account);

        return "Account deactivated successfully";
    }
}
