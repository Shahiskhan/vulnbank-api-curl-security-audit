package com.example.vulnbank.config;

import com.example.vulnbank.model.BankTransaction;
import com.example.vulnbank.model.User;
import com.example.vulnbank.repository.TransactionRepository;
import com.example.vulnbank.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
public class DemoData {
    @Bean
    CommandLineRunner seedDemoData(
            UserRepository users,
            TransactionRepository transactions,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            User admin = createUser(users, passwordEncoder, "admin", "admin-password-123",
                    "admin@example.test", "ADMIN", "10000.00");
            User alice = createUser(users, passwordEncoder, "alice", "alice-password-123",
                    "alice@example.test", "USER", "2500.00");
            User bob = createUser(users, passwordEncoder, "bob", "bob-password-123",
                    "bob@example.test", "USER", "900.00");

            saveTransaction(transactions, alice.getId(), bob.getId(), "125.00", "Lunch repayment");
            saveTransaction(transactions, admin.getId(), alice.getId(), "500.00", "Demo payroll");
        };
    }

    private User createUser(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            String username,
            String password,
            String email,
            String role,
            String balance
    ) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole(role);
        user.setBalance(new BigDecimal(balance));
        return users.save(user);
    }

    private void saveTransaction(
            TransactionRepository transactions,
            Long fromUserId,
            Long toUserId,
            String amount,
            String note
    ) {
        BankTransaction transaction = new BankTransaction();
        transaction.setFromUserId(fromUserId);
        transaction.setToUserId(toUserId);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setNote(note);
        transactions.save(transaction);
    }
}
