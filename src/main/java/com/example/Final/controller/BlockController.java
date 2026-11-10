package com.example.Final.controller;

import com.example.Final.entity.Block;
import com.example.Final.repository.BlockRepository;
import com.example.Final.repository.PageVisitRepository;
import com.example.Final.service.ActivityService;
import com.example.Final.service.PageAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author aicpq
 */
@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockRepository blockRepository;
    private final PageAccessService pageAccessService;
    private final PageVisitRepository pageVisitRepository;
    private final ActivityService activityService;

    public BlockController(BlockRepository blockRepository, PageAccessService pageAccessService,
                           PageVisitRepository pageVisitRepository, ActivityService activityService) {
        this.blockRepository = blockRepository;
        this.pageAccessService = pageAccessService;
        this.pageVisitRepository = pageVisitRepository;
        this.activityService = activityService;
    }

    // POST http://localhost:8080/api/blocks
    @PostMapping
    public ResponseEntity<String> createBlock(@RequestBody Block block, HttpSession session) {
        // page_id 是 NOT NULL，少了這道檢查會以 500 收場而不是 400
        if (block.getPageId() == null) {
            return ResponseEntity.badRequest().body("pageId is required");
        }
        if (pageAccessService.editablePage(block.getPageId(), currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        block.setId(null);
        if ("todo".equals(block.getBlockType())) {
            block.setTodoStatus(Boolean.TRUE.equals(block.getIsChecked()) ? "done" : "todo");
        } else {
            block.setDueDate(null);
            block.setTodoStatus("todo");
            block.setOverdueFlag(false);
        }
        blockRepository.saveAndFlush(block);
        activityService.record(block.getPageId(), block.getId(), currentUserId(session), "block_created",
                "Created a " + block.getBlockType() + " block");
        return ResponseEntity.ok("Block created successfully");
    }

    // GET http://localhost:8080/api/blocks/page/{pageId}
    @GetMapping("/page/{pageId}")
    public ResponseEntity<?> getBlocksByPage(@PathVariable Long pageId, HttpSession session) {
        Long userId = currentUserId(session);
        if (pageAccessService.readablePage(pageId, userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        pageVisitRepository.recordView(userId, pageId);
        List<Block> blocks = blockRepository.findByPageId(pageId);
        return ResponseEntity.ok(blocks);
    }

    // PUT http://localhost:8080/api/blocks/{id}
    @PutMapping("/{id}")
    public ResponseEntity<String> updateBlock(@PathVariable Long id, @RequestBody Map<String, String> body,
                                               HttpSession session) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null || pageAccessService.editablePage(block.getPageId(), currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        int result = blockRepository.updateContent(id, body.get("content"));
        if (result > 0) activityService.record(block.getPageId(), id, currentUserId(session),
                "block_edited", "Edited a block");
        if (result > 0) return ResponseEntity.ok("Block updated successfully");
        return ResponseEntity.status(404).body("Block not found or update failed");
    }

    // DELETE http://localhost:8080/api/blocks/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBlock(@PathVariable Long id, HttpSession session) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null || pageAccessService.editablePage(block.getPageId(), currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        activityService.record(block.getPageId(), id, currentUserId(session), "block_deleted", "Deleted a block");
        int result = blockRepository.delete(id);
        if (result > 0) return ResponseEntity.ok("Block deleted successfully");
        return ResponseEntity.status(404).body("Block not found or delete failed");
    }

    // PUT http://localhost:8080/api/blocks/page/{pageId}/reorder
    @PutMapping("/page/{pageId}/reorder")
    public ResponseEntity<String> reorderBlocks(@PathVariable Long pageId, @RequestBody List<Long> blockIds,
                                                 HttpSession session) {
        if (pageAccessService.editablePage(pageId, currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Set<Long> actualBlockIds = blockRepository.findByPageId(pageId).stream()
                .map(Block::getId)
                .collect(Collectors.toSet());
        if (blockIds == null || blockIds.contains(null) || blockIds.size() != actualBlockIds.size()
                || !actualBlockIds.equals(Set.copyOf(blockIds))) {
            return ResponseEntity.badRequest().body("Block order must include every block on the page exactly once");
        }
        blockRepository.updateSortOrders(blockIds);
        activityService.record(pageId, null, currentUserId(session), "blocks_reordered",
                "Reordered page blocks");
        return ResponseEntity.ok("Sort order updated successfully");
    }

    // PUT http://localhost:8080/api/blocks/{id}/type
    @PutMapping("/{id}/type")
    public ResponseEntity<String> updateBlockType(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                   HttpSession session) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null || pageAccessService.editablePage(block.getPageId(), currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String type = body.get("type");
        if (type == null || !List.of("p", "h1", "todo", "page").contains(type)) {
            return ResponseEntity.badRequest().body("A supported block type is required");
        }
        block.setBlockType(type);
        if (!"todo".equals(type)) {
            block.setDueDate(null);
            block.setTodoStatus("todo");
            block.setOverdueFlag(false);
            block.setIsChecked(false);
        } else {
            block.setTodoStatus(Boolean.TRUE.equals(block.getIsChecked()) ? "done" : "todo");
        }
        blockRepository.save(block);
        int result = 1;
        activityService.record(block.getPageId(), id, currentUserId(session), "block_type_changed",
                "Changed a block type to " + type);
        if (result > 0) return ResponseEntity.ok("Block type updated successfully");
        return ResponseEntity.status(404).body("Block not found or update failed");
    }

    // PUT http://localhost:8080/api/blocks/{id}/check
    @PutMapping("/{id}/check")
    public ResponseEntity<String> updateBlockCheckStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body,
                                                          HttpSession session) {
        // is_checked 是 NOT NULL，這裡是 JPQL UPDATE，繞過 Entity 的預設值，
        // 少了這道檢查會直接把 null 寫進資料庫而失敗。
        Boolean isChecked = body.get("isChecked");
        if (isChecked == null) return ResponseEntity.badRequest().body("isChecked is required");
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null || pageAccessService.editablePage(block.getPageId(), currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        block.setIsChecked(isChecked);
        block.setTodoStatus(isChecked ? "done" : "todo");
        block.setOverdueFlag(!isChecked && block.getDueDate() != null
                && block.getDueDate().isBefore(java.time.LocalDate.now()));
        blockRepository.save(block);
        int result = 1;
        activityService.record(block.getPageId(), id, currentUserId(session), "todo_completed",
                isChecked ? "Completed a to-do" : "Reopened a to-do");
        if (result > 0) return ResponseEntity.ok("Check status updated successfully");
        return ResponseEntity.status(404).body("Update failed");
    }

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(UserController.AUTHENTICATED_USER_ID);
    }
}
