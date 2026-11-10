package com.example.Final.repository;

import com.example.Final.entity.BlockComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BlockCommentRepository extends JpaRepository<BlockComment, Long> {
    List<BlockComment> findByBlockIdOrderByCreatedAtAsc(Long blockId);
}
