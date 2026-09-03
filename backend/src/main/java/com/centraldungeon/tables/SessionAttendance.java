package com.centraldungeon.tables;

import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Whether one player was at one session. Table {@code session_attendance}.
 *
 * <p>It exists for two things (#36): letting a master see who actually turns up, and proving that
 * somebody who comments about a person really shared a table with them (#31) - evidence that is
 * <b>used and not persisted</b> when the comment is written.
 *
 * <p><b>Nothing is cached from these rows</b> (#137). The historical counts are a {@code GROUP BY}
 * over them, following the precedent of #11 that removed a trigger-maintained counter: a cached
 * total is a class of inconsistency, not an optimization. The baseline's covering index
 * {@code (user_id, attendance)} is what keeps that aggregate index-only.
 */
@Entity
@Table(name = "session_attendance")
public class SessionAttendance {

    /** The pair (session, user) this row records. */
    @EmbeddedId
    private SessionAttendanceId id;

    /** The session being recorded. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tableSessionId")
    @JoinColumn(name = "table_session_id")
    private TableSession session;

    /** The player whose attendance it is. */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    /** Present, Absent, Excused or Unknown (#36). The four never collapse into fewer (#137). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttendanceStatus attendance = AttendanceStatus.Unknown;

    /** When the row was first written. Stamped on persist. */
    @Column(name = "created_at", nullable = false)
    private @Nullable LocalDateTime createdAt;

    /** When the record was last corrected. Null until the first correction. */
    @Column(name = "updated_at")
    private @Nullable LocalDateTime updatedAt;

    /** Required by JPA. */
    protected SessionAttendance() {
    }

    /**
     * Records one player's attendance at one session.
     *
     * @param session    the session, already persisted - its id becomes half the composite key
     * @param user       the player. That they actually play at the table is
     *                   {@link TableSessionService}'s check, not this constructor's
     * @param attendance what was recorded
     */
    public SessionAttendance(TableSession session, User user, AttendanceStatus attendance) {
        this.session = session;
        this.user = user;
        this.attendance = attendance;
        this.id = new SessionAttendanceId(session.getId(), user.getId());
    }

    /** Stamps {@code createdAt} on insert. Called by JPA, never by application code. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Stamps {@code updatedAt} on every correction. Called by JPA, never by application code. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Returns the pair this row records.
     *
     * @return the composite key, never null on a persisted row
     */
    public SessionAttendanceId getId() {
        return id;
    }

    /**
     * Returns the session being recorded.
     *
     * @return the session, lazily loaded
     */
    public TableSession getSession() {
        return session;
    }

    /**
     * Returns the player whose attendance this is.
     *
     * @return the user, lazily loaded
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns what was recorded.
     *
     * @return the attendance, never null. {@code Unknown} means nobody recorded anything and stays
     *         out of every denominator (#137)
     */
    public AttendanceStatus getAttendance() {
        return attendance;
    }

    /**
     * Corrects what was recorded.
     *
     * @param attendance the value the master now says is true
     */
    public void setAttendance(AttendanceStatus attendance) {
        this.attendance = attendance;
    }

    /**
     * Returns when the row was first written.
     *
     * @return the creation timestamp, or null before the row is persisted
     */
    public @Nullable LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns when the record was last corrected.
     *
     * @return the update timestamp, or null if it was never corrected
     */
    public @Nullable LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
