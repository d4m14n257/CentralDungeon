package com.centraldungeon.features.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.centraldungeon.features.approvals.ApprovalEntityResolver;
import com.centraldungeon.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

/**
 * The {@code {type}} segment of the two reservation routes, and the one thing it must not do: invent
 * a vocabulary.
 *
 * <p>The spelling is the one {@code approval_requests.entity_type} already uses and that
 * {@code Notification.relatedEntityType} publishes. A second vocabulary for the same things is how a
 * frontend ends up with two maps saying the same thing and a bug living between them - which is why
 * the shape is pinned here and not left to a reader to notice.
 */
class QueueSourceTest {

    /**
     * Singular of the table, snake case - the same shape {@code ApprovalEntityResolver.USER} follows
     * for {@code "user"}, and the same {@code "game_table"} the notifications already publish.
     */
    @Test
    void speaksTheSameVocabularyAsEntityType() {
        assertThat(QueueSource.APPROVAL_REQUEST.wireName()).isEqualTo("approval_request");
        assertThat(QueueSource.GAME_TABLE.wireName()).isEqualTo("game_table");
        assertThat(ApprovalEntityResolver.USER).isEqualTo("user");
    }

    @Test
    void itResolvesThePathSegmentInAnyCase() {
        assertThat(QueueSource.require("game_table")).isEqualTo(QueueSource.GAME_TABLE);
        assertThat(QueueSource.require("  APPROVAL_REQUEST  ")).isEqualTo(QueueSource.APPROVAL_REQUEST);
    }

    /**
     * <b>404 and not 400</b>: the segment is part of the path, not of a body.
     * {@code /admin/queue/anything/abc/claim} names a resource that does not exist, which is the same
     * answer any other id that is not there receives. A 400 would be telling the caller their request is
     * malformed when what they asked for simply does not exist.
     */
    @Test
    void aKindThatDoesNotExistIs404() {
        assertThatThrownBy(() -> QueueSource.require("comment"))
                .isInstanceOf(NotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("NOT_FOUND");
    }

    /**
     * Two live sources and not four: {@code comments} and {@code system_feedback} arrive later, with the
     * feature that produces them.
     */
    @Test
    void exactlyTwoSourcesExistNotFour() {
        assertThat(QueueSource.values())
                .containsExactly(QueueSource.APPROVAL_REQUEST, QueueSource.GAME_TABLE);
        assertThat(QueueSource.fromWireName("comment")).isEmpty();
        assertThat(QueueSource.fromWireName("system_feedback")).isEmpty();
    }
}
