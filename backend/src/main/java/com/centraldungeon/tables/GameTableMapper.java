package com.centraldungeon.tables;

import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.tables.dto.PlayerSessionResponse;
import com.centraldungeon.tables.dto.PublicSessionResponse;
import com.centraldungeon.tables.dto.SessionAttendanceEntry;
import com.centraldungeon.tables.dto.TableScheduleEntry;
import com.centraldungeon.tables.dto.TableSessionResponse;
import com.centraldungeon.tables.dto.TableStatusChangeResponse;
import com.centraldungeon.users.User;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

/** Wired as a @Bean in common/config/MapperConfig.java, not componentModel="spring" - see that class for why. */
@Mapper
public interface GameTableMapper {

    @Mapping(target = "name", source = "gameTable.name")
    @Mapping(target = "status", expression = "java(gameTable.getStatus().name())")
    @Mapping(target = "tableTypeName", expression = "java(gameTable.getTableType() != null ? gameTable.getTableType().getName() : null)")
    GameTableSummaryResponse toSummary(
            GameTable gameTable,
            int playerCount,
            MasterSummaryResponse primaryMaster,
            List<TableScheduleEntry> schedule,
            boolean scheduleConflict);

    /**
     * The three rich-text fields arrive as parameters rather than being read off the entity: they are
     * sanitized on the way out as well as on the way in (#62), and passing the cleaned strings in
     * keeps that from turning a read into a write on the managed entity.
     */
    @Mapping(target = "status", expression = "java(gameTable.getStatus().name())")
    @Mapping(target = "tableTypeName", expression = "java(gameTable.getTableType() != null ? gameTable.getTableType().getName() : null)")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "permitted", source = "permitted")
    @Mapping(target = "requirements", source = "requirements")
    GameTableDetailResponse toDetail(
            GameTable gameTable,
            int playerCount,
            List<MasterSummaryResponse> masters,
            List<TableScheduleEntry> schedule,
            List<PublicSessionResponse> sessions,
            List<CatalogValueResponse> systems,
            List<CatalogValueResponse> tags,
            List<CatalogValueResponse> platforms,
            @Nullable String description,
            @Nullable String permitted,
            @Nullable String requirements,
            boolean scheduleConflict);

    /**
     * One session as its master sees it, with the roster the service assembled.
     *
     * <p>The roster arrives as a parameter and is not read off the entity: it is the table's active
     * players joined with whatever was recorded, which is a question about two tables and not about
     * this row (#36).
     *
     * @param session    the session
     * @param attendance the roster, one line per active player of the table
     * @return the session's response
     */
    default TableSessionResponse toSessionResponse(TableSession session, List<SessionAttendanceEntry> attendance) {
        return new TableSessionResponse(
                session.getId(),
                session.getSequenceNumber(),
                session.getScheduledAt(),
                session.getStatus(),
                session.getNotes(),
                attendance);
    }

    /**
     * One session as the player sitting at the table sees it: no notes, and only their own attendance
     * (#121).
     *
     * @param session      the session
     * @param myAttendance what was recorded for the actor of the token, or {@code Unknown}
     * @return the player's view of the session
     */
    default PlayerSessionResponse toPlayerSessionResponse(TableSession session, AttendanceStatus myAttendance) {
        return new PlayerSessionResponse(
                session.getId(), session.getSequenceNumber(), session.getScheduledAt(), session.getStatus(), myAttendance);
    }

    /**
     * One session as anybody looking at the table sees it: when, and how it went.
     *
     * @param session the session
     * @return the public view of it, with neither the notes nor anybody's attendance
     */
    default PublicSessionResponse toPublicSessionResponse(TableSession session) {
        return new PublicSessionResponse(
                session.getId(), session.getSequenceNumber(), session.getScheduledAt(), session.getStatus());
    }

    /**
     * One line of a session's roster.
     *
     * @param player     the player the line is about
     * @param attendance what was recorded for them, or {@code Unknown} when nothing was (#137)
     * @return the roster line
     */
    default SessionAttendanceEntry toAttendanceEntry(User player, AttendanceStatus attendance) {
        String displayName = player.getName() != null ? player.getName() : player.getDiscordUsername();
        return new SessionAttendanceEntry(player.getId(), displayName, attendance);
    }

    default MasterSummaryResponse toMasterSummary(Master master) {
        String displayName = master.getUser().getName() != null ? master.getUser().getName() : master.getUser().getDiscordUsername();
        return new MasterSummaryResponse(
                master.getUser().getId(), displayName, master.getUser().getKarma(), master.getMasterType().name());
    }

    default TableStatusChangeResponse toStatusChangeResponse(TableStatusChange change) {
        User changedBy = change.getChangedBy();
        String displayName = changedBy.getName() != null ? changedBy.getName() : changedBy.getDiscordUsername();
        return new TableStatusChangeResponse(
                change.getId(), change.getFromStatus().name(), change.getToStatus().name(), displayName, change.getJustification(),
                change.getCreatedAt());
    }
}
