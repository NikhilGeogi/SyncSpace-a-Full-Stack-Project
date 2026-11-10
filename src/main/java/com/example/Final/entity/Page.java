package com.example.Final.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

/**
 *
 * @author aicpq
 */
@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kept non-null to match V1__initial_schema.sql.
    @Column(nullable = false)
    private String title;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "parent_page_id")
    private Long parentPageId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "trash_root_id")
    private Long trashRootId;

    @Column(name = "public_token", unique = true, length = 64)
    @JsonIgnore
    private String publicToken;

    @Transient
    private String permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_page_id", insertable = false, updatable = false)
    @JsonIgnore
    private Page parentPage;

    @OneToMany(mappedBy = "parentPage", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Page> childPages = new ArrayList<>();

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Block> blocks = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "page_tag_links",
            joinColumns = @JoinColumn(name = "page_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @JsonIgnore
    private List<Tag> tags = new ArrayList<>();

    @Transient
    private List<String> tagNames = new ArrayList<>();

    @Transient
    private List<Long> tagIds = new ArrayList<>();

    @Transient
    private boolean favorite;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getParentPageId() { return parentPageId; }
    public void setParentPageId(Long parentPageId) { this.parentPageId = parentPageId; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public Long getTrashRootId() { return trashRootId; }
    public void setTrashRootId(Long trashRootId) { this.trashRootId = trashRootId; }
    public String getPublicToken() { return publicToken; }
    public void setPublicToken(String publicToken) { this.publicToken = publicToken; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public User getUser() { return user; }
    public Page getParentPage() { return parentPage; }
    public List<Page> getChildPages() { return childPages; }
    public List<Block> getBlocks() { return blocks; }
    public List<Tag> getTags() { return tags; }
    public List<String> getTagNames() { return tagNames; }
    public void setTagNames(List<String> tagNames) { this.tagNames = tagNames; }
    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
}
