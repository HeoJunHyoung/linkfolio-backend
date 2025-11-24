package com.example.communityservice.entity;

import com.example.commonmodule.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "community_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCommentEntity extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    @Column(nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PostCommentEntity parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostCommentEntity> children = new ArrayList<>();

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean isAccepted = false;

    @Builder
    public PostCommentEntity(PostEntity post, Long userId, String content, PostCommentEntity parent) {
        this.post = post;
        this.userId = userId;
        this.content = content;
        this.parent = parent;
    }

    public void accept() {
        this.isAccepted = true;
    }

    public void updateContent(String content) {
        this.content = content;
    }
}