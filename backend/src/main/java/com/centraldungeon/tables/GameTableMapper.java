package com.centraldungeon.tables;

import com.centraldungeon.catalogs.dto.CatalogValueResponse;
import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import com.centraldungeon.tables.dto.TableScheduleEntry;
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
            List<CatalogValueResponse> systems,
            List<CatalogValueResponse> tags,
            List<CatalogValueResponse> platforms,
            @Nullable String description,
            @Nullable String permitted,
            @Nullable String requirements,
            boolean scheduleConflict);

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
