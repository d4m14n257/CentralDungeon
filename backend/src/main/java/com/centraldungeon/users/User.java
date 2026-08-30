package com.centraldungeon.users;

import com.centraldungeon.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "discord_id", nullable = false, unique = true, length = 32)
    private String discordId;

    @Column(name = "discord_username", nullable = false, length = 64)
    private String discordUsername;

    @Column(length = 64)
    private @Nullable String name;

    @Column(nullable = false)
    private int karma = 8000;

    @Column(name = "karma_updated_at")
    private @Nullable LocalDateTime karmaUpdatedAt;

    @Column(length = 2)
    private @Nullable String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status = UserStatus.Allowed;

    protected User() {
    }

    public User(String discordId, String discordUsername) {
        this.discordId = discordId;
        this.discordUsername = discordUsername;
    }

    public String getDiscordId() {
        return discordId;
    }

    public String getDiscordUsername() {
        return discordUsername;
    }

    public void setDiscordUsername(String discordUsername) {
        this.discordUsername = discordUsername;
    }

    public @Nullable String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public int getKarma() {
        return karma;
    }

    public @Nullable String getCountry() {
        return country;
    }

    public void setCountry(@Nullable String country) {
        this.country = country;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    /** Onboarding is complete once both display name and country are set (#134). */
    public boolean hasCompletedOnboarding() {
        return name != null && country != null;
    }
}
