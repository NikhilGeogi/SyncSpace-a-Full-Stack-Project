package com.example.Final.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "page_shares", uniqueConstraints = @UniqueConstraint(
        name = "uk_page_shares_page_user", columnNames = {"page_id", "shared_with_user_id"}))
public class PageShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private Long pageId;

    @Column(name = "shared_with_user_id", nullable = false)
    private Long sharedWithUserId;

    @Column(nullable = false, length = 16)
    private String permission;

    public Long getId() { return id; }
    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }
    public Long getSharedWithUserId() { return sharedWithUserId; }
    public void setSharedWithUserId(Long sharedWithUserId) { this.sharedWithUserId = sharedWithUserId; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
}
