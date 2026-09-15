package com.centraldungeon.users;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.users.dto.AdminUserDetailResponse;
import com.centraldungeon.users.dto.AdminUserSummaryResponse;
import com.centraldungeon.users.dto.UserAdminChangeResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * What {@code /admin/users} is: finding a person, blocking them, letting them back in, and reading
 * what was done to them. Role changes are next door in {@link UserRoleService}, because they are the
 * one thing on this screen an admin cannot always do.
 *
 * <p>Two rules carry most of the weight here:
 *
 * <ul>
 *   <li><b>Nobody with Admin or Owner can be blocked</b> - not by an admin, not by an owner, not by
 *       themselves (fase-3-admin-owner.md 3). Among peers there is no authority, and a block is
 *       irreversible from the blocked side: they cannot log in to ask about it (#84). Stated as one
 *       rule about the target rather than as a comparison between actor and target, it also covers
 *       blocking yourself for free - the actor of this screen always holds one of the two.</li>
 *   <li><b>A block evicts the auth cache</b> (#128). A block that takes sixty seconds to apply is a
 *       block that does not block, and the blocked person spends that minute inside.</li>
 * </ul>
 *
 * <p>A block <b>keeps everything</b> (#84): no table, no application and no history is removed. It
 * is a door that stops opening, not a deletion.
 */
@Service
public class AdminUserService {

    /** The {@code users} table, queried by specification for the search box. */
    private final UserRepository userRepository;

    /** Reads the live grants, in bulk for a page and one by one for a check. */
    private final UserRoleRepository userRoleRepository;

    /** The audit trail of role changes, read for the history panel. */
    private final UserRoleChangeRepository userRoleChangeRepository;

    /** The audit trail of blocks, written here and read for the history panel. */
    private final UserStatusChangeRepository userStatusChangeRepository;

    /** Loads people and drops their cached authorization snapshot. */
    private final UserService userService;

    /** Entity to DTO. */
    private final UserMapper userMapper;

    /**
     * @param userRepository             the {@code users} table
     * @param userRoleRepository         reads live grants
     * @param userRoleChangeRepository   the role-change trail
     * @param userStatusChangeRepository the status-change trail
     * @param userService                loads people and evicts their auth snapshot
     * @param userMapper                 entity to DTO
     */
    public AdminUserService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            UserRoleChangeRepository userRoleChangeRepository,
            UserStatusChangeRepository userStatusChangeRepository,
            UserService userService,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRoleChangeRepository = userRoleChangeRepository;
        this.userStatusChangeRepository = userStatusChangeRepository;
        this.userService = userService;
        this.userMapper = userMapper;
    }

    /**
     * The listing behind {@code /admin/users}.
     *
     * <p>Unlike {@code UserService.search}, this one <b>sees blocked and deleted accounts</b>: an
     * admin who cannot find the person they blocked cannot unblock them. The picker's search keeps
     * its {@code status = Allowed} filter, which is not negotiable there and is fixed by its own
     * test - the two share {@code UserSearchSpecification} and differ only in that rule.
     *
     * @param rawQuery the search box: bare text matches either name, and {@code /discord_name},
     *                 {@code /user_name}, {@code /role} and {@code /status} narrow it. An
     *                 unrecognized value matches nothing and is never a 400
     * @param pageable page, size and sort, with a tie-break by id (#171)
     * @return one page of people, each with their roles resolved in a single extra query
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> search(@Nullable String rawQuery, Pageable pageable) {
        SearchQuery query = SearchQueryParser.parse(rawQuery, UserSearchField.adminWireNames());
        Page<User> page = userRepository.findAll(UserSearchSpecification.forAdmin(query), pageable);
        Map<String, List<String>> rolesByUser = rolesOf(page.getContent());
        return PageResponse.from(page.map(user ->
                userMapper.toAdminSummaryResponse(user, rolesByUser.getOrDefault(user.getId(), List.of()))));
    }

    /**
     * One person, as the detail dialog shows them.
     *
     * @param targetId the person
     * @return their detail
     * @throws NotFoundException 404 when nobody has that id
     */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getDetail(String targetId) {
        User target = userService.getById(targetId);
        return userMapper.toAdminDetailResponse(target, userRoleRepository.findActiveRoleNames(targetId));
    }

    /**
     * Blocks an account (#84). The person stops being able to log in; everything they made stays.
     *
     * @param targetId      the account to block
     * @param justification why. Required: the blocked person cannot log in to ask
     * @param actor         the actor, from the token (#121)
     * @return the account afterwards
     * @throws NotFoundException        404 when nobody has that id
     * @throws ForbiddenActionException 403 {@code CANNOT_BLOCK_PRIVILEGED} when the target holds
     *                                  Admin or Owner - which includes the actor themselves
     * @throws ConflictException        409 {@code USER_ALREADY_BLOCKED} when the account is not
     *                                  Allowed. A Deleted account answers this too: F3.1 does not
     *                                  move one anywhere
     */
    @Transactional
    public AdminUserDetailResponse block(String targetId, String justification, CurrentUser actor) {
        User target = userService.getById(targetId);
        Set<String> roles = userRoleRepository.findActiveRoleNames(targetId);
        requireNotPrivileged(target, roles);

        if (target.getStatus() != UserStatus.Allowed) {
            throw new ConflictException(
                    "User " + targetId + " is " + target.getStatus() + " and cannot be blocked",
                    ConflictException.USER_ALREADY_BLOCKED);
        }
        return recordStatusChange(target, UserStatus.Blocked, justification, actor, roles);
    }

    /**
     * Lets a blocked account back in.
     *
     * @param targetId      the account to unblock
     * @param justification why, so the history reads as a conversation and not as a silent reversal
     * @param actor         the actor, from the token (#121)
     * @return the account afterwards
     * @throws NotFoundException 404 when nobody has that id
     * @throws ConflictException 409 {@code USER_NOT_BLOCKED} when the account is not Blocked. A
     *                           Deleted account answers this: unblocking is not undeleting
     */
    @Transactional
    public AdminUserDetailResponse unblock(String targetId, String justification, CurrentUser actor) {
        User target = userService.getById(targetId);

        if (target.getStatus() != UserStatus.Blocked) {
            throw new ConflictException(
                    "User " + targetId + " is " + target.getStatus() + " and is not blocked",
                    ConflictException.USER_NOT_BLOCKED);
        }
        return recordStatusChange(
                target, UserStatus.Allowed, justification, actor, userRoleRepository.findActiveRoleNames(targetId));
    }

    /**
     * Everything that was ever done to one person's account, as one timeline, oldest first - the
     * same order {@code GameTableService.getStatusHistory} reads a table's history in.
     *
     * <p>It is what keeps the two audit tables from being write-only. Without it they would be born
     * orphaned, which is the failure fase-3-admin-owner.md 7 names.
     *
     * <p>Not paginated: an account's administrative history is a handful of rows read as a single
     * sequence, not a collection somebody scrolls.
     *
     * @param targetId the person
     * @return their role changes and status changes merged and ordered by time
     * @throws NotFoundException 404 when nobody has that id - an empty history and a person who does
     *                           not exist are different answers
     */
    @Transactional(readOnly = true)
    public List<UserAdminChangeResponse> history(String targetId) {
        userService.getById(targetId);
        List<UserAdminChangeResponse> entries = new ArrayList<>();
        userRoleChangeRepository.findByUser_IdOrderByCreatedAtAsc(targetId).stream()
                .map(userMapper::toAdminChangeResponse)
                .forEach(entries::add);
        userStatusChangeRepository.findByUser_IdOrderByCreatedAtAsc(targetId).stream()
                .map(userMapper::toAdminChangeResponse)
                .forEach(entries::add);
        entries.sort(Comparator.comparing(UserAdminChangeResponse::createdAt).thenComparing(UserAdminChangeResponse::id));
        return List.copyOf(entries);
    }

    /**
     * The one rule that makes an admin panel safe to hand out: an account holding Admin or Owner is
     * not blockable by anybody (fase-3-admin-owner.md 3).
     */
    private void requireNotPrivileged(User target, Set<String> roles) {
        if (roles.contains(PlatformRole.ADMIN.roleName()) || roles.contains(PlatformRole.OWNER.roleName())) {
            throw new ForbiddenActionException(
                    "User " + target.getId() + " holds Admin or Owner and cannot be blocked",
                    ForbiddenActionException.CANNOT_BLOCK_PRIVILEGED);
        }
    }

    /** The one place the status moves, so no transition can be added without leaving its trail. */
    private AdminUserDetailResponse recordStatusChange(
            User target, UserStatus to, String justification, CurrentUser actor, Set<String> roles) {
        UserStatus from = target.getStatus();
        target.setStatus(to);
        userStatusChangeRepository.save(
                new UserStatusChange(target, from, to, userService.getById(actor.userId()), justification));
        evictAuthCacheAfterCommit(target.getId());
        return userMapper.toAdminDetailResponse(target, roles);
    }

    /**
     * Drops the target's cached snapshot - <b>once the change is actually committed</b>.
     *
     * <p>The timing is the rule, not the call. Evicting inside the transaction opens a window
     * between the eviction and the commit: a concurrent request misses the cache, reads the rows as
     * they were <em>before</em> the commit, and repopulates {@code userAuth} with the answer the
     * change was meant to replace - where it then survives the full TTL. That is the exact hole
     * #128 exists to close, so the eviction waits for {@code afterCommit}.
     *
     * <p>Called without a transaction - a unit test, or a future caller that forgets one - it
     * evicts immediately, which is the best that can be done when there is no commit to wait for.
     */
    private void evictAuthCacheAfterCommit(String userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            userService.evictAuthCache(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                userService.evictAuthCache(userId);
            }
        });
    }

    /** One query for a whole page: twenty people would otherwise cost twenty lookups. */
    private Map<String, List<String>> rolesOf(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<String> ids = users.stream().map(User::getId).toList();
        return userRoleRepository.findActiveGrantsByUsers(ids).stream()
                .collect(Collectors.groupingBy(
                        UserRoleGrant::userId, Collectors.mapping(UserRoleGrant::roleName, Collectors.toList())));
    }
}
