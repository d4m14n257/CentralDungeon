package com.centraldungeon.tables;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterService {

    private static final List<TableRegistrationStatus> ACTIVE_APPLICATION_STATUSES =
            List.of(TableRegistrationStatus.Candidate, TableRegistrationStatus.Player);

    private final MasterRepository masterRepository;
    private final TableRegistrationRepository registrationRepository;
    private final UserService userService;

    public MasterService(MasterRepository masterRepository, TableRegistrationRepository registrationRepository, UserService userService) {
        this.masterRepository = masterRepository;
        this.registrationRepository = registrationRepository;
        this.userService = userService;
    }

    @Transactional
    public Master createPrimary(GameTable gameTable, User creator) {
        return masterRepository.save(new Master(gameTable, creator, MasterType.Primary));
    }

    /**
     * Only the current Primary may add a Secondary or hand off Primary itself (modelo-datos.md #73).
     * The row lock on the table's existing masters serializes concurrent calls so exactly one
     * Primary survives even under a race - the invariant MySQL cannot enforce with a partial
     * unique index.
     */
    @Transactional
    public void addOrPromote(GameTable gameTable, String actorId, String targetUserId, MasterType requestedType) {
        List<Master> masters = masterRepository.findByGameTableIdForUpdate(gameTable.getId());

        Master actorMaster = masters.stream()
                .filter(m -> m.getUser().getId().equals(actorId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenActionException("Only a master of this table can manage its masters"));
        if (actorMaster.getMasterType() != MasterType.Primary) {
            throw new ForbiddenActionException("Only the Primary master can add or promote masters");
        }

        Master target = masters.stream()
                .filter(m -> m.getUser().getId().equals(targetUserId))
                .findFirst()
                .orElseGet(() -> {
                    if (registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(
                            gameTable.getId(), targetUserId, ACTIVE_APPLICATION_STATUSES)) {
                        throw new ConflictException("A player of this table cannot be made its master");
                    }
                    return masterRepository.save(new Master(gameTable, userService.getById(targetUserId), MasterType.Secondary));
                });

        if (requestedType == MasterType.Primary) {
            if (!target.getUser().getId().equals(actorId)) {
                actorMaster.setMasterType(MasterType.Secondary);
            }
            target.setMasterType(MasterType.Primary);
        } else {
            target.setMasterType(MasterType.Secondary);
        }
    }

    @Transactional(readOnly = true)
    public List<Master> findByGameTable(String gameTableId) {
        return masterRepository.findByGameTable_Id(gameTableId);
    }

    @Transactional(readOnly = true)
    public boolean isMasterOf(String gameTableId, String userId) {
        return masterRepository.findByGameTable_IdAndUser_Id(gameTableId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isPrimaryOf(String gameTableId, String userId) {
        return masterRepository
                .findByGameTable_IdAndUser_Id(gameTableId, userId)
                .map(master -> master.getMasterType() == MasterType.Primary)
                .orElse(false);
    }
}
