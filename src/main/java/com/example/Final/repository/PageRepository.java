package com.example.Final.repository;

import com.example.Final.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 *
 * @author aicpq
 */
public interface PageRepository extends JpaRepository<Page, Long> {

    @Query("SELECT p FROM Page p WHERE p.userId = :userId AND p.parentPageId IS NULL AND p.deletedAt IS NULL")
    List<Page> findTopLevelPagesByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT p FROM Page p
            WHERE p.deletedAt IS NULL AND
                (p.userId = :userId OR EXISTS (
                    SELECT s.id FROM PageShare s
                    WHERE s.pageId = p.id AND s.sharedWithUserId = :userId
                ))
            """)
    List<Page> findAllPagesByUserId(@Param("userId") Long userId);

    java.util.Optional<Page> findByPublicTokenAndDeletedAtIsNull(String publicToken);

    @Query(value = """
            SELECT DISTINCT p.*
            FROM blocks b
            JOIN pages p ON p.id = b.page_id
            LEFT JOIN page_shares s ON s.page_id = p.id AND s.shared_with_user_id = :userId
            WHERE b.block_type = 'page'
                AND BINARY b.content = BINARY CAST(:targetPageId AS CHAR)
                AND p.deleted_at IS NULL
                AND (p.user_id = :userId OR s.id IS NOT NULL)
            ORDER BY p.title
            """, nativeQuery = true)
    List<Page> findAccessibleBacklinks(@Param("targetPageId") Long targetPageId, @Param("userId") Long userId);

    @Query("SELECT p FROM Page p WHERE p.userId = :userId AND p.id = p.trashRootId ORDER BY p.deletedAt DESC")
    List<Page> findTrashRootsByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Page p SET p.title = :title WHERE p.id = :id")
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    default Long savePage(Page page) {
        Page saved = saveAndFlush(page);
        return saved.getId();
    }
}
