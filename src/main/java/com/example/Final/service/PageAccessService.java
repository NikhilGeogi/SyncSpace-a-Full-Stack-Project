package com.example.Final.service;

import com.example.Final.entity.Page;
import com.example.Final.entity.PageShare;
import com.example.Final.repository.PageRepository;
import com.example.Final.repository.PageShareRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PageAccessService {

    private final PageRepository pageRepository;
    private final PageShareRepository pageShareRepository;

    public PageAccessService(PageRepository pageRepository, PageShareRepository pageShareRepository) {
        this.pageRepository = pageRepository;
        this.pageShareRepository = pageShareRepository;
    }

    public Optional<Page> readablePage(Long pageId, Long userId) {
        return pageRepository.findById(pageId)
                .filter(page -> page.getDeletedAt() == null && permission(page, userId) != null);
    }

    public Optional<Page> editablePage(Long pageId, Long userId) {
        return readablePage(pageId, userId)
                .filter(page -> "OWNER".equals(permission(page, userId))
                        || "EDITOR".equals(permission(page, userId)));
    }

    public String permission(Page page, Long userId) {
        if (page.getUserId().equals(userId)) return "OWNER";
        return pageShareRepository.findByPageIdAndSharedWithUserId(page.getId(), userId)
                .map(PageShare::getPermission)
                .orElse(null);
    }
}
