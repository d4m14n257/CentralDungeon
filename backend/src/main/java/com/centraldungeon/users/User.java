package com.centraldungeon.users;

import com.centraldungeon.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * A person. Table {@code users}.
 *
 * <p>There is no sign-up of our own: membership of the Discord guild is the front door (#38), so a
 * user row is created by {@code DiscordOAuth2UserService} on first login and never by a form. That
 * is why {@code discordId} is the natural key and why there is no password anywhere in this class.
 *
 * <p>Roles are not here either: they are rows in {@code users_roles}, they stack, and they are read
 * from the database on every request rather than carried in a token (#37, #67, #122).
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    /** Discord's own id for this person. The natural key, and the only stable one we have. */
    @Column(name = "discord_id", nullable = false, unique = true, length = 32)
    private String discordId;

    /** Their Discord handle. Refreshed on each login, since people rename themselves there. */
    @Column(name = "discord_username", nullable = false, length = 64)
    private String discordUsername;

    /** The display name they chose during onboarding. Null until they complete it (#134). */
    @Column(length = 64)
    private @Nullable String name;

    /** Community reputation (#97). Everyone starts at the same 8000; comments move it. */
    @Column(nullable = false)
    private int karma = 8000;

    /** When karma last changed. Null for someone nobody has commented on yet. */
    @Column(name = "karma_updated_at")
    private @Nullable LocalDateTime karmaUpdatedAt;

    /** ISO 3166-1 alpha-2. Asked during onboarding because it is what makes a play time legible. */
    @Column(length = 2)
    private @Nullable String country;

    /** Whether the account may be used. Re-read from here on every request, never from a token (#122). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status = UserStatus.Allowed;

    /** Required by JPA. */
    protected User() {
    }

    /**
     * Creates the row for someone logging in for the first time. Everything else - display name,
     * country, roles - is filled in afterwards.
     *
     * @param discordId       Discord's id for them
     * @param discordUsername their Discord handle at this moment
     */
    public User(String discordId, String discordUsername) {
        this.discordId = discordId;
        this.discordUsername = discordUsername;
    }

    /**
     * Returns Discord's id for this person.
     *
     * @return the Discord id, never null
     */
    public String getDiscordId() {
        return discordId;
    }

    /**
     * Returns their Discord handle.
     *
     * @return the handle as of the last login, never null
     */
    public String getDiscordUsername() {
        return discordUsername;
    }

    /**
     * Refreshes the handle. Called on every login, because people rename themselves on Discord.
     *
     * @param discordUsername the handle Discord reports now
     */
    public void setDiscordUsername(String discordUsername) {
        this.discordUsername = discordUsername;
    }

    /**
     * Returns the display name they chose.
     *
     * @return the name, or null while onboarding is incomplete (#134)
     */
    public @Nullable String getName() {
        return name;
    }

    /**
     * Sets the display name.
     *
     * @param name what they want to be called across the site
     */
    public void setName(@Nullable String name) {
        this.name = name;
    }

    /**
     * Returns their community reputation.
     *
     * @return the karma, recalculated when a comment is approved or by the weekly job (#97)
     */
    public int getKarma() {
        return karma;
    }

    /**
     * Returns where they play from.
     *
     * @return the ISO 3166-1 alpha-2 code, or null while onboarding is incomplete
     */
    public @Nullable String getCountry() {
        return country;
    }

    /**
     * Sets where they play from.
     *
     * @param country an ISO 3166-1 alpha-2 code
     */
    public void setCountry(@Nullable String country) {
        this.country = country;
    }

    /**
     * Returns whether the account may be used.
     *
     * @return the status, never null
     */
    public UserStatus getStatus() {
        return status;
    }

    /**
     * Blocks, unblocks or soft-deletes the account.
     *
     * @param status the new status. Changing it has to evict the security cache, or the old value
     *               stays live for up to its TTL (#128)
     */
    public void setStatus(UserStatus status) {
        this.status = status;
    }

    /**
     * Onboarding is complete once both display name and country are set (#134).
     *
     * @return true when the person can be let past the onboarding screen
     */
    public boolean hasCompletedOnboarding() {
        return name != null && country != null;
    }
}
