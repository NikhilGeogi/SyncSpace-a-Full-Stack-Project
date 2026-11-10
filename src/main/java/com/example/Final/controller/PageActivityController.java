package com.example.Final.controller;

import com.example.Final.entity.PageActivity;
import com.example.Final.entity.User;
import com.example.Final.repository.PageActivityRepository;
import com.example.Final.repository.UserRepository;
import com.example.Final.service.PageAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pages")
public class PageActivityController {

    private final PageActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final PageAccessService pageAccessService;

    public PageActivityController(PageActivityRepository activityRepository, UserRepository userRepository,
                                  PageAccessService pageAccessService) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.pageAccessService = pageAccessService;
    }

    @GetMapping("/{pageId}/activity")
    public ResponseEntity<?> getActivity(@PathVariable Long pageId, HttpSession session) {
        if (pageAccessService.readablePage(pageId, currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(activityRepository.findTop100ByPageIdOrderByCreatedAtDesc(pageId).stream()
                .map(this::summary).toList());
    }

    private ActivitySummary summary(PageActivity activity) {
        User user = userRepository.findById(activity.getUserId())
                .orElseThrow(() -> new IllegalStateException("Activity author no longer exists"));
        return new ActivitySummary(activity.getId(), activity.getBlockId(), activity.getAction(),
                activity.getDetails(), activity.getCreatedAt(), user.getUsername());
    }

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(UserController.AUTHENTICATED_USER_ID);
    }

    public record ActivitySummary(Long id, Long blockId, String action, String details,
                                  java.time.LocalDateTime createdAt, String username) {}
}
