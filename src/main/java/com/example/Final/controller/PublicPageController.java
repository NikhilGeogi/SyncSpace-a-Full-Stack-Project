package com.example.Final.controller;

import com.example.Final.entity.Block;
import com.example.Final.entity.Page;
import com.example.Final.repository.BlockRepository;
import com.example.Final.repository.PageRepository;
import com.example.Final.service.ActivityService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
public class PublicPageController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PageRepository pageRepository;
    private final BlockRepository blockRepository;
    private final ActivityService activityService;

    public PublicPageController(PageRepository pageRepository, BlockRepository blockRepository,
                                ActivityService activityService) {
        this.pageRepository = pageRepository;
        this.blockRepository = blockRepository;
        this.activityService = activityService;
    }

    @GetMapping("/api/pages/{id}/public-link")
    public ResponseEntity<?> getPublicLink(@PathVariable Long id, HttpSession session) {
        Page page = ownerPage(id, currentUserId(session));
        if (page == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new PublicLink(page.getPublicToken() != null, url(page.getPublicToken())));
    }

    @PostMapping("/api/pages/{id}/public-link")
    public ResponseEntity<?> createPublicLink(@PathVariable Long id, HttpSession session) {
        Page page = ownerPage(id, currentUserId(session));
        if (page == null) return ResponseEntity.notFound().build();
        if (page.getPublicToken() == null) {
            byte[] tokenBytes = new byte[32];
            SECURE_RANDOM.nextBytes(tokenBytes);
            page.setPublicToken(Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes));
            pageRepository.save(page);
            activityService.record(id, null, currentUserId(session), "public_link_enabled",
                    "Enabled a public read-only link");
        }
        return ResponseEntity.ok(new PublicLink(true, url(page.getPublicToken())));
    }

    @DeleteMapping("/api/pages/{id}/public-link")
    public ResponseEntity<?> revokePublicLink(@PathVariable Long id, HttpSession session) {
        Page page = ownerPage(id, currentUserId(session));
        if (page == null) return ResponseEntity.notFound().build();
        if (page.getPublicToken() != null) {
            page.setPublicToken(null);
            pageRepository.save(page);
            activityService.record(id, null, currentUserId(session), "public_link_revoked",
                    "Revoked the public read-only link");
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/public/pages/{token}")
    public ResponseEntity<?> readPublicPage(@PathVariable String token) {
        if (token.length() < 40 || token.length() > 64) return ResponseEntity.notFound().build();
        Page page = pageRepository.findByPublicTokenAndDeletedAtIsNull(token).orElse(null);
        if (page == null) return ResponseEntity.notFound().build();
        List<PublicBlock> blocks = blockRepository.findByPageId(page.getId()).stream()
                .map(block -> new PublicBlock(block.getBlockType(),
                        "page".equals(block.getBlockType()) ? "Linked page" : block.getContent(), block.getIsChecked(),
                        block.getTodoStatus(), block.getDueDate()))
                .toList();
        return ResponseEntity.ok(new PublicPage(page.getTitle(), blocks));
    }

    private Page ownerPage(Long pageId, Long userId) {
        return pageRepository.findById(pageId)
                .filter(page -> page.getUserId().equals(userId) && page.getDeletedAt() == null)
                .orElse(null);
    }

    private String url(String token) {
        if (token == null) return null;
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/public/{token}")
                .buildAndExpand(token)
                .toUriString();
    }

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(UserController.AUTHENTICATED_USER_ID);
    }

    public record PublicLink(boolean enabled, String url) {}
    public record PublicPage(String title, List<PublicBlock> blocks) {}
    public record PublicBlock(String blockType, String content, Boolean isChecked,
                              String todoStatus, LocalDate dueDate) {}
}
