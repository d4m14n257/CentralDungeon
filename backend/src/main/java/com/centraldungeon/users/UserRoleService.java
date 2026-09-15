package com.centraldungeon.users;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.security.CurrentUser;
import com.centraldungeon.users.dto.AdminUserDetailResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Who holds which global role, and who is allowed to change that.
 *
 * <p><b>The granting is the rule, not the door</b> (fase-3-admin-owner.md 4). The controller's
 * {@code @PreAuthorize} says {@code hasAnyRole('ADMIN','OWNER')} and stops there, because the line
 * between the two is not a role check: an admin may move Player and Master and nothing else, and
 * that sentence cannot be written in an annotation without either a {@code RoleHierarchy} - which
 * this project refuses (#37, #89, #123) - or a second endpoint that does the same thing with a
 * different guard. So it lives here, once, and every path into a role change goes through it. When
 * F3.2 approves a "please make me a master" request, it calls this service; it does not grow a
 * second way to write the same row.
 *
 * <p>Four rules are worth naming because none of them is obvious from the table:
 *
 * <ul>
 *   <li><b>Admin and Owner never coexist</b> (#169). Granting one revokes the other, in the same
 *       transaction, and the revocation gets an audit row of its own - burying it inside the grant
 *       would lose exactly the event somebody would go looking for.</li>
 *   <li><b>The platform is never left without an Owner.</b> The only global invariant of roles, and
 *       MySQL cannot express it, so it is checked here - <em>before</em> anything is written, so a
 *       refused operation leaves no half of itself behind.</li>
 *   <li><b>Restoring a revoked role flips a row, never inserts one</b> (#25). The key of
 *       {@code users_roles} is {@code (user_id, role_id)}: there is only ever one row per pair, and
 *       {@link UserRoleRepository#findAllGrants} exists for this exact write.</li>
 *   <li><b>Every change evicts the auth cache</b> (#128). The TTL is the revocation window, and a
 *       demotion that takes a minute to apply is a demotion that did not happen yet.</li>
 * </ul>
 */
@Service
public class UserRoleService {

    /** Loads people and drops their cached authorization snapshot. */
    private final UserService userService;

    /** Resolves the four seeded roles by name. */
    private final RoleRepository roleRepository;

    /** Reads and writes the rows of {@code users_roles}. */
    private final UserRoleRepository userRoleRepository;

    /** The audit trail: one row per role given or taken away. */
    private final UserRoleChangeRepository userRoleChangeRepository;

    /** Entity to DTO. */
    private final UserMapper userMapper;

    /**
     * @param userService              loads people and evicts their auth snapshot
     * @param roleRepository           resolves roles by name
     * @param userRoleRepository       reads and writes grants
     * @param userRoleChangeRepository writes the audit trail
     * @param userMapper               entity to DTO
     */
    public UserRoleService(
            UserService userService,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserRoleChangeRepository userRoleChangeRepository,
            UserMapper userMapper) {
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRoleChangeRepository = userRoleChangeRepository;
        this.userMapper = userMapper;
    }

    /**
     * Gives somebody a role.
     *
     * <p>Idempotent: granting a role the person already holds changes nothing, writes no audit row
     * and answers 200. A screen that fires the same click twice must not produce two entries in a
     * history that is supposed to read as a record of what happened.
     *
     * @param targetId      the person the role is given to
     * @param role          the role to grant
     * @param justification why, for the audit row. Never blank - the request validates it
     * @param actor         the actor, from the token (#121)
     * @return the person afterwards, so the screen does not have to re-fetch
     * @throws ForbiddenActionException 403 {@code ROLE_GRANT_FORBIDDEN} when an admin reaches for
     *                                  Admin or Owner
     * @throws NotFoundException        404 when nobody has that id
     * @throws ConflictException        409 {@code LAST_OWNER} when the exclusion of #169 would take
     *                                  Owner away from the last one left
     */
    @Transactional
    public AdminUserDetailResponse grantRole(String targetId, PlatformRole role, String justification, CurrentUser actor) {
        requireRankAuthority(role, actor);
        User target = userService.getById(targetId);
        List<UserRole> grants = new ArrayList<>(userRoleRepository.findAllGrants(targetId));

        Optional<UserRole> existing = grantOf(grants, role);
        boolean alreadyHeld = existing.filter(UserRoleService::isLive).isPresent();

        PlatformRole counterpart = exclusiveCounterpartOf(role);
        Optional<UserRole> heldCounterpart = counterpart == null
                ? Optional.empty()
                : grantOf(grants, counterpart).filter(UserRoleService::isLive);

        if (alreadyHeld && heldCounterpart.isEmpty()) {
            return userMapper.toAdminDetailResponse(target, activeRoleNames(grants));
        }

        // Checked before the first write: the operation is refused whole, never half-applied.
        if (heldCounterpart.isPresent() && counterpart == PlatformRole.OWNER && isLastActiveOwner(target)) {
            throw new ConflictException(
                    "Granting Admin to " + targetId + " would take Owner away from the last active owner",
                    ConflictException.LAST_OWNER);
        }

        User changedBy = userService.getById(actor.userId());

        heldCounterpart.ifPresent(counterpartGrant -> {
            counterpartGrant.revoke();
            userRoleRepository.save(counterpartGrant);
            userRoleChangeRepository.save(new UserRoleChange(
                    target, counterpartGrant.getRole(), UserRoleChangeAction.Revoked, changedBy, justification));
        });

        if (!alreadyHeld) {
            UserRole grant = existing.orElseGet(() -> {
                UserRole created = new UserRole(target, roleEntity(role));
                grants.add(created);
                return created;
            });
            grant.restore();
            userRoleRepository.save(grant);
            userRoleChangeRepository.save(
                    new UserRoleChange(target, grant.getRole(), UserRoleChangeAction.Granted, changedBy, justification));
        }

        evictAuthCacheAfterCommit(targetId);
        return userMapper.toAdminDetailResponse(target, activeRoleNames(grants));
    }

    /**
     * Takes a role away. The row of {@code users_roles} stays and its status flips: the record of
     * who once held what is worth keeping (#25).
     *
     * <p>Idempotent, like {@link #grantRole}: revoking a role that was already revoked, or that the
     * person never held, is a no-op with no audit row and a 200.
     *
     * @param targetId      the person the role is taken from
     * @param role          the role to revoke
     * @param justification why, for the audit row
     * @param actor         the actor, from the token (#121)
     * @return the person afterwards
     * @throws ForbiddenActionException 403 {@code ROLE_GRANT_FORBIDDEN} when an admin reaches for
     *                                  Admin or Owner
     * @throws NotFoundException        404 when nobody has that id
     * @throws ConflictException        409 {@code CANNOT_REVOKE_OWN_OWNER} when an owner tries to
     *                                  step down, or {@code LAST_OWNER} when they are the last one
     * @implNote The idempotence check reads the target's grants before the Owner lock is taken, so
     *           two simultaneous revocations of Owner <em>from the same person</em> answer 200 and
     *           409 {@code LAST_OWNER} rather than 200 and 200. Left as is: the 409 is a true
     *           statement about the platform if not about the click, the invariant is untouched, and
     *           the alternative is a second freshly-locked read of one user's grants on a path where
     *           extra branching is the likelier source of a real bug.
     */
    @Transactional
    public AdminUserDetailResponse revokeRole(String targetId, PlatformRole role, String justification, CurrentUser actor) {
        requireRankAuthority(role, actor);
        User target = userService.getById(targetId);

        // The more specific of the two conflicts goes first: "you cannot demote yourself" and "there
        // has to be an owner" are different sentences even when the same person triggers both.
        if (role == PlatformRole.OWNER && targetId.equals(actor.userId())) {
            throw new ConflictException(
                    "An Owner cannot revoke their own Owner role", ConflictException.CANNOT_REVOKE_OWN_OWNER);
        }

        List<UserRole> grants = new ArrayList<>(userRoleRepository.findAllGrants(targetId));
        Optional<UserRole> live = grantOf(grants, role).filter(UserRoleService::isLive);
        if (live.isEmpty()) {
            return userMapper.toAdminDetailResponse(target, activeRoleNames(grants));
        }

        if (role == PlatformRole.OWNER && isLastActiveOwner(target)) {
            throw new ConflictException(
                    "Revoking Owner from " + targetId + " would leave the platform without an active owner",
                    ConflictException.LAST_OWNER);
        }

        UserRole grant = live.get();
        grant.revoke();
        userRoleRepository.save(grant);
        userRoleChangeRepository.save(new UserRoleChange(
                target, grant.getRole(), UserRoleChangeAction.Revoked, userService.getById(actor.userId()), justification));

        evictAuthCacheAfterCommit(targetId);
        return userMapper.toAdminDetailResponse(target, activeRoleNames(grants));
    }

    /**
     * Who may move which rank. An admin moves Player and Master; Admin and Owner are an Owner's to
     * give and to take (fase-3-admin-owner.md 3).
     *
     * <p>403 and not 400 on purpose: the request is perfectly well formed, and what makes it fail is
     * who sent it.
     */
    private void requireRankAuthority(PlatformRole role, CurrentUser actor) {
        boolean isRank = role == PlatformRole.ADMIN || role == PlatformRole.OWNER;
        if (isRank && !actor.hasRole(PlatformRole.OWNER.roleName())) {
            throw new ForbiddenActionException(
                    "Only an Owner can grant or revoke the " + role.roleName() + " role",
                    ForbiddenActionException.ROLE_GRANT_FORBIDDEN);
        }
    }

    /** Admin and Owner are the same rank at two scopes, so holding one excludes the other (#169). */
    private static @Nullable PlatformRole exclusiveCounterpartOf(PlatformRole role) {
        return switch (role) {
            case ADMIN -> PlatformRole.OWNER;
            case OWNER -> PlatformRole.ADMIN;
            case PLAYER, MASTER -> null;
        };
    }

    /**
     * True when taking Owner away from this person would leave nobody able to grant it back.
     *
     * <p>Somebody whose account is not Allowed is not one of the owners the count is about: they
     * cannot log in to grant the role to anyone, so taking it from them changes nothing the
     * invariant protects. Without this guard, a platform with one live owner and one deleted one
     * would refuse to tidy up the deleted one's role with a 409 that protects nothing.
     *
     * <p><b>Two locks, in this order, and the order is the whole design.</b> This used to be an
     * optimistic count, and it was wrong in the way concurrency is usually wrong: perfectly correct
     * every time it was read and broken every time it was raced. Two owners revoking each other at
     * the same moment each saw the other still live, each concluded somebody would remain, and the
     * platform ended with nobody who could grant Owner back - a state with no way in from outside,
     * which is the entire reason the rule exists. The same hole opened from the other side, through
     * promotions to Admin, because the exclusion of #169 revokes Owner as a side effect.
     *
     * <ol>
     *   <li>{@link RoleRepository#lockByName} on the Owner row: a single, always-present row that
     *       every path able to remove an Owner takes first. One row means no lock cycle is even
     *       expressible, and it covers the case the second lock cannot - when there are no grants
     *       left to lock at all.</li>
     *   <li>{@link UserRoleRepository#lockActiveHolders}: the count itself, as a locking read. Not
     *       an optimisation of step 1 but the other half of it - the lock serialises, and only a
     *       locking read is fresh. A plain count here would answer from the snapshot this
     *       transaction opened before it started waiting, and cheerfully count an owner that the
     *       transaction it just waited for has already demoted.</li>
     * </ol>
     *
     * <p>Every path that can remove an Owner comes through here before it writes anything - the
     * direct revocation and the grant of Admin whose exclusion revokes Owner - so the check and the
     * write are inside the same held lock. Granting Player or Master never reaches this method, so
     * the ordinary case pays nothing.
     */
    private boolean isLastActiveOwner(User target) {
        if (target.getStatus() != UserStatus.Allowed) {
            return false;
        }
        roleRepository.lockByName(PlatformRole.OWNER.roleName())
                .orElseThrow(() -> new IllegalStateException("Owner role is missing - check V2__seed.sql"));
        return userRoleRepository.lockActiveHolders(PlatformRole.OWNER.roleName()).size() <= 1;
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

    private static Optional<UserRole> grantOf(Collection<UserRole> grants, PlatformRole role) {
        return grants.stream().filter(grant -> grant.getRole().getName().equals(role.roleName())).findFirst();
    }

    private static boolean isLive(UserRole grant) {
        return grant.getStatus() == UserRoleStatus.Allowed;
    }

    private static List<String> activeRoleNames(Collection<UserRole> grants) {
        return grants.stream().filter(UserRoleService::isLive).map(grant -> grant.getRole().getName()).toList();
    }

    private Role roleEntity(PlatformRole role) {
        return roleRepository.findByName(role.roleName())
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + role.roleName() + " is missing - check V2__seed.sql"));
    }
}
