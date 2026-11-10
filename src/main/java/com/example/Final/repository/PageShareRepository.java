package com.example.Final.repository;

import com.example.Final.entity.PageShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PageShareRepository extends JpaRepository<PageShare, Long> {

    Optional<PageShare> findByPageIdAndSharedWithUserId(Long pageId, Long sharedWithUserId);

    List<PageShare> findByPageId(Long pageId);

    void deleteByPageIdAndSharedWithUserId(Long pageId, Long sharedWithUserId);
}
