package com.centraldungeon.users;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.users.dto.UpdateUserRequest;
import com.centraldungeon.users.dto.UserDetailResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final MasterRepository masterRepository;
    private final UserMapper userMapper;

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

    @Transactional(readOnly = true)
    public User getById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    @Cacheable(cacheNames = "userAuth", key = "#userId")
    @Transactional(readOnly = true)
    public UserAuthSnapshot loadAuthSnapshot(String userId) {
        User user = getById(userId);
        return new UserAuthSnapshot(user.getId(), user.getStatus(), userRoleRepository.findActiveRoleNames(userId));
    }

    @CacheEvict(cacheNames = "userAuth", key = "#userId")
    public void evictAuthCache(String userId) {
    }

    /**
     * hasManagedTables cubre al master de una sola mesa que no tiene el rol de plataforma
     * (decisiones.md #135): el ContextSwitcher necesita esta señal para ofrecer el contexto
     * Master aunque `roles` no lo incluya.
     */
    @Transactional(readOnly = true)
    public UserDetailResponse getDetailResponse(String userId) {
        User user = getById(userId);
        return userMapper.toDetailResponse(user, userRoleRepository.findActiveRoleNames(userId), masterRepository.existsByUser_Id(userId));
    }

    @Transactional
    public UserDetailResponse completeOnboarding(String userId, UpdateUserRequest request) {
        User user = getById(userId);
        user.setName(request.name());
        user.setCountry(request.country());
        return userMapper.toDetailResponse(user, userRoleRepository.findActiveRoleNames(userId), masterRepository.existsByUser_Id(userId));
    }
}
