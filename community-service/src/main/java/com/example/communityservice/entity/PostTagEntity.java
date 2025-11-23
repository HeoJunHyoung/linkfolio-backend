package com.example.communityservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTagEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    @Column(nullable = false)
    private String tagName;

    public PostTagEntity(PostEntity post, String tagName) {
        this.post = post;
        this.tagName = tagName;
    }
}