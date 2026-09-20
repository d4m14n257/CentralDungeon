package com.centraldungeon.adminqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.adminqueue.dto.AdminQueueItemResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * How {@link AdminQueueItemKind} crosses HTTP, in both directions.
 *
 * <p>This test exists because of #253, which this phase has already paid for twice.
 * {@code PlatformRole} crossed HTTP without {@code @JsonValue}: every response published
 * {@code "Player"} - built by hand in a mapper - and every request carrying that same value was
 * rejected with a 400. No service test could see it, because a service test calls the method with the
 * enum already in hand, and the frontend's tests mock the fetch. The only thing that catches it is
 * asking the mapper, which is what this does.
 *
 * <p>The constants already coincide with their wire names, so Jackson's default would produce the
 * same strings today. That is precisely why the pin is worth having: it is the day somebody renames a
 * constant, or F5 adds a kind whose wire name differs, that the two would drift - and the drift is
 * silent in exactly one direction.
 *
 * <p>Jackson 3 ({@code tools.jackson.*}), like the rest of the application.
 */
class AdminQueueItemKindJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void itSerializesWithTheWireNameAndNotTheConstant() {
        assertThat(json.writeValueAsString(AdminQueueItemKind.ApprovalRequest)).isEqualTo("\"ApprovalRequest\"");
        assertThat(json.writeValueAsString(AdminQueueItemKind.TableWaitingReview))
                .isEqualTo("\"TableWaitingReview\"");
    }

    /** What the API publishes is what the API accepts - otherwise the frontend cannot hand back what it read. */
    @Test
    void itDeserializesTheSameValueTheResponsePublishes() {
        for (AdminQueueItemKind kind : AdminQueueItemKind.values()) {
            String published = json.writeValueAsString(kind).replace("\"", "");

            assertThat(json.readValue("\"" + published + "\"", AdminQueueItemKind.class)).isEqualTo(kind);
        }
    }

    /**
     * The {@code kind} travels as a string inside the item, and the string is the one the enum publishes.
     * It is the half that breaks without anybody seeing it: the service builds the DTO by hand, so if the
     * enum changed its spelling the listing would still compile and the frontend would stop recognising it.
     */
    @Test
    void theItemPublishesTheSameStringAsTheEnum() {
        AdminQueueItemResponse item = new AdminQueueItemResponse(
                AdminQueueSource.GAME_TABLE.wireName(),
                "table-1",
                AdminQueueItemKind.TableWaitingReview.wireName(),
                "La Cripta",
                "ana",
                null,
                LocalDateTime.parse("2026-09-01T10:00"),
                null,
                null);

        String serialized = json.writeValueAsString(item);

        assertThat(serialized).contains("\"kind\":\"TableWaitingReview\"");
        assertThat(serialized).contains("\"type\":\"game_table\"");
        assertThat(json.readValue("\"" + item.kind() + "\"", AdminQueueItemKind.class))
                .isEqualTo(AdminQueueItemKind.TableWaitingReview);
    }

    /**
     * <b>Two values and not four</b>, and that is a decision and not an oversight. {@code modelo-datos.md}
     * §5 lists four sources: the two here, plus {@code comments} in {@code Under review} and
     * {@code system_feedback} in {@code New} - and those two arrive in F5 <em>with the feature that
     * produces them</em>. An enum value nothing emits is the orphan this phase came to close.
     */
    @Test
    void entranDosClasesYNoCuatro() {
        assertThat(AdminQueueItemKind.values())
                .containsExactly(AdminQueueItemKind.ApprovalRequest, AdminQueueItemKind.TableWaitingReview);

        assertThatThrownBy(() -> json.readValue("\"CommentUnderReview\"", AdminQueueItemKind.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> json.readValue("\"SystemFeedback\"", AdminQueueItemKind.class))
                .isInstanceOf(Exception.class);
    }

    /**
     * The two doors, side by side: the JSON body is exact and the search by name forgives. They differ on
     * purpose, the same way they do in {@code ApprovalRequestType}.
     */
    @Test
    void theBodyIsExactAndTheSearchByNameForgives() {
        assertThatThrownBy(() -> json.readValue("\"approvalrequest\"", AdminQueueItemKind.class))
                .isInstanceOf(Exception.class);

        assertThat(AdminQueueItemKind.fromWireName("approvalrequest"))
                .contains(AdminQueueItemKind.ApprovalRequest);
        assertThat(AdminQueueItemKind.fromWireName("  TABLEWAITINGREVIEW  "))
                .contains(AdminQueueItemKind.TableWaitingReview);
        assertThat(AdminQueueItemKind.fromWireName("Whatever")).isEmpty();
    }
}
