package com.example.vulnbank.service;

import com.example.vulnbank.dto.TransferRequest;
import com.example.vulnbank.model.BankTransaction;
import com.example.vulnbank.model.User;
import com.example.vulnbank.repository.TransactionRepository;
import com.example.vulnbank.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.TreeSet;

@Service
public class TransferService {
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public TransferService(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public BankTransaction transfer(Long senderId, TransferRequest request) {
        if (senderId.equals(request.toUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot transfer to the same account");
        }

        var ids = new TreeSet<>(java.util.List.of(senderId, request.toUserId()));
        var lockedUsers = new java.util.HashMap<Long, User>();
        for (Long id : ids) {
            User user = userRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
            lockedUsers.put(id, user);
        }

        User sender = lockedUsers.get(senderId);
        User recipient = lockedUsers.get(request.toUserId());
        if (sender.getBalance().compareTo(request.amount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds");
        }

        sender.setBalance(sender.getBalance().subtract(request.amount()));
        recipient.setBalance(recipient.getBalance().add(request.amount()));
        userRepository.save(sender);
        userRepository.save(recipient);

        BankTransaction transaction = new BankTransaction();
        transaction.setFromUserId(senderId);
        transaction.setToUserId(recipient.getId());
        transaction.setAmount(request.amount());
        transaction.setNote(request.note());
        return transactionRepository.save(transaction);
    }
}
