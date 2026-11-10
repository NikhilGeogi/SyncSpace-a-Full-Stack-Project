package com.example.Final.repository;

import com.example.Final.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNameIgnoreCase(String name);

    @Query("SELECT p.id, t.id, t.name FROM Page p JOIN p.tags t WHERE p.id IN :pageIds")
    List<Object[]> findTagsForPages(@Param("pageIds") List<Long> pageIds);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE t FROM tags t
            LEFT JOIN page_tag_links link ON link.tag_id = t.id
            WHERE link.tag_id IS NULL
            """, nativeQuery = true)
    int deleteUnusedTags();
}
