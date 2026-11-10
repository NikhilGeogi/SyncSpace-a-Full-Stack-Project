package com.example.Final.controller;

import com.example.Final.entity.Block;
import com.example.Final.repository.BlockRepository;
import com.example.Final.service.ActivityService;
import com.example.Final.service.PageAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/pages")
public class PageImportController {

    private static final long MAX_IMPORT_BYTES = 1_048_576;
    private static final Pattern TASK_LINE = Pattern.compile("^\\s*[-*]\\s+\\[([ xX])\\]\\s*(.*)$");
    private static final Pattern HEADING_LINE = Pattern.compile("^#{1,6}\\s+(.*)$");

    private final BlockRepository blockRepository;
    private final PageAccessService pageAccessService;
    private final ActivityService activityService;

    public PageImportController(BlockRepository blockRepository, PageAccessService pageAccessService,
                                ActivityService activityService) {
        this.blockRepository = blockRepository;
        this.pageAccessService = pageAccessService;
        this.activityService = activityService;
    }

    @PostMapping(value = "/{pageId}/import-markdown", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<?> importMarkdown(@PathVariable Long pageId, @RequestParam("file") MultipartFile file,
                                             HttpSession session) {
        Long userId = currentUserId(session);
        if (pageAccessService.editablePage(pageId, userId).isEmpty()) return ResponseEntity.notFound().build();
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
            return ResponseEntity.badRequest().body("Choose a Markdown (.md) file");
        }
        if (file.isEmpty() || file.getSize() > MAX_IMPORT_BYTES) {
            return ResponseEntity.badRequest().body("Markdown files must be between 1 byte and 1 MB");
        }
        String markdown;
        try {
            markdown = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the uploaded Markdown file", exception);
        }
        int nextOrder = blockRepository.findByPageId(pageId).stream()
                .mapToInt(Block::getSortOrder)
                .max()
                .orElse(0) + 1;
        List<Block> imported = new ArrayList<>();
        for (String line : markdown.split("\\R", -1)) {
            Block block = new Block();
            block.setPageId(pageId);
            block.setSortOrder(nextOrder++);
            Matcher heading = HEADING_LINE.matcher(line);
            Matcher task = TASK_LINE.matcher(line);
            if (heading.matches()) {
                block.setBlockType("h1");
                block.setContent(heading.group(1).trim());
            } else if (task.matches()) {
                block.setBlockType("todo");
                block.setContent(task.group(2).trim());
                boolean checked = !task.group(1).isBlank() && !task.group(1).equalsIgnoreCase(" ");
                block.setIsChecked(checked);
                block.setTodoStatus(checked ? "done" : "todo");
            } else {
                block.setBlockType("p");
                block.setContent(line.replaceFirst("^\\s*[-*+]\\s+", "").trim());
            }
            if (!block.getContent().isEmpty()) imported.add(block);
        }
        if (!imported.isEmpty()) blockRepository.saveAll(imported);
        activityService.record(pageId, null, userId, "markdown_imported",
                "Imported " + imported.size() + " blocks from Markdown");
        return ResponseEntity.ok(new ImportResult(imported.size()));
    }

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(UserController.AUTHENTICATED_USER_ID);
    }

    public record ImportResult(int importedBlocks) {}
}
