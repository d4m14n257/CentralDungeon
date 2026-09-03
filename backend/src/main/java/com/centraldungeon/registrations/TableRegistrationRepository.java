package com.centraldungeon.registrations;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableRegistrationRepository extends JpaRepository<TableRegistration, String> {

    boolean existsByGameTable_IdAndUser_IdAndStatusIn(String gameTableId, String userId, Collection<TableRegistrationStatus> statuses);

    long countByGameTable_IdAndStatus(String gameTableId, TableRegistrationStatus status);

    Page<TableRegistration> findByGameTable_IdAndStatus(String gameTableId, TableRegistrationStatus status, Pageable pageable);

    /** FIFO order is not negotiable (modelo-datos.md #28): callers never re-sort this. */
    List<TableRegistration> findByGameTable_IdAndStatusOrderByCreatedAtAsc(String gameTableId, TableRegistrationStatus status);

    Page<TableRegistration> findByUser_Id(String userId, Pageable pageable);

    /** Backs /my/tables: every game table where the actor holds an active Player registration. */
    Page<TableRegistration> findByUser_IdAndStatus(String userId, TableRegistrationStatus status, Pageable pageable);

    boolean existsByGameTable_IdAndStatusIn(String gameTableId, Collection<TableRegistrationStatus> statuses);

    List<TableRegistration> findByGameTable_Id(String gameTableId);
}
