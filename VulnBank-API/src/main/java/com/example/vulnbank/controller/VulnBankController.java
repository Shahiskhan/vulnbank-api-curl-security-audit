package com.example.vulnbank.controller;

import com.example.vulnbank.dto.AuthResponse;
import com.example.vulnbank.dto.LoginRequest;
import com.example.vulnbank.dto.RegisterRequest;
import com.example.vulnbank.dto.TransactionResponse;
import com.example.vulnbank.dto.TransferRequest;
import com.example.vulnbank.dto.UserResponse;
import com.example.vulnbank.model.BankTransaction;
import com.example.vulnbank.model.User;
import com.example.vulnbank.repository.TransactionRepository;
import com.example.vulnbank.repository.UserRepository;
import com.example.vulnbank.security.BankPrincipal;
import com.example.vulnbank.security.TokenStore;
import com.example.vulnbank.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
public class VulnBankController {
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransferService transferService;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public VulnBankController(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            TransferService transferService,
            PasswordEncoder passwordEncoder,
            TokenStore tokenStore
    ) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.transferService = transferService;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username()) || userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already registered");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setRole("USER");
        user.setBalance(BigDecimal.ZERO);
        return ResponseEntity.status(HttpStatus.CREATED).body(toUserResponse(userRepository.save(user)));
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return new AuthResponse(tokenStore.issue(user.getId()), toUserResponse(user));
    }

    @GetMapping("/api/users/{id}/profile")
    public UserResponse profile(@PathVariable Long id, @AuthenticationPrincipal BankPrincipal principal) {
        verifyOwnerOrAdmin(principal, id);
        return toUserResponse(findUser(id));
    }

    @GetMapping("/api/transactions/search")
    public List<TransactionResponse> search(
            @RequestParam String note,
            @AuthenticationPrincipal BankPrincipal principal
    ) {
        if (note.isBlank() || note.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "note must contain 1 to 120 characters");
        }
        return transactionRepository.searchByNoteForUser(note, principal.userId()).stream()
                .map(this::toTransactionResponse).toList();
    }

    @PostMapping("/api/transactions/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal BankPrincipal principal
    ) {
        BankTransaction transaction = transferService.transfer(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTransactionResponse(transaction));
    }

    @GetMapping("/api/transactions/history/{userId}")
    public List<TransactionResponse> history(
            @PathVariable Long userId,
            @AuthenticationPrincipal BankPrincipal principal
    ) {
        verifyOwnerOrAdmin(principal, userId);
        return transactionRepository.findHistoryForUser(userId).stream().map(this::toTransactionResponse).toList();
    }

    @DeleteMapping("/api/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    private void verifyOwnerOrAdmin(BankPrincipal principal, Long requestedUserId) {
        User requester = findUser(principal.userId());
        if (!requester.getRole().equals("ADMIN") && !principal.userId().equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access another user's account");
        }
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getBalance());
    }

    private TransactionResponse toTransactionResponse(BankTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getFromUserId(),
                transaction.getToUserId(), transaction.getAmount(), transaction.getNote());
    }
}
