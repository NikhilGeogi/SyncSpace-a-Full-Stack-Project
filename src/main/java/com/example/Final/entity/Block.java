package com.example.Final.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;

/**
 *
 * @author aicpq
 */
@Entity
@Table(name = "blocks")
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", insertable = false, updatable = false)
    @JsonIgnore
    private Page page;

    @Column(name = "block_type")
    private String blockType;

    // TEXT keeps long block content from being truncated by MySQL.
    @Column(columnDefinition = "TEXT")
    private String content;

    // Java defaults are needed because Hibernate includes these fields in inserts.
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_checked", nullable = false)
    private Boolean isChecked = false;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "todo_status", nullable = false, length = 16)
    private String todoStatus = "todo";

    @Column(name = "overdue_flag", nullable = false)
    private Boolean overdueFlag = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }

    public Page getPage() { return page; }

    public String getBlockType() { return blockType; }
    public void setBlockType(String blockType) { this.blockType = blockType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getIsChecked() { return isChecked; }
    public void setIsChecked(Boolean isChecked) { this.isChecked = isChecked; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getTodoStatus() { return todoStatus; }
    public void setTodoStatus(String todoStatus) { this.todoStatus = todoStatus; }
    public Boolean getOverdueFlag() { return overdueFlag; }
    public void setOverdueFlag(Boolean overdueFlag) { this.overdueFlag = overdueFlag; }
}
