package com.example.Final.repository;

import com.example.Final.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 *
 * @author aicpq
 */
public interface BlockRepository extends JpaRepository<Block, Long>, BlockRepositoryJdbc {

    List<Block> findByPageIdOrderBySortOrderAsc(Long pageId);

    @Modifying
    @Transactional
    @Query("UPDATE Block b SET b.content = :content WHERE b.id = :id")
    int updateContent(@Param("id") Long id, @Param("content") String content);

    @Modifying
    @Transactional
    @Query("UPDATE Block b SET b.blockType = :blockType WHERE b.id = :id")
    int updateType(@Param("id") Long id, @Param("blockType") String blockType);

    @Modifying
    @Transactional
    @Query("UPDATE Block b SET b.isChecked = :isChecked WHERE b.id = :id")
    int updateIsChecked(@Param("id") Long id, @Param("isChecked") Boolean isChecked);

    @Query("""
            SELECT b FROM Block b JOIN Page p ON p.id = b.pageId
            WHERE b.blockType = 'todo' AND p.deletedAt IS NULL
                AND (p.userId = :userId OR EXISTS (
                    SELECT s.id FROM PageShare s
                    WHERE s.pageId = p.id AND s.sharedWithUserId = :userId
                ))
            ORDER BY CASE WHEN b.dueDate IS NULL THEN 1 ELSE 0 END, b.dueDate, b.sortOrder
            """)
    List<Block> findAccessibleTodos(@Param("userId") Long userId);

    @Query("""
            SELECT b FROM Block b JOIN Page p ON p.id = b.pageId
            WHERE b.blockType = 'todo' AND b.todoStatus <> 'done' AND b.dueDate <= :today
                AND p.deletedAt IS NULL
                AND (p.userId = :userId OR EXISTS (
                    SELECT s.id FROM PageShare s
                    WHERE s.pageId = p.id AND s.sharedWithUserId = :userId
                ))
            ORDER BY b.dueDate, b.sortOrder
            """)
    List<Block> findAccessibleDueTodos(@Param("userId") Long userId, @Param("today") LocalDate today);

    List<Block> findByPageIdIn(List<Long> pageIds);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE blocks SET overdue_flag =
                CASE WHEN block_type = 'todo' AND todo_status <> 'done'
                    AND due_date < CURRENT_DATE THEN TRUE ELSE FALSE END
            WHERE due_date IS NOT NULL OR overdue_flag = TRUE
            """, nativeQuery = true)
    int refreshOverdueFlags();

    default List<Block> findByPageId(Long pageId) {
        return findByPageIdOrderBySortOrderAsc(pageId);
    }

    @Transactional
    default int delete(Long id) {
        if (!existsById(id)) return 0;
        deleteById(id);
        return 1;
    }
}
