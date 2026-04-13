package com.wandocorp.nytscorebot.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String channelId;

    @Column(nullable = false)
    private String name;

    @Column(name = "discord_user_id")
    private String discordUserId;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("date ASC")
    private List<Scoreboard> scoreboards = new ArrayList<>();

    public User(String channelId, String name, String discordUserId) {
        this.channelId = channelId;
        this.name = name;
        this.discordUserId = discordUserId;
    }
}
