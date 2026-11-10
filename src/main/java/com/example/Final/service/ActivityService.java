package com.example.Final.service;

import com.example.Final.entity.PageActivity;
import com.example.Final.repository.PageActivityRepository;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

    private final PageActivityRepository activityRepository;

    public ActivityService(PageActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public void record(Long pageId, Long blockId, Long userId, String action, String details) {
        PageActivity activity = new PageActivity();
        activity.setPageId(pageId);
        activity.setBlockId(blockId);
        activity.setUserId(userId);
        activity.setAction(action);
        activity.setDetails(details.length() > 500 ? details.substring(0, 500) : details);
        activityRepository.save(activity);
    }
}
