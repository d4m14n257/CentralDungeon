package com.centraldungeon.tables;

import com.centraldungeon.tables.dto.GameTableDetailResponse;
import com.centraldungeon.tables.dto.GameTableSummaryResponse;
import com.centraldungeon.tables.dto.MasterSummaryResponse;
import java.util.List;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;

/** Wired as a @Bean in common/config/MapperConfig.java, not componentModel="spring" - see that class for why. */
@Mapper
public interface GameTableMapper {

    @Mapping(target = "name", source = "gameTable.name")
    @Mapping(target = "status", expression = "java(gameTable.getStatus().name())")
    @Mapping(target = "tableTypeName", expression = "java(gameTable.getTableType() != null ? gameTable.getTableType().getName() : null)")
    GameTableSummaryResponse toSummary(GameTable gameTable, int playerCount, MasterSummaryResponse primaryMaster);

    @Mapping(target = "status", expression = "java(gameTable.getStatus().name())")
    @Mapping(target = "tableTypeName", expression = "java(gameTable.getTableType() != null ? gameTable.getTableType().getName() : null)")
    GameTableDetailResponse toDetail(GameTable gameTable, int playerCount, List<MasterSummaryResponse> masters);

    default MasterSummaryResponse toMasterSummary(Master master) {
        String displayName = master.getUser().getName() != null ? master.getUser().getName() : master.getUser().getDiscordUsername();
        return new MasterSummaryResponse(
                master.getUser().getId(), displayName, master.getUser().getKarma(), master.getMasterType().name());
    }
}
