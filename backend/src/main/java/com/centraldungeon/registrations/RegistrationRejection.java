package com.centraldungeon.registrations;

import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/** No BaseEntity: this table has no created_at/updated_at/deleted_at - rejected_at is its own timestamp. */
@Entity
@Table(name = "registration_rejections")
public class RegistrationRejection {

    @Id
    @Column(length = 64)
    private @Nullable String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private TableRegistration registration;

    @Column(columnDefinition = "LONGTEXT")
    private @Nullable String description;

    @Column(name = "rejected_at", nullable = false)
    private @Nullable LocalDateTime rejectedAt;

    /** null = automatic rejection because the table filled up (modelo-datos.md #34). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private @Nullable User rejectedBy;

    protected RegistrationRejection() {
    }

    public RegistrationRejection(TableRegistration registration, String description, @Nullable User rejectedBy) {
        this.registration = registration;
        this.description = description;
        this.rejectedBy = rejectedBy;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = IdGenerator.newId();
        }
        rejectedAt = LocalDateTime.now();
    }

    public TableRegistration getRegistration() {
        return registration;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable User getRejectedBy() {
        return rejectedBy;
    }
}
