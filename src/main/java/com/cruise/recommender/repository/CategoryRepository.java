package com.cruise.recommender.repository;

import com.cruise.recommender.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Category operations
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}

