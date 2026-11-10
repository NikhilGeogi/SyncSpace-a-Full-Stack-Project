package com.example.Final.service;

import com.example.Final.entity.Block;
import com.example.Final.repository.BlockRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PageTemplateService {

    private static final Map<String, PageTemplate> TEMPLATES = Map.of(
            "meeting-notes", new PageTemplate("Meeting notes", "Capture attendees, agenda, notes, and follow-ups.",
                    List.of(
                            new BlockSeed("h1", "Attendees"),
                            new BlockSeed("p", ""),
                            new BlockSeed("h1", "Agenda"),
                            new BlockSeed("p", ""),
                            new BlockSeed("h1", "Notes"),
                            new BlockSeed("p", ""),
                            new BlockSeed("h1", "Action items"),
                            new BlockSeed("todo", ""))),
            "weekly-plan", new PageTemplate("Weekly plan", "Set a focus for the week and turn priorities into action.",
                    List.of(
                            new BlockSeed("h1", "Weekly focus"),
                            new BlockSeed("p", ""),
                            new BlockSeed("h1", "Top priorities"),
                            new BlockSeed("todo", ""),
                            new BlockSeed("todo", ""),
                            new BlockSeed("todo", ""),
                            new BlockSeed("h1", "Notes"),
                            new BlockSeed("p", ""))),
            "project-brief", new PageTemplate("Project brief", "Align on the goal, scope, and next milestone.",
                    List.of(
                            new BlockSeed("h1", "Objective"),
                            new BlockSeed("p", ""),
                            new BlockSeed("h1", "Scope"),
                            new BlockSeed("p", ""),
                            new BlockSeed("h1", "Success measures"),
                            new BlockSeed("todo", ""),
                            new BlockSeed("h1", "Next steps"),
                            new BlockSeed("todo", ""))));

    private final BlockRepository blockRepository;

    public PageTemplateService(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    public List<TemplateInfo> listTemplates() {
        return TEMPLATES.entrySet().stream()
                .map(entry -> new TemplateInfo(entry.getKey(), entry.getValue().title(), entry.getValue().description()))
                .sorted((first, second) -> first.title().compareTo(second.title()))
                .toList();
    }

    public boolean contains(String templateKey) {
        return TEMPLATES.containsKey(templateKey);
    }

    public String defaultTitle(String templateKey) {
        return TEMPLATES.get(templateKey).title();
    }

    public void addTemplateBlocks(Long pageId, String templateKey) {
        List<BlockSeed> seeds = TEMPLATES.get(templateKey).blocks();
        List<Block> blocks = new java.util.ArrayList<>(seeds.size());
        for (int index = 0; index < seeds.size(); index++) {
            BlockSeed seed = seeds.get(index);
            Block block = new Block();
            block.setPageId(pageId);
            block.setBlockType(seed.type());
            block.setContent(seed.content());
            block.setSortOrder(index + 1);
            block.setIsChecked(false);
            blocks.add(block);
        }
        blockRepository.saveAll(blocks);
    }

    private record PageTemplate(String title, String description, List<BlockSeed> blocks) {}
    private record BlockSeed(String type, String content) {}

    public record TemplateInfo(String key, String title, String description) {}
}
