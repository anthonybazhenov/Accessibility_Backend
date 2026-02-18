package com.husky.spring_portfolio.mvc.chatLLM;
import org.springframework.data.jpa.repository.JpaRepository;

public interface chatDocRepository extends JpaRepository<chatDoc, Long> {
    // Repository for PDF accessibility documents
}

