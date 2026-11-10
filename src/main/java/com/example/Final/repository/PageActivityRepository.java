package com.example.Final.repository;

import com.example.Final.entity.PageActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PageActivityRepository extends JpaRepository<PageActivity, Long> {
    List<PageActivity> findTop100ByPageIdOrderByCreatedAtDesc(Long pageId);
}
