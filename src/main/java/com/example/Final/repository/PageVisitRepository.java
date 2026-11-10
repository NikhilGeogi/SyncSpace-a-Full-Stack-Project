package com.example.Final.repository;

import com.example.Final.entity.PageVisit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PageVisitRepository extends JpaRepository<PageVisit, Long> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO page_views (user_id, page_id, last_viewed_at)
            VALUES (:userId, :pageId, CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE last_viewed_at = CURRENT_TIMESTAMP(6)
            """, nativeQuery = true)
    int recordView(@Param("userId") Long userId, @Param("pageId") Long pageId);

    @Query("""
            SELECT v FROM PageVisit v
            JOIN FETCH v.page p
            WHERE v.userId = :userId AND p.deletedAt IS NULL
                AND (p.userId = :userId OR EXISTS (
                    SELECT s.id FROM PageShare s
                    WHERE s.pageId = p.id AND s.sharedWithUserId = :userId
                ))
            ORDER BY v.lastViewedAt DESC
            """)
    List<PageVisit> findRecentAccessiblePages(@Param("userId") Long userId, Pageable pageable);
}
