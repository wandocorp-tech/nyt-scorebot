package com.wandocorp.nytscorebot.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

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

    @Column
    private String userId;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("date ASC")
    private List<Scoreboard> scoreboards = new ArrayList<>();

    protected User() {}

    public User(String channelId, String name, String discordUserId) {
        this.channelId = channelId;
        this.name = name;
        this.userId = discordUserId;
    }

    public Long getId() { return id; }
    public String getChannelId() { return channelId; }
    public String getName() { return name; }
    public String getUserId() { return userId; }
    public List<Scoreboard> getScoreboards() { return scoreboards; }
}
