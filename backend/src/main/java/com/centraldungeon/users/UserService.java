package com.centraldungeon.users;

import com.centraldungeon.common.exception.NotFoundException;
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
    private final UserMapper userMapper;

    public UserService(
            UserRepository userRepository, RoleRepository roleRepository, UserRoleRepository userRoleRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
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

    @Transactional(readOnly = true)
    public UserDetailResponse getDetailResponse(String userId) {
        User user = getById(userId);
        return userMapper.toDetailResponse(user, userRoleRepository.findActiveRoleNames(userId));
    }

    @Transactional
    public UserDetailResponse completeOnboarding(String userId, UpdateUserRequest request) {
        User user = getById(userId);
        user.setName(request.name());
        user.setCountry(request.country());
        return userMapper.toDetailResponse(user, userRoleRepository.findActiveRoleNames(userId));
    }
}
