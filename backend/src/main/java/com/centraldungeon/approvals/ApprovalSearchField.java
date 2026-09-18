package com.centraldungeon.approvals;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fields the admin request search accepts behind a {@code /prefix} (decisiones.md #164). The
 * wire name is what the person types and what the chip shows.
 *
 * <p>Typing nothing in particular searches the <b>justification and the requester's name at once</b>.
 * That pairing is the point of the screen: an admin is either looking for a person they spoke to or
 * for a phrase they remember reading, and they should not have to say which before typing.
 *
 * <p>Two of the three take fixed options - {@code /request_type} and {@code /status} - which is what
 * lets the frontend offer them rather than making somebody remember the spelling. The third is free
 * text. None of them ever answers 400: an unrecognized value matches nothing (arquitectura.md 2.5).
 */
public enum ApprovalSearchField {

    /** Which kind of request, matched whole: {@code MasterGrant}, {@code TableOpen}, {@code General}. */
    REQUEST_TYPE("request_type"),

    /** Where the request is, matched whole: {@code Pending}, {@code Approved}, {@code Rejected}. */
    STATUS("status"),

    /** Who asked, by display name or by Discord handle. Substring, case-insensitive. */
    REQUESTED_BY("requested_by");

    private final String wireName;

    ApprovalSearchField(String wireName) {
        this.wireName = wireName;
    }

    /**
     * Returns what the person types after the slash.
     *
     * @return the wire name, lowercase
     */
    public String wireName() {
        return wireName;
    }

    /**
     * The set the parser needs in order to tell a {@code /field} from literal text.
     *
     * @return every wire name this search accepts
     */
    public static Set<String> wireNames() {
        return Arrays.stream(values()).map(ApprovalSearchField::wireName).collect(Collectors.toUnmodifiableSet());
    }

    static Optional<ApprovalSearchField> fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(field -> field.wireName.equals(normalized)).findFirst();
    }
}
