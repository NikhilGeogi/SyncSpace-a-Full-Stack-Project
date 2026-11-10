package com.example.Final.controller;

import com.example.Final.entity.Block;
import com.example.Final.entity.Page;
import com.example.Final.repository.BlockRepository;
import com.example.Final.repository.PageRepository;
import com.example.Final.service.PageAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final BlockRepository blockRepository;
    private final PageRepository pageRepository;
    private final PageAccessService pageAccessService;

    public TodoController(BlockRepository blockRepository, PageRepository pageRepository,
                          PageAccessService pageAccessService) {
        this.blockRepository = blockRepository;
        this.pageRepository = pageRepository;
        this.pageAccessService = pageAccessService;
    }

    @GetMapping
    public ResponseEntity<?> getDueTodos(@RequestParam String filter, HttpSession session) {
        if (!List.of("today", "overdue").contains(filter)) {
            return ResponseEntity.badRequest().body("filter must be today or overdue");
        }
        LocalDate today = LocalDate.now();
        List<TodoSummary> todos = blockRepository.findAccessibleDueTodos(currentUserId(session), today).stream()
                .filter(block -> filter.equals("today") ? today.equals(block.getDueDate())
                        : block.getDueDate().isBefore(today) && Boolean.TRUE.equals(block.getOverdueFlag()))
                .map(block -> summary(block, currentUserId(session)))
                .toList();
        return ResponseEntity.ok(todos);
    }

    @GetMapping("/board")
    public List<TodoSummary> getBoard(HttpSession session) {
        Long userId = currentUserId(session);
        return blockRepository.findAccessibleTodos(userId).stream()
                .map(block -> summary(block, userId))
                .toList();
    }

    private TodoSummary summary(Block block, Long userId) {
        Page page = pageRepository.findById(block.getPageId())
                .orElseThrow(() -> new IllegalStateException("To-do page no longer exists"));
        return new TodoSummary(block.getId(), block.getPageId(), page.getTitle(), block.getContent(),
                block.getTodoStatus(), block.getDueDate(), Boolean.TRUE.equals(block.getOverdueFlag()),
                pageAccessService.editablePage(page.getId(), userId).isPresent());
    }

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(UserController.AUTHENTICATED_USER_ID);
    }

    public record TodoSummary(Long id, Long pageId, String pageTitle, String content, String status,
                              LocalDate dueDate, boolean overdue, boolean canEdit) {}
}
