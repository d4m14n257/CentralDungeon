package com.centraldungeon.tables;

import static org.assertj.core.api.Assertions.assertThat;

import com.centraldungeon.tables.dto.TableStatusChangeResponse;
import com.centraldungeon.users.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

/** The one mapping here that is hand-written rather than generated. */
class GameTableMapperTest {

    private final GameTableMapper mapper = Mappers.getMapper(GameTableMapper.class);

    /** The History tab opens the author's card, so the id travels with the name. */
    @Test
    void aStatusChangeCarriesWhoMadeItAsAnIdAndAName() {
        User author = new User("discord-1", "Ana");
        ReflectionTestUtils.setField(author, "id", "user-1");
        GameTable table = new GameTable("Mesa", author);
        TableStatusChange change = new TableStatusChange(table, GameTableStatus.Opened, GameTableStatus.Pause, author, "break");
        ReflectionTestUtils.setField(change, "id", "change-1");
        ReflectionTestUtils.setField(change, "createdAt", LocalDateTime.now());

        TableStatusChangeResponse response = mapper.toStatusChangeResponse(change);

        assertThat(response.changedById()).isEqualTo("user-1");
        assertThat(response.changedByName()).isEqualTo("Ana");
    }
}
