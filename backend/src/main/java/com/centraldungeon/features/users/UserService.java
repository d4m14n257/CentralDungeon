package com.centraldungeon.features.users;

import com.centraldungeon.common.exception.ConflictException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.features.tables.MasterRepository;
import com.centraldungeon.features.tables.MasterRowStatus;
import com.centraldungeon.features.users.dto.UpdateUserRequest;
import com.centraldungeon.features.users.dto.UserDetailResponse;
import com.centraldungeon.features.users.dto.UserSummaryResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * People: their account, their global roles, and the snapshot the security filter reads on every
 * request.
 *
 * <p>{@link #loadAuthSnapshot} is the load-bearing method here. Roles and status are read from the
 * database on each request rather than lifted from the token's claims, because a JWT asserts
 * identity and not authorization (ADR-05 §3) - a degraded admin or a blocked account has to stop working
 * now, not when the token expires. The Caffeine cache in front of it (ADR-05 §3) is what keeps that from
 * being a query per call, and <b>its TTL is the revocation window</b>: any change to roles or status
 * has to call {@link #evictAuthCache} for the effect to be immediate.
 */
@Service
public class UserService {

    /** Fewest characters every value of a people search must have before it finds anybody. */
    static final int MIN_SEARCH_LENGTH = 2;

    /** Most people a people search ever returns, however large a page is asked for. */
    static final int MAX_SEARCH_RESULTS = 20;

    /** The only order a search is served in; the client's is ignored. */
    private static final Sort SEARCH_ORDER = Sort.by("discordUsername", "id");

    /** The {@code users} table. */
    private final UserRepository userRepository;

    /** Resolves the Player role granted on first login (ADR-05 §1). */
    private final RoleRepository roleRepository;

    /** Reads and grants the rows of {@code users_roles}. */
    private final UserRoleRepository userRoleRepository;

    /** Answers whether somebody runs any table - membership, not role (ADR-06 §4). */
    private final MasterRepository masterRepository;

    /** Entity to DTO. */
    private final UserMapper userMapper;

    /**
     * @param userRepository     the {@code users} table
     * @param roleRepository     resolves roles by name
     * @param userRoleRepository reads and grants role rows
     * @param masterRepository   answers whether somebody runs any table (ADR-06 §4)
     * @param userMapper         entity to DTO
     */
    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            MasterRepository masterRepository,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.masterRepository = masterRepository;
        this.userMapper = userMapper;
    }

    /** New users get the Player role (ADR-05 §1); existing ones just refresh their cached Discord username. */
    @Transactional
    public User findOrCreateByDiscordId(String discordId, String discordUsername) {
        return userRepository.findByDiscordId(discordId)
                .map(existing -> {
                    existing.setDiscordUsername(discordUsername);
                    return existing;
                })
                .orElseGet(() -> createWithPlayerRole(discordId, discordUsername));
    }

    private User createWithPlayerRole(String discordId, String discordUsername) {
        User user = userRepository.save(new User(discordId, discordUsername));
        Role playerRole = roleRepository.findByName(PlatformRole.PLAYER.roleName())
                .orElseThrow(() -> new IllegalStateException("Player role is missing - check V2__seed.sql"));
        userRoleRepository.save(new UserRole(user, playerRole));
        return user;
    }

    /**
     * Loads a person by id.
     *
     * @param userId the person
     * @return the entity
     * @throws NotFoundException if no user has that id
     */
    @Transactional(readOnly = true)
    public User getById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    /**
     * What {@code JwtAuthenticationFilter} builds the request's {@code Authentication} from: the
     * person's status and the roles they hold <em>right now</em> (ADR-05 §3).
     *
     * <p>Cached with Caffeine (ADR-05 §3) so this is not a query per request. The TTL is the revocation
     * window, not a performance number: it is the longest a stale answer can survive if somebody
     * forgets to evict.
     *
     * @param userId the person, from the token's subject
     * @return their status and live role names
     * @throws NotFoundException if the user no longer exists
     */
    @Cacheable(cacheNames = "userAuth", key = "#userId")
    @Transactional(readOnly = true)
    public UserAuthSnapshot loadAuthSnapshot(String userId) {
        User user = getById(userId);
        return new UserAuthSnapshot(user.getId(), user.getStatus(), userRoleRepository.findActiveRoleNames(userId));
    }

    /**
     * Drops one person's cached snapshot, so a change to their roles or status takes effect on the
     * very next request instead of at the end of the TTL.
     *
     * <p>Empty on purpose: the work is the annotation. Call it after blocking somebody or changing
     * their roles - the TTL is only the safety net for a path that forgets to.
     *
     * <p>Per JVM (ADR-05 §3, ADR-18 §2): with more than one instance running, this clears the local cache only.
     *
     * @param userId the person whose snapshot to drop
     */
    @CacheEvict(cacheNames = "userAuth", key = "#userId")
    public void evictAuthCache(String userId) {
    }

    /**
     * The caller's own profile - what the whole frontend shell is built from.
     *
     * <p>{@code hasManagedTables} covers the person who runs a single table without holding the
     * platform role (ADR-06 §4): the ContextSwitcher needs that signal to offer the Master
     * context even though {@code roles} does not include it. It is the clearest place the difference
     * between a role and membership shows up in the API.
     *
     * @param userId the actor, from the token
     * @return their profile, with roles and the membership flag resolved
     * @throws NotFoundException if the user does not exist
     */
    @Transactional(readOnly = true)
    public UserDetailResponse getDetailResponse(String userId) {
        User user = getById(userId);
        return userMapper.toDetailResponse(
                user,
                userRoleRepository.findActiveRoleNames(userId),
                masterRepository.existsByUser_IdAndStatus(userId, MasterRowStatus.Created));
    }

    /**
     * The search box behind every user picker, bounded so it cannot be used to dump the
     * directory: every value of the query must have at least {@value #MIN_SEARCH_LENGTH} characters, and
     * only the first page of at most {@value #MAX_SEARCH_RESULTS} people is ever served.
     *
     * <p>A query that is too short - or empty - is not an error but an empty page, the same way an unknown
     * role or field finds nobody: a search box has to survive being typed into. The page number of
     * {@code pageable} is ignored on purpose, or paging would walk the whole directory twenty at a time,
     * and so is its sort: ordering the same query ascending and then descending would show both ends of
     * the directory, so the order is fixed to the username, with the id to break ties.
     *
     * @param rawQuery what was typed, or null
     * @param pageable the requested size, which is capped; the sort is the server's own
     * @return up to {@value #MAX_SEARCH_RESULTS} Allowed people matching the query, none for a short one
     */
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> search(@Nullable String rawQuery, Pageable pageable) {
        SearchQuery query = SearchQueryParser.parse(rawQuery, UserSearchField.wireNames());
        Pageable bounded = PageRequest.of(0, Math.min(pageable.getPageSize(), MAX_SEARCH_RESULTS), SEARCH_ORDER);
        if (!isLongEnough(query)) {
            return PageResponse.from(Page.empty(bounded));
        }
        return PageResponse.from(
                userRepository.findAll(UserSearchSpecification.matching(query), bounded).map(userMapper::toSummaryResponse));
    }

    /** True when the query has a criterion and none of its values is a single character. */
    private static boolean isLongEnough(SearchQuery query) {
        return !query.isEmpty()
                && query.terms().stream()
                        .flatMap(term -> term.values().stream())
                        .allMatch(value -> value.length() >= MIN_SEARCH_LENGTH);
    }

    /**
     * Fills in the display name and country that onboarding blocks on (ADR-10 §4).
     *
     * <p>The actor comes from the token, never from the body: this can only ever edit the caller's
     * own profile (ADR-05 §5). It is not an edit: somebody who already completed onboarding is
     * refused, so the step cannot be repeated (see {@link #updateProfile} for the edit).
     *
     * @param userId  the actor, from the token
     * @param request the name and country they chose
     * @return their profile afterwards, with {@code needsOnboarding} now false
     * @throws NotFoundException if the user does not exist
     * @throws ConflictException with {@link ConflictException#ONBOARDING_ALREADY_COMPLETED} if they
     *                           had already completed onboarding
     */
    @Transactional
    public UserDetailResponse completeOnboarding(String userId, UpdateUserRequest request) {
        User user = getById(userId);
        if (user.hasCompletedOnboarding()) {
            throw new ConflictException(
                    "Onboarding already completed by user " + userId, ConflictException.ONBOARDING_ALREADY_COMPLETED);
        }
        return writeProfile(user, request);
    }

    /**
     * Edits the display name and country of somebody who already completed onboarding (ADR-10 §4).
     *
     * <p>Same actor rule as {@link #completeOnboarding}. It is a separate door so the first-time step
     * and the later edit cannot be confused: while onboarding is pending this is refused.
     *
     * @param userId  the actor, from the token
     * @param request the new name and country
     * @return their profile afterwards
     * @throws NotFoundException if the user does not exist
     * @throws ConflictException with {@link ConflictException#ONBOARDING_PENDING} if they have not
     *                           completed onboarding yet
     */
    @Transactional
    public UserDetailResponse updateProfile(String userId, UpdateUserRequest request) {
        User user = getById(userId);
        if (!user.hasCompletedOnboarding()) {
            throw new ConflictException(
                    "Onboarding still pending for user " + userId, ConflictException.ONBOARDING_PENDING);
        }
        return writeProfile(user, request);
    }

    private UserDetailResponse writeProfile(User user, UpdateUserRequest request) {
        user.setName(request.name());
        user.setCountry(request.country());
        return userMapper.toDetailResponse(
                user,
                userRoleRepository.findActiveRoleNames(user.getId()),
                masterRepository.existsByUser_IdAndStatus(user.getId(), MasterRowStatus.Created));
    }
}
