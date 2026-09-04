package com.centraldungeon.tasks;

import com.centraldungeon.common.model.BaseEntity;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.TableSession;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Something a table asks of somebody. Table {@code table_tasks}.
 *
 * <p><b>One shape for what used to be three things</b> (#63). The requirements a master publishes
 * when opening the table, what a player is asked for once accepted, and what comes up mid-campaign
 * are the same object at different moments: somebody asks, somebody answers with text, files, or
 * both. The analogy the feature was designed against is a classroom.
 *
 * <p><b>{@code isMandatory} is a label and nothing else</b> (#70). Nothing in this feature reads it
 * to refuse an action, and that is the decision, not an omission: a missing answer can have a
 * thousand reasons and automating a punishment over it does more damage than it prevents. The system
 * reports; the people decide.
 *
 * <p><b>{@code tableSession} being null is the common case</b>, not a missing value: most tasks are
 * "whenever you can". A non-null one ties the ask to one evening, which is what makes "bring the
 * map for session 4" expressible at all.
 *
 * <p>Named {@code TableTask} rather than {@code TableRequirement} (#87): {@code game_tables} already
 * has a {@code requirements} column holding rich text, and a {@code TableRequirement} entity next to
 * {@code gameTable.getRequirements()} would be two different things wearing one name.
 */
@Entity
@Table(name = "table_tasks")
public class TableTask extends BaseEntity {

    /** The table doing the asking. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_table_id", nullable = false)
    private GameTable gameTable;

    /**
     * The session this is tied to, or null for "at any point" (#63).
     *
     * <p>The service checks the session belongs to this table before it is ever set: a task pointing
     * at somebody else's evening is a row nothing on screen could explain.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_session_id")
    private @Nullable TableSession tableSession;

    /** Who is being asked (#63). It is what publication resolves into a list of recipients (#77). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskAudience audience;

    /** The one person addressed, and only when {@link TaskAudience#Single} (#76). Null otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private @Nullable User targetUser;

    /** The headline the recipient reads in their notification and on the table. */
    @Column(nullable = false, length = 128)
    private String title;

    /** The detail, as sanitized rich text (#62). Sanitized on the way in and on the way out. */
    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String description;

    /** Whether a written answer is expected. At least one of the two {@code accepts} is always true. */
    @Column(name = "accepts_text", nullable = false)
    private boolean acceptsText = true;

    /** Whether files are expected. Reused from the submitter's history, never re-uploaded (#65). */
    @Column(name = "accepts_files", nullable = false)
    private boolean acceptsFiles = true;

    /**
     * Whether the master considers this indispensable.
     *
     * <p><b>Informational</b> (#70): it changes what the screen says and nothing else. No code path
     * reads it to block, evict or refuse.
     */
    @Column(name = "is_mandatory", nullable = false)
    private boolean isMandatory;

    /** When the master would like it by, in UTC (#22), or null for no date. Nothing expires on it. */
    @Column(name = "due_at")
    private @Nullable LocalDateTime dueAt;

    /** Taking answers, closed, or gone. Only {@link TableTaskService} moves it. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status = TaskStatus.Open;

    /** Soft delete (#25). Null for a live row. */
    @Column(name = "deleted_at")
    private @Nullable LocalDateTime deletedAt;

    /** Required by JPA. */
    protected TableTask() {
    }

    /**
     * Publishes a task, open from the start - there is no draft state (#77).
     *
     * @param gameTable the table doing the asking
     * @param audience  who is being asked
     * @param title     the headline the recipient reads
     */
    public TableTask(GameTable gameTable, TaskAudience audience, String title) {
        this.gameTable = gameTable;
        this.audience = audience;
        this.title = title;
    }

    /**
     * Returns the table doing the asking.
     *
     * @return the table, never null on a persisted row
     */
    public GameTable getGameTable() {
        return gameTable;
    }

    /**
     * Returns the session this task is tied to.
     *
     * @return the session, or null when the ask is not tied to one evening (#63)
     */
    public @Nullable TableSession getTableSession() {
        return tableSession;
    }

    /**
     * Ties the task to one session of its table, or unties it.
     *
     * @param tableSession the session, already checked to belong to this table, or null for "at any point"
     */
    public void setTableSession(@Nullable TableSession tableSession) {
        this.tableSession = tableSession;
    }

    /**
     * Returns who is being asked.
     *
     * @return the audience, never null
     */
    public TaskAudience getAudience() {
        return audience;
    }

    /**
     * Changes who is being asked.
     *
     * @param audience the new audience. The service re-checks the target against it before saving
     */
    public void setAudience(TaskAudience audience) {
        this.audience = audience;
    }

    /**
     * Returns the one person addressed.
     *
     * @return the target, or null on any audience other than {@link TaskAudience#Single}
     */
    public @Nullable User getTargetUser() {
        return targetUser;
    }

    /**
     * Sets or clears the one person addressed.
     *
     * @param targetUser the recipient, non-null only together with {@link TaskAudience#Single}
     */
    public void setTargetUser(@Nullable User targetUser) {
        this.targetUser = targetUser;
    }

    /**
     * Returns the headline.
     *
     * @return the title, never null
     */
    public String getTitle() {
        return title;
    }

    /**
     * Rewrites the headline.
     *
     * @param title the new title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the detail.
     *
     * @return the description as stored rich text, or null when the title said it all
     */
    public @Nullable String getDescription() {
        return description;
    }

    /**
     * Rewrites the detail.
     *
     * @param description the new rich text, already sanitized by the service (#62)
     */
    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    /**
     * Returns whether a written answer is expected.
     *
     * @return true when the task takes text
     */
    public boolean isAcceptsText() {
        return acceptsText;
    }

    /**
     * Sets whether a written answer is expected.
     *
     * @param acceptsText true to take text. The service refuses leaving both channels off
     */
    public void setAcceptsText(boolean acceptsText) {
        this.acceptsText = acceptsText;
    }

    /**
     * Returns whether files are expected.
     *
     * @return true when the task takes files
     */
    public boolean isAcceptsFiles() {
        return acceptsFiles;
    }

    /**
     * Sets whether files are expected.
     *
     * @param acceptsFiles true to take files. The service refuses leaving both channels off
     */
    public void setAcceptsFiles(boolean acceptsFiles) {
        this.acceptsFiles = acceptsFiles;
    }

    /**
     * Returns whether the master considers this indispensable.
     *
     * @return true when the task is labelled mandatory. <b>Informational only</b> (#70)
     */
    public boolean isMandatory() {
        return isMandatory;
    }

    /**
     * Labels the task mandatory, or stops labelling it so.
     *
     * @param mandatory true to label it. It changes what the screen says and nothing else (#70)
     */
    public void setMandatory(boolean mandatory) {
        this.isMandatory = mandatory;
    }

    /**
     * Returns when the master would like the answer by.
     *
     * @return the instant in UTC (#22), or null when no date was set. Nothing happens when it passes
     */
    public @Nullable LocalDateTime getDueAt() {
        return dueAt;
    }

    /**
     * Sets or clears the date the master would like the answer by.
     *
     * @param dueAt the instant in UTC (#22), or null for no date
     */
    public void setDueAt(@Nullable LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    /**
     * Returns where the task is in its life.
     *
     * @return the status, never null
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Moves the task's status - closing the intake, or marking it gone.
     *
     * @param status the new status
     */
    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    /**
     * Returns when the task was marked gone.
     *
     * @return the timestamp of the logical delete, or null while it is live
     */
    public @Nullable LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * Stamps or clears the logical delete (#25).
     *
     * @param deletedAt when it was marked gone, or null to bring it back
     */
    public void setDeletedAt(@Nullable LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
