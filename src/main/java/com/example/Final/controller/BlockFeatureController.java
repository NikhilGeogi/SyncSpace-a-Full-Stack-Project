package com.example.Final.controller;

import com.example.Final.entity.Block;
import com.example.Final.entity.BlockComment;
import com.example.Final.entity.Page;
import com.example.Final.entity.User;
import com.example.Final.repository.BlockCommentRepository;
import com.example.Final.repository.BlockRepository;
import com.example.Final.repository.UserRepository;
import com.example.Final.service.ActivityService;
import com.example.Final.service.PageAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
public class BlockFeatureController {

    private final BlockRepository blockRepository;
    private final BlockCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PageAccessService pageAccessService;
    private final ActivityService activityService;

    public BlockFeatureController(BlockRepository blockRepository, BlockCommentRepository commentRepository,
                                  UserRepository userRepository, PageAccessService pageAccessService,
                                  ActivityService activityService) {
        this.blockRepository = blockRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.pageAccessService = pageAccessService;
        this.activityService = activityService;
    }

    @PutMapping("/api/blocks/{id}/due-date")
    @Transactional
    public ResponseEntity<?> updateDueDate(@PathVariable Long id, @RequestBody DueDateRequest request,
                                             HttpSession session) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null || !editable(block, currentUserId(session))) return ResponseEntity.notFound().build();
        if (!"todo".equals(block.getBlockType())) {
            return ResponseEntity.badRequest().body("Due dates can only be set on to-do blocks");
        }
        LocalDate dueDate;
        try {
            dueDate = request.dueDate() == null || request.dueDate().isBlank()
                    ? null : LocalDate.parse(request.dueDate());
        } catch (DateTimeParseException exception) {
            return ResponseEntity.badRequest().body("dueDate must use YYYY-MM-DD or be null");
        }
        block.setDueDate(dueDate);
        block.setOverdueFlag(dueDate != null && dueDate.isBefore(LocalDate.now())
                && !"done".equals(block.getTodoStatus()));
        blockRepository.save(block);
        activityService.record(block.getPageId(), id, currentUserId(session), "due_date",
                dueDate == null ? "Removed a to-do due date" : "Set a to-do due date to " + dueDate);
        return ResponseEntity.ok(new DueDateResponse(dueDate, block.getOverdueFlag()));
    }

    @PutMapping("/api/blocks/{id}/todo-status")
    @Transactional
    public ResponseEntity<?> updateTodoStatus(@PathVariable Long id, @RequestBody StatusRequest request,
                                               HttpSession session) {
        Block block = blockRepository.findById(id).orElse(null);
        if (block == null || !editable(block, currentUserId(session))) return ResponseEntity.notFound().build();
        if (!"todo".equals(block.getBlockType())) {
            return ResponseEntity.badRequest().body("Only to-do blocks have a board status");
        }
        if (request.status() == null || !List.of("todo", "doing", "done").contains(request.status())) {
            return ResponseEntity.badRequest().body("status must be todo, doing, or done");
        }
        block.setTodoStatus(request.status());
        block.setIsChecked("done".equals(request.status()));
        block.setOverdueFlag(block.getDueDate() != null && block.getDueDate().isBefore(LocalDate.now())
                && !"done".equals(request.status()));
        blockRepository.save(block);
        activityService.record(block.getPageId(), id, currentUserId(session), "todo_status",
                "Moved a to-do to " + request.status());
        return ResponseEntity.ok(new StatusResponse(block.getTodoStatus(), block.getIsChecked()));
    }

    @GetMapping("/api/blocks/{blockId}/comments")
    public ResponseEntity<?> getComments(@PathVariable Long blockId, HttpSession session) {
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block == null || pageAccessService.readablePage(block.getPageId(), currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(commentRepository.findByBlockIdOrderByCreatedAtAsc(blockId).stream()
                .map(this::commentSummary).toList());
    }

    @PostMapping("/api/blocks/{blockId}/comments")
    @Transactional
    public ResponseEntity<?> addComment(@PathVariable Long blockId, @RequestBody CommentRequest request,
                                        HttpSession session) {
        Long userId = currentUserId(session);
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block == null || pageAccessService.readablePage(block.getPageId(), userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (request.content() == null || request.content().isBlank() || request.content().trim().length() > 4000) {
            return ResponseEntity.badRequest().body("Comment must be between 1 and 4000 characters");
        }
        BlockComment comment = new BlockComment();
        comment.setBlockId(blockId);
        comment.setUserId(userId);
        comment.setContent(request.content().trim());
        BlockComment saved = commentRepository.saveAndFlush(comment);
        activityService.record(block.getPageId(), blockId, userId, "comment_added", "Added a block comment");
        return ResponseEntity.ok(commentSummary(saved));
    }

    @DeleteMapping("/api/comments/{commentId}")
    @Transactional
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, HttpSession session) {
        Long userId = currentUserId(session);
        BlockComment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) return ResponseEntity.notFound().build();
        Block block = blockRepository.findById(comment.getBlockId()).orElse(null);
        if (block == null) return ResponseEntity.notFound().build();
        Page page = pageAccessService.readablePage(block.getPageId(), userId).orElse(null);
        if (page == null || (!comment.getUserId().equals(userId)
                && !page.getUserId().equals(userId))) {
            return ResponseEntity.notFound().build();
        }
        commentRepository.delete(comment);
        activityService.record(block.getPageId(), block.getId(), userId, "comment_deleted", "Deleted a block comment");
        return ResponseEntity.ok().build();
    }

    private boolean editable(Block block, Long userId) {
        return pageAccessService.editablePage(block.getPageId(), userId).isPresent();
    }

    private CommentSummary commentSummary(BlockComment comment) {
        User user = userRepository.findById(comment.getUserId())
                .orElseThrow(() -> new IllegalStateException("Comment author no longer exists"));
        return new CommentSummary(comment.getId(), comment.getBlockId(), comment.getUserId(),
                user.getUsername(), comment.getContent(), comment.getCreatedAt());
    }

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(UserController.AUTHENTICATED_USER_ID);
    }

    public record DueDateRequest(String dueDate) {}
    public record DueDateResponse(LocalDate dueDate, boolean overdue) {}
    public record StatusRequest(String status) {}
    public record StatusResponse(String status, boolean isChecked) {}
    public record CommentRequest(String content) {}
    public record CommentSummary(Long id, Long blockId, Long userId, String username, String content,
                                 java.time.LocalDateTime createdAt) {}
}
