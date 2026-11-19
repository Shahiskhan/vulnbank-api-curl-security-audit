package com.example.vulnbank.repository;

import com.example.vulnbank.model.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<BankTransaction, Long> {
    @Query("""
            select t from BankTransaction t
            where (t.fromUserId = :userId or t.toUserId = :userId)
              and lower(t.note) like lower(concat('%', :note, '%'))
            order by t.id desc
            """)
    List<BankTransaction> searchByNoteForUser(@Param("note") String note, @Param("userId") Long userId);

    @Query("""
            select t from BankTransaction t
            where t.fromUserId = :userId or t.toUserId = :userId
            order by t.id desc
            """)
    List<BankTransaction> findHistoryForUser(@Param("userId") Long userId);
}
