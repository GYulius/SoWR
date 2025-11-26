package com.cruise.recommender.repository;

import com.cruise.recommender.entity.EmailVerificationToken;
import com.cruise.recommender.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for EmailVerificationToken entity operations
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    
    Optional<EmailVerificationToken> findByToken(String token);
    
    Optional<EmailVerificationToken> findByUser(User user);
    
    void deleteByUser(User user);
}

