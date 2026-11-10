package com.example.Final.service;

import com.example.Final.repository.BlockRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TodoReminderService {

    private final BlockRepository blockRepository;

    public TodoReminderService(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 3600000)
    public void flagOverdueTodos() {
        blockRepository.refreshOverdueFlags();
    }
}
