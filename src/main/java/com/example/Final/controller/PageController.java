package com.example.Final.controller;

import com.example.Final.entity.Page;
import com.example.Final.entity.PageFavorite;
import com.example.Final.entity.PageShare;
import com.example.Final.entity.PageVisit;
import com.example.Final.entity.Tag;
import com.example.Final.entity.User;
import com.example.Final.repository.PageFavoriteRepository;
import com.example.Final.repository.PageRepository;
import com.example.Final.repository.PageShareRepository;
import com.example.Final.repository.PageVisitRepository;
import com.example.Final.repository.TagRepository;
import com.example.Final.repository.UserRepository;
import com.example.Final.service.PageAccessService;
import com.example.Final.service.PageFeatureService;
import com.example.Final.service.PageTemplateService;
import com.example.Final.service.ActivityService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageRepository pageRepository;
    private final PageShareRepository pageShareRepository;
    private final UserRepository userRepository;
    private final PageAccessService pageAccessService;
    private final PageFeatureService pageFeatureService;
    private final TagRepository tagRepository;
    private final PageFavoriteRepository pageFavoriteRepository;
    private final PageVisitRepository pageVisitRepository;
    private final PageTemplateService pageTemplateService;
    private final ActivityService activityService;

    public PageController(PageRepository pageRepository, PageShareRepository pageShareRepository,
                          UserRepository userRepository, PageAccessService pageAccessService,
                          PageFeatureService pageFeatureService, TagRepository tagRepository,
                          PageFavoriteRepository pageFavoriteRepository, PageVisitRepository pageVisitRepository,
                          PageTemplateService pageTemplateService, ActivityService activityService) {
        this.pageRepository = pageRepository;
        this.pageShareRepository = pageShareRepository;
        this.userRepository = userRepository;
        this.pageAccessService = pageAccessService;
        this.pageFeatureService = pageFeatureService;
        this.tagRepository = tagRepository;
        this.pageFavoriteRepository = pageFavoriteRepository;
        this.pageVisitRepository = pageVisitRepository;
        this.pageTemplateService = pageTemplateService;
        this.activityService = activityService;
    }

    @PostMapping
    public ResponseEntity<?> createPage(@RequestBody Page page, HttpSession session) {
        if (page.getTitle() == null || page.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body("title is required");
        }
        Long userId = currentUserId(session);
        if (page.getParentPageId() != null
                && pageAccessService.editablePage(page.getParentPageId(), userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        page.setId(null);
        page.setUserId(userId);
        page.setDeletedAt(null);
        page.setTrashRootId(null);
        Page saved = pageRepository.saveAndFlush(page);
        activityService.record(saved.getId(), null, userId, "page_created", "Created the page");
        return ResponseEntity.ok(Map.of("id", saved.getId(), "message", "Page created successfully"));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getTopPages(@PathVariable Long userId, HttpSession session) {
        if (!userId.equals(currentUserId(session))) return ResponseEntity.status(403).body("Forbidden");
        return ResponseEntity.ok(pageRepository.findTopLevelPagesByUserId(userId));
    }

    @GetMapping("/user/{userId}/all")
    public ResponseEntity<?> getAllPages(@PathVariable Long userId, HttpSession session) {
        if (!userId.equals(currentUserId(session))) return ResponseEntity.status(403).body("Forbidden");
        return ResponseEntity.ok(pageRepository.findAllPagesByUserId(userId));
    }

    @GetMapping("/mine/all")
    public ResponseEntity<List<Page>> getAccessiblePages(HttpSession session) {
        Long userId = currentUserId(session);
        List<Page> pages = pageRepository.findAllPagesByUserId(userId);
        addPageMetadata(pages, userId);
        return ResponseEntity.ok(pages);
    }

    @GetMapping("/mine/recent")
    public ResponseEntity<List<RecentPage>> getRecentlyViewedPages(HttpSession session) {
        Long userId = currentUserId(session);
        List<RecentPage> pages = pageVisitRepository.findRecentAccessiblePages(userId, PageRequest.of(0, 10))
                .stream()
                .map(visit -> new RecentPage(visit.getPage().getId(), visit.getPage().getTitle(),
                        pageAccessService.permission(visit.getPage(), userId), visit.getLastViewedAt()))
                .toList();
        return ResponseEntity.ok(pages);
    }

    @GetMapping("/templates")
    public ResponseEntity<List<PageTemplateService.TemplateInfo>> getTemplates() {
        return ResponseEntity.ok(pageTemplateService.listTemplates());
    }

    @PostMapping("/from-template")
    @Transactional
    public ResponseEntity<?> createPageFromTemplate(@RequestBody TemplateRequest request, HttpSession session) {
        if (request.templateKey() == null || !pageTemplateService.contains(request.templateKey())) {
            return ResponseEntity.badRequest().body("A valid template is required");
        }
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isBlank()) return ResponseEntity.badRequest().body("title is required");
        Long userId = currentUserId(session);
        if (request.parentPageId() != null
                && pageAccessService.editablePage(request.parentPageId(), userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Page page = new Page();
        page.setTitle(title);
        page.setUserId(userId);
        page.setParentPageId(request.parentPageId());
        Page saved = pageRepository.saveAndFlush(page);
        pageTemplateService.addTemplateBlocks(saved.getId(), request.templateKey());
        activityService.record(saved.getId(), null, userId, "page_created",
                "Created the page from the " + request.templateKey() + " template");
        return ResponseEntity.ok(Map.of("id", saved.getId(), "message", "Page created from template"));
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagSummary>> getAvailableTags(HttpSession session) {
        Long userId = currentUserId(session);
        List<Page> pages = pageRepository.findAllPagesByUserId(userId);
        addPageMetadata(pages, userId);
        Map<String, Long> counts = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        pages.forEach(page -> page.getTagNames().forEach(name -> counts.merge(name, 1L, Long::sum)));
        return ResponseEntity.ok(counts.entrySet().stream()
                .map(entry -> new TagSummary(null, entry.getKey(), entry.getValue()))
                .toList());
    }

    @PostMapping("/{id}/favorite")
    @Transactional
    public ResponseEntity<String> favoritePage(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        if (pageAccessService.readablePage(id, userId).isEmpty()) return ResponseEntity.notFound().build();
        if (pageFavoriteRepository.findByUserIdAndPageId(userId, id).isEmpty()) {
            PageFavorite favorite = new PageFavorite();
            favorite.setUserId(userId);
            favorite.setPageId(id);
            pageFavoriteRepository.save(favorite);
        }
        return ResponseEntity.ok("Page added to favorites");
    }

    @DeleteMapping("/{id}/favorite")
    @Transactional
    public ResponseEntity<String> unfavoritePage(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        pageFavoriteRepository.deleteByUserIdAndPageId(userId, id);
        return ResponseEntity.ok("Page removed from favorites");
    }

    @GetMapping("/{id}/tags")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPageTags(@PathVariable Long id, HttpSession session) {
        if (pageAccessService.readablePage(id, currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tagRepository.findTagsForPages(List.of(id)).stream()
                .map(row -> new TagSummary((Long) row[1], (String) row[2], null))
                .toList());
    }

    @PostMapping("/{id}/tags")
    @Transactional
    public ResponseEntity<?> addPageTag(@PathVariable Long id, @RequestBody Map<String, String> body,
                                        HttpSession session) {
        Long userId = currentUserId(session);
        Optional<Page> page = pageAccessService.editablePage(id, userId);
        if (page.isEmpty()) return ResponseEntity.notFound().build();
        String name = body.get("name");
        if (name == null || name.isBlank() || name.trim().length() > 80) {
            return ResponseEntity.badRequest().body("Tag name must be between 1 and 80 characters");
        }
        String normalizedName = name.trim();
        Tag tag = tagRepository.findByNameIgnoreCase(normalizedName).orElseGet(() -> {
            Tag created = new Tag();
            created.setName(normalizedName);
            return tagRepository.saveAndFlush(created);
        });
        Page editablePage = page.get();
        if (editablePage.getTags().stream().noneMatch(existing -> existing.getId().equals(tag.getId()))) {
            editablePage.getTags().add(tag);
            pageRepository.save(editablePage);
            activityService.record(id, null, userId, "tag_added", "Added the " + tag.getName() + " tag");
        }
        return ResponseEntity.ok(new TagSummary(tag.getId(), tag.getName(), null));
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @Transactional
    public ResponseEntity<String> removePageTag(@PathVariable Long id, @PathVariable Long tagId,
                                                 HttpSession session) {
        Optional<Page> page = pageAccessService.editablePage(id, currentUserId(session));
        if (page.isEmpty()) return ResponseEntity.notFound().build();
        boolean removed = page.get().getTags().removeIf(tag -> tag.getId().equals(tagId));
        if (!removed) return ResponseEntity.notFound().build();
        pageRepository.saveAndFlush(page.get());
        tagRepository.deleteUnusedTags();
        activityService.record(id, null, currentUserId(session), "tag_removed", "Removed a page tag");
        return ResponseEntity.ok("Tag removed from page");
    }

    @GetMapping("/{id}/backlinks")
    public ResponseEntity<?> getBacklinks(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        if (pageAccessService.readablePage(id, userId).isEmpty()) return ResponseEntity.notFound().build();
        List<Page> pages = pageRepository.findAccessibleBacklinks(id, userId);
        return ResponseEntity.ok(pages.stream()
                .map(page -> new Backlink(page.getId(), page.getTitle(), pageAccessService.permission(page, userId)))
                .toList());
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q, HttpSession session) {
        if (q.isBlank()) return ResponseEntity.badRequest().body("Search query is required");
        return ResponseEntity.ok(pageFeatureService.search(currentUserId(session), q));
    }

    @PutMapping("/{id}/title")
    public ResponseEntity<String> updatePageTitle(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                   HttpSession session) {
        String title = body.get("title");
        if (title == null || title.isBlank()) return ResponseEntity.badRequest().body("title is required");
        if (pageAccessService.editablePage(id, currentUserId(session)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        pageRepository.updateTitle(id, title.trim());
        activityService.record(id, null, currentUserId(session), "page_renamed", "Renamed the page");
        return ResponseEntity.ok("Title updated successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> trashPage(@PathVariable Long id, HttpSession session) {
        Optional<Page> page = pageRepository.findById(id);
        if (page.isEmpty() || !page.get().getUserId().equals(currentUserId(session))
                || page.get().getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }
        if (!pageFeatureService.trashSubtree(id, currentUserId(session))) {
            return ResponseEntity.notFound().build();
        }
        activityService.record(id, null, currentUserId(session), "page_trashed", "Moved the page to trash");
        return ResponseEntity.ok("Page moved to trash");
    }

    @GetMapping("/trash")
    public ResponseEntity<List<Page>> getTrash(HttpSession session) {
        return ResponseEntity.ok(pageRepository.findTrashRootsByUserId(currentUserId(session)));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<String> restorePage(@PathVariable Long id, HttpSession session) {
        if (!pageFeatureService.restoreSubtree(id, currentUserId(session))) {
            return ResponseEntity.notFound().build();
        }
        activityService.record(id, null, currentUserId(session), "page_restored", "Restored the page from trash");
        return ResponseEntity.ok("Page restored");
    }

    @DeleteMapping("/trash/{id}")
    @Transactional
    public ResponseEntity<String> permanentlyDeletePage(@PathVariable Long id, HttpSession session) {
        Optional<Page> page = pageRepository.findById(id);
        if (page.isEmpty() || !page.get().getUserId().equals(currentUserId(session))
                || !id.equals(page.get().getTrashRootId())) {
            return ResponseEntity.notFound().build();
        }
        pageRepository.delete(page.get());
        pageRepository.flush();
        tagRepository.deleteUnusedTags();
        return ResponseEntity.ok("Page permanently deleted");
    }

    @GetMapping("/{id}/shares")
    public ResponseEntity<?> getShares(@PathVariable Long id, HttpSession session) {
        Optional<Page> page = pageRepository.findById(id);
        if (page.isEmpty() || !page.get().getUserId().equals(currentUserId(session))
                || page.get().getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> shares = pageShareRepository.findByPageId(id).stream()
                .map(share -> userRepository.findById(share.getSharedWithUserId())
                        .map(user -> Map.<String, Object>of(
                                "userId", user.getId(),
                                "username", user.getUsername(),
                                "email", user.getEmail(),
                                "permission", share.getPermission()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return ResponseEntity.ok(shares);
    }

    @PostMapping("/{id}/shares")
    public ResponseEntity<?> sharePage(@PathVariable Long id, @RequestBody Map<String, String> body,
                                       HttpSession session) {
        Optional<Page> page = pageRepository.findById(id);
        if (page.isEmpty() || !page.get().getUserId().equals(currentUserId(session))
                || page.get().getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }
        String email = body.get("email");
        String permission = body.get("permission");
        if (email == null || email.isBlank() || permission == null
                || !List.of("VIEWER", "EDITOR").contains(permission)) {
            return ResponseEntity.badRequest().body("A valid email and VIEWER or EDITOR permission are required");
        }
        Optional<User> recipient = userRepository.findByEmail(email.trim());
        if (recipient.isEmpty()) return ResponseEntity.badRequest().body("No account found for that email");
        if (recipient.get().getId().equals(currentUserId(session))) {
            return ResponseEntity.badRequest().body("You already own this page");
        }

        PageShare share = pageShareRepository.findByPageIdAndSharedWithUserId(id, recipient.get().getId())
                .orElseGet(PageShare::new);
        share.setPageId(id);
        share.setSharedWithUserId(recipient.get().getId());
        share.setPermission(permission);
        pageShareRepository.save(share);
        activityService.record(id, null, currentUserId(session), "page_shared",
                "Shared the page with " + recipient.get().getUsername() + " as " + permission);
        return ResponseEntity.ok(Map.of("message", "Page shared", "permission", permission));
    }

    @DeleteMapping("/{id}/shares/{sharedUserId}")
    @Transactional
    public ResponseEntity<String> removeShare(@PathVariable Long id, @PathVariable Long sharedUserId,
                                               HttpSession session) {
        Optional<Page> page = pageRepository.findById(id);
        if (page.isEmpty() || !page.get().getUserId().equals(currentUserId(session))) {
            return ResponseEntity.notFound().build();
        }
        if (pageShareRepository.findByPageIdAndSharedWithUserId(id, sharedUserId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        pageShareRepository.deleteByPageIdAndSharedWithUserId(id, sharedUserId);
        activityService.record(id, null, currentUserId(session), "share_removed", "Removed a page share");
        return ResponseEntity.ok("Share removed");
    }

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(UserController.AUTHENTICATED_USER_ID);
    }

    private void addPageMetadata(List<Page> pages, Long userId) {
        if (pages.isEmpty()) return;
        List<Long> pageIds = pages.stream().map(Page::getId).toList();
        Map<Long, List<String>> tagsByPage = new HashMap<>();
        Map<Long, List<Long>> tagIdsByPage = new HashMap<>();
        tagRepository.findTagsForPages(pageIds).forEach(row ->
                {
                    tagsByPage.computeIfAbsent((Long) row[0], ignored -> new ArrayList<>()).add((String) row[2]);
                    tagIdsByPage.computeIfAbsent((Long) row[0], ignored -> new ArrayList<>()).add((Long) row[1]);
                });
        Set<Long> favoriteIds = new HashSet<>(pageFavoriteRepository.findByUserIdAndPageIdIn(userId, pageIds)
                .stream().map(PageFavorite::getPageId).toList());
        pages.forEach(page -> {
            page.setPermission(pageAccessService.permission(page, userId));
            page.setTagNames(tagsByPage.getOrDefault(page.getId(), List.of()));
            page.setTagIds(tagIdsByPage.getOrDefault(page.getId(), List.of()));
            page.setFavorite(favoriteIds.contains(page.getId()));
        });
    }

    public record TemplateRequest(String templateKey, String title, Long parentPageId) {}
    public record TagSummary(Long id, String name, Long count) {}
    public record Backlink(Long id, String title, String permission) {}
    public record RecentPage(Long id, String title, String permission, java.time.LocalDateTime lastViewedAt) {}
}
