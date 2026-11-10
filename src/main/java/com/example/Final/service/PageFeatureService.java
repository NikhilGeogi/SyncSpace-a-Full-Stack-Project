package com.example.Final.service;

import com.example.Final.entity.Page;
import com.example.Final.repository.PageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Arrays;

@Service
public class PageFeatureService {

    private final JdbcTemplate jdbcTemplate;
    private final PageRepository pageRepository;

    public PageFeatureService(JdbcTemplate jdbcTemplate, PageRepository pageRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.pageRepository = pageRepository;
    }

    public List<SearchResult> search(Long userId, String query) {
        String booleanQuery = Arrays.stream(query.trim().split("\\s+"))
                .map(term -> term.replaceAll("[^\\p{L}\\p{N}_]", ""))
                .filter(term -> !term.isEmpty())
                .map(term -> "+" + term + "*")
                .collect(Collectors.joining(" "));
        if (booleanQuery.isEmpty()) return List.of();

        return jdbcTemplate.query("""
                SELECT DISTINCT p.id, p.title, p.user_id, p.parent_page_id,
                    CASE WHEN p.user_id = ? THEN 'OWNER' ELSE s.permission END AS permission
                FROM pages p
                LEFT JOIN blocks b ON b.page_id = p.id
                LEFT JOIN page_shares s ON s.page_id = p.id AND s.shared_with_user_id = ?
                WHERE p.deleted_at IS NULL
                    AND (p.user_id = ? OR s.id IS NOT NULL)
                    AND (MATCH(p.title) AGAINST (? IN BOOLEAN MODE)
                        OR MATCH(b.content) AGAINST (? IN BOOLEAN MODE))
                ORDER BY p.title
                LIMIT 50
                """, this::mapSearchResult, userId, userId, userId, booleanQuery, booleanQuery);
    }

    @Transactional
    public boolean trashSubtree(Long pageId, Long ownerId) {
        List<Long> pageIds = jdbcTemplate.queryForList("""
                WITH RECURSIVE page_tree (id) AS (
                    SELECT id FROM pages WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT child.id
                    FROM pages child
                    JOIN page_tree parent ON child.parent_page_id = parent.id
                    WHERE child.deleted_at IS NULL
                )
                SELECT id FROM page_tree
                """, Long.class, pageId, ownerId);
        if (pageIds.isEmpty()) return false;

        String placeholders = String.join(",", java.util.Collections.nCopies(pageIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(pageId);
        parameters.addAll(pageIds);
        jdbcTemplate.update("UPDATE pages SET deleted_at = CURRENT_TIMESTAMP(6), trash_root_id = ? WHERE id IN ("
                + placeholders + ")", parameters.toArray());
        return true;
    }

    @Transactional
    public boolean restoreSubtree(Long rootPageId, Long ownerId) {
        Optional<Page> root = pageRepository.findById(rootPageId);
        if (root.isEmpty() || !root.get().getUserId().equals(ownerId)
                || !rootPageId.equals(root.get().getTrashRootId())) {
            return false;
        }
        return jdbcTemplate.update(
                "UPDATE pages SET deleted_at = NULL, trash_root_id = NULL WHERE trash_root_id = ?",
                rootPageId) > 0;
    }

    private SearchResult mapSearchResult(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SearchResult(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getLong("user_id"),
                resultSet.getObject("parent_page_id", Long.class),
                resultSet.getString("permission"));
    }

    public record SearchResult(Long id, String title, Long userId, Long parentPageId, String permission) {}
}
