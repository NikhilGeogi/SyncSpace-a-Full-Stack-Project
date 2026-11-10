package com.example.Final.controller;

import com.example.Final.entity.Block;
import com.example.Final.entity.Page;
import com.example.Final.repository.BlockRepository;
import com.example.Final.repository.PageRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PageRepository pageRepository;
    private final BlockRepository blockRepository;

    public DashboardController(PageRepository pageRepository, BlockRepository blockRepository) {
        this.pageRepository = pageRepository;
        this.blockRepository = blockRepository;
    }

    @GetMapping("/stats")
    public DashboardStats getStats(HttpSession session) {
        List<Page> pages = pageRepository.findAllPagesByUserId(currentUserId(session));
        if (pages.isEmpty()) return new DashboardStats(0, 0, 0, 0);
        List<Long> pageIds = pages.stream().map(Page::getId).toList();
        List<Block> blocks = blockRepository.findByPageIdIn(pageIds);
        long completed = blocks.stream().filter(block -> "todo".equals(block.getBlockType())
                && "done".equals(block.getTodoStatus())).count();
        long open = blocks.stream().filter(block -> "todo".equals(block.getBlockType())
                && !"done".equals(block.getTodoStatus())).count();
        return new DashboardStats(pages.size(), blocks.size(), completed, open);
    }

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(UserController.AUTHENTICATED_USER_ID);
    }

    public record DashboardStats(long pages, long blocks, long completedTodos, long openTodos) {}
}
