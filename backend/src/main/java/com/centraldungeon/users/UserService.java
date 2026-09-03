package com.centraldungeon.users;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.model.PageResponse;
import com.centraldungeon.common.search.SearchQuery;
import com.centraldungeon.common.search.SearchQueryParser;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.users.dto.UpdateUserRequest;
import com.centraldungeon.users.dto.UserDetailResponse;
import com.centraldungeon.users.dto.UserSummaryResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * People: their account, their global roles, and the snapshot the security filter reads on every
 * request.
 *
 * <p>{@link #loadAuthSnapshot} is the load-bearing method here. Roles and status are read from the
 * database on each request rather than lifted from the token's claims, because a JWT asserts
 * identity and not authorization (#122) - a degraded admin or a blocked account has to stop working
 * now, not when the token expires. The Caffeine cache in front of it (#128) is what keeps that from
 * being a query per call, and <b>its TTL is the revocation window</b>: any change to roles or status
 * has to call {@link #evictAuthCache} for the effect to be immediate.
 */
@Service
public class UserService {

    /** The {@code users} table. */
    private final UserRepository userRepository;

    /** Resolves the Player role granted on first login (#38). */
    private final RoleRepository roleRepository;

    /** Reads and grants the rows of {@code users_roles}. */
    private final UserRoleRepository userRoleRepository;

    /** Answers whether somebody runs any table - membership, not role (#135). */
    private final MasterRepository masterRepository;

    /** Entity to DTO. */
    private final UserMapper userMapper;

    /**
     * @param userRepository     the {@code users} table
     * @param roleRepository     resolves roles by name
     * @param userRoleRepository reads and grants role rows
     * @param masterRepository   answers whether somebody runs any table (#135)
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

    /** New users get the Player role (#38); existing ones just refresh their cached Discord username. */
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
     * person's status and the roles they hold <em>right now</em> (#122).
     *
     * <p>Cached with Caffeine (#128) so this is not a query per request. The TTL is the revocation
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
     * <p>Per JVM (#128, #101): with more than one instance running, this clears the local cache only.
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
     * platform role (decisiones.md #135): the ContextSwitcher needs that signal to offer the Master
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
        return userMapper.toDetailResponse(user, userRoleRepository.findActiveRoleNames(userId), masterRepository.existsByUser_Id(userId));
    }

    /**
     * The search box behind every user picker (decisiones.md #164). An empty query is not an error:
     * it lists Allowed users page by page, which is what /admin/users will want to open with.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> search(@Nullable String rawQuery, Pageable pageable) {
        SearchQuery query = SearchQueryParser.parse(rawQuery, UserSearchField.wireNames());
        return PageResponse.from(
                userRepository.findAll(UserSearchSpecification.matching(query), pageable).map(userMapper::toSummaryResponse));
    }

    /**
     * Fills in the display name and country that onboarding blocks on (#134).
     *
     * <p>The actor comes from the token, never from the body: this endpoint can only ever edit the
     * caller's own profile (#121).
     *
     * @param userId  the actor, from the token
     * @param request the name and country they chose
     * @return their profile afterwards, with {@code needsOnboarding} now false
     * @throws NotFoundException if the user does not exist
     */
    @Transactional
    public UserDetailResponse completeOnboarding(String userId, UpdateUserRequest request) {
        User user = getById(userId);
        user.setName(request.name());
        user.setCountry(request.country());
        return userMapper.toDetailResponse(user, userRoleRepository.findActiveRoleNames(userId), masterRepository.existsByUser_Id(userId));
    }
}
