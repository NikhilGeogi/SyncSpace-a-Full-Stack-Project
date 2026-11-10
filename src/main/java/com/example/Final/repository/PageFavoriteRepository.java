package com.example.Final.repository;

import com.example.Final.entity.PageFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PageFavoriteRepository extends JpaRepository<PageFavorite, Long> {

    Optional<PageFavorite> findByUserIdAndPageId(Long userId, Long pageId);

    List<PageFavorite> findByUserIdAndPageIdIn(Long userId, Collection<Long> pageIds);

    void deleteByUserIdAndPageId(Long userId, Long pageId);
}
