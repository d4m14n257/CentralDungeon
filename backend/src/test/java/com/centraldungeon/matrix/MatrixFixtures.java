package com.centraldungeon.matrix;

import com.centraldungeon.approvals.ApprovalRequest;
import com.centraldungeon.approvals.ApprovalRequestRepository;
import com.centraldungeon.approvals.ApprovalRequestType;
import com.centraldungeon.catalogs.CatalogStatus;
import com.centraldungeon.catalogs.CatalogType;
import com.centraldungeon.catalogs.CatalogValue;
import com.centraldungeon.catalogs.GameSystem;
import com.centraldungeon.catalogs.Platform;
import com.centraldungeon.catalogs.PlatformRepository;
import com.centraldungeon.catalogs.SystemRepository;
import com.centraldungeon.catalogs.TablePlatform;
import com.centraldungeon.catalogs.TablePlatformRepository;
import com.centraldungeon.catalogs.TableSystem;
import com.centraldungeon.catalogs.TableSystemRepository;
import com.centraldungeon.catalogs.TableTag;
import com.centraldungeon.catalogs.TableTagRepository;
import com.centraldungeon.catalogs.Tag;
import com.centraldungeon.catalogs.TagRepository;
import com.centraldungeon.common.model.IdGenerator;
import com.centraldungeon.common.security.JwtService;
import com.centraldungeon.common.security.RefreshCookieFactory;
import com.centraldungeon.common.storage.StorageService;
import com.centraldungeon.files.FileCategory;
import com.centraldungeon.files.FileCategoryLink;
import com.centraldungeon.files.FileCategoryLinkRepository;
import com.centraldungeon.files.FileType;
import com.centraldungeon.files.StoredFile;
import com.centraldungeon.files.StoredFileRepository;
import com.centraldungeon.files.TableFile;
import com.centraldungeon.files.TableFileRepository;
import com.centraldungeon.files.TableFileType;
import com.centraldungeon.notifications.Notification;
import com.centraldungeon.notifications.NotificationParams;
import com.centraldungeon.notifications.NotificationRepository;
import com.centraldungeon.notifications.NotificationType;
import com.centraldungeon.registrations.RegistrationFile;
import com.centraldungeon.registrations.RegistrationFileRepository;
import com.centraldungeon.registrations.RegistrationRejection;
import com.centraldungeon.registrations.RegistrationRejectionRepository;
import com.centraldungeon.registrations.RegistrationStatusChange;
import com.centraldungeon.registrations.RegistrationStatusChangeRepository;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.settings.SettingKey;
import com.centraldungeon.settings.SystemSetting;
import com.centraldungeon.settings.SystemSettingChange;
import com.centraldungeon.settings.SystemSettingChangeRepository;
import com.centraldungeon.settings.SystemSettingRepository;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.Master;
import com.centraldungeon.tables.MasterRepository;
import com.centraldungeon.tables.MasterRowStatus;
import com.centraldungeon.tables.MasterType;
import com.centraldungeon.tables.TableSchedule;
import com.centraldungeon.tables.TableScheduleRepository;
import com.centraldungeon.tables.TableSession;
import com.centraldungeon.tables.TableSessionRepository;
import com.centraldungeon.tables.TableSessionStatus;
import com.centraldungeon.tables.TableStatusChange;
import com.centraldungeon.tables.TableStatusChangeRepository;
import com.centraldungeon.tables.Weekday;
import com.centraldungeon.tasks.SubmissionFile;
import com.centraldungeon.tasks.SubmissionFileRepository;
import com.centraldungeon.tasks.TableTask;
import com.centraldungeon.tasks.TableTaskRepository;
import com.centraldungeon.tasks.TaskAudience;
import com.centraldungeon.tasks.TaskFile;
import com.centraldungeon.tasks.TaskFileRepository;
import com.centraldungeon.tasks.TaskSubmission;
import com.centraldungeon.tasks.TaskSubmissionRepository;
import com.centraldungeon.users.PlatformRole;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleChange;
import com.centraldungeon.users.UserRoleChangeAction;
import com.centraldungeon.users.UserRoleChangeRepository;
import com.centraldungeon.users.UserRoleRepository;
import com.centraldungeon.users.UserStatus;
import com.centraldungeon.users.UserStatusChange;
import com.centraldungeon.users.UserStatusChangeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;

/**
 * The world of one cell: an empty database, the cell's actor with its roles and its relation, and
 * whatever the row's request asks for. Rows call it {@code w}.
 *
 * <p>Every cell starts from an emptied database, so what one cell creates or mutates is never seen by
 * the next, and the order cells run in does not matter. Only migrations' reference data survives: the
 * roles, the table types and the seeded catalog values.
 *
 * <p>Relations are made on demand. {@link #table()} creates the row's main table with its live
 * {@code Primary}, and gives the <em>acting</em> relational actor its relation to it: a live
 * {@code Secondary} row, a removed one, a {@code Candidate} or a {@code Player} registration. The other
 * relational actors exist only when a row asks for them, which is what keeps, for example, a draft
 * without applications deletable by its {@code Primary}.
 *
 * <p>Everything is written through repositories and never inside a test transaction, the same way the
 * other HTTP integration tests seed their data, so the request sees exactly what was committed.
 */
final class MatrixFixtures {

    /** Reference data that migrations seed and nothing in the matrix may change. */
    private static final Set<String> KEPT_TABLES = Set.of("flyway_schema_history", "roles", "table_types");

    /** Tables whose seeded rows survive the wipe while the rows created by a cell do not. */
    private static final Set<String> SEEDED_CATALOGS = Set.of("systems", "tags", "platforms");

    /** Capacity of every table the fixtures create: room for anybody a row adds. */
    private static final int MAX_PLAYERS = 5;

    /** Planned sessions of every table the fixtures create. */
    private static final int TOTAL_SESSIONS = 4;

    /** Weekdays the fixture tables are scheduled on; Sunday is left free for {@link #tableJson()}. */
    private static final int WEEKDAYS_FOR_FIXTURES = 6;

    /** The first hour a fixture table is scheduled at. */
    private static final int FIRST_SLOT_HOUR = 8;

    /** The statements that empty the database, computed once from the real schema. */
    private static @Nullable List<String> wipeStatements;

    /** Where every repository and service comes from. */
    private final ApplicationContext context;

    /** Who calls the endpoint in this cell. */
    private final MatrixActor actor;

    /** The accounts behind the actors, created on first use. */
    private final Map<MatrixActor, User> actors = new EnumMap<>(MatrixActor.class);

    /** Third parties, by the label a row gave them. */
    private final Map<String, User> persons = new HashMap<>();

    /** Tables other than the main one, by the label a row gave them. */
    private final Map<String, GameTable> otherTables = new HashMap<>();

    /** The third party that is the Primary of each table made by {@link #otherTable}, by table id. */
    private final Map<String, User> otherPrimaries = new HashMap<>();

    /** One accepted value per catalog, for the tables and the table bodies. */
    private final Map<CatalogType, CatalogValue> acceptedValues = new EnumMap<>(CatalogType.class);

    /** The roles, looked up once. */
    private final Map<PlatformRole, Role> roles = new EnumMap<>(PlatformRole.class);

    /** Ids a row kept for its body check. */
    private final Map<String, String> remembered = new HashMap<>();

    /** The row's main table, once something asked for it. */
    private @Nullable GameTable table;

    /** The {@code CANDIDATE} actor's application on the main table. */
    private @Nullable TableRegistration candidateRegistration;

    /** The {@code TABLE_PLAYER} actor's registration on the main table. */
    private @Nullable TableRegistration playerRegistration;

    /** How many weekly slots this world has handed out, so no two tables clash. */
    private int slots;

    /** How many sessions this world has created, for their sequence numbers. */
    private int sessions;

    private MatrixFixtures(ApplicationContext context, MatrixActor actor) {
        this.context = context;
        this.actor = actor;
    }

    /**
     * Empties the database and starts the world of one cell.
     *
     * @param context the application context of the test
     * @param actor   who calls the endpoint in this cell
     * @return the fixtures of that cell
     */
    static MatrixFixtures forCell(ApplicationContext context, MatrixActor actor) {
        wipe(context);
        return new MatrixFixtures(context, actor);
    }

    // ---------------------------------------------------------------- people

    /** @return who calls the endpoint in this cell */
    MatrixActor actor() {
        return actor;
    }

    /**
     * The account behind the cell's actor, which is what "own" means in a row: the resource the actor
     * owns, the notification addressed to them. For {@code ANONYMOUS}, who has no account, it is a
     * bystander, so a row's request stays the same for every column.
     *
     * @return the cell's account
     */
    User me() {
        return actor == MatrixActor.ANONYMOUS ? person("bystander") : user(actor);
    }

    /**
     * @param who any actor but {@code ANONYMOUS}
     * @return its account, created with exactly its roles the first time it is asked for
     */
    User user(MatrixActor who) {
        if (who == MatrixActor.ANONYMOUS) {
            throw new IllegalArgumentException("ANONYMOUS has no account; me() gives a bystander instead");
        }
        User existing = actors.get(who);
        if (existing != null) {
            return existing;
        }
        User created = createUser(who.name().toLowerCase(Locale.ROOT), who.roles());
        actors.put(who, created);
        return created;
    }

    /**
     * @param label how the row calls this third party; the same label gives the same account
     * @return an account with only {@code Player}, unrelated to everything
     */
    User person(String label) {
        return person(label, PlatformRole.PLAYER);
    }

    /**
     * @param label how the row calls this third party; the same label gives the same account
     * @param roles the roles it holds, used only the first time the label is asked for
     * @return an account unrelated to everything
     */
    User person(String label, PlatformRole... roles) {
        User existing = persons.get(label);
        if (existing != null) {
            return existing;
        }
        User created = createUser(label, List.of(roles));
        persons.put(label, created);
        return created;
    }

    /**
     * @param user the account
     * @param role a role it did not hold
     */
    void grant(User user, PlatformRole role) {
        bean(UserRoleRepository.class).save(new UserRole(user, role(role)));
    }

    /**
     * @param user the account to block, which keeps its roles and its tokens
     */
    void block(User user) {
        user.setStatus(UserStatus.Blocked);
        bean(UserRepository.class).save(user);
    }

    /**
     * @param user the account whose onboarding goes back to pending
     */
    void withoutOnboarding(User user) {
        user.setName(null);
        user.setCountry(null);
        bean(UserRepository.class).save(user);
    }

    // ---------------------------------------------------------------- tables

    /** @return the row's main table, {@code Opened} unless a row asked for a status first */
    GameTable table() {
        return table != null ? table : table(GameTableStatus.Opened);
    }

    /**
     * The row's main table: complete (system, platform, tag, a weekly slot), with {@code PRIMARY} as
     * its live Primary and the acting relational actor in its relation. An {@code Unassigned} table has
     * no masters, like the real one.
     *
     * @param status the status it is in; it has to be asked for before anything else uses the table
     * @return the main table
     */
    GameTable table(GameTableStatus status) {
        if (table != null) {
            if (table.getStatus() != status) {
                throw new IllegalStateException("The main table is already " + table.getStatus() + ", not " + status);
            }
            return table;
        }
        if (status == GameTableStatus.Unassigned) {
            table = newTable("Main table", status, person("table author", PlatformRole.ADMIN));
            return table;
        }
        User primary = user(MatrixActor.PRIMARY);
        table = newTable("Main table", status, primary);
        master(table, primary, MasterType.Primary);
        switch (actor) {
            case SECONDARY -> master(table, user(MatrixActor.SECONDARY), MasterType.Secondary);
            case REMOVED_SECONDARY -> removedMaster(table, user(MatrixActor.REMOVED_SECONDARY));
            case CANDIDATE -> candidateRegistration();
            case TABLE_PLAYER -> playerRegistration();
            default -> { }
        }
        return table;
    }

    /**
     * Another table, with a third party as its live Primary.
     *
     * @param label  how the row calls it; the same label gives the same table
     * @param status the status it is in
     * @return the table
     */
    GameTable otherTable(String label, GameTableStatus status) {
        GameTable existing = otherTables.get(label);
        if (existing != null) {
            return existing;
        }
        User primary = person(label + " primary");
        GameTable created = newTable(label, status, primary);
        if (status != GameTableStatus.Unassigned) {
            master(created, primary, MasterType.Primary);
        }
        otherTables.put(label, created);
        otherPrimaries.put(created.getId(), primary);
        return created;
    }

    /**
     * @param table a table made by {@link #otherTable}
     * @return the third party that runs it as its Primary
     * @throws IllegalArgumentException if the table was not made by {@link #otherTable}
     */
    User primaryOf(GameTable table) {
        User primary = otherPrimaries.get(table.getId());
        if (primary == null) {
            throw new IllegalArgumentException("Not a table made by otherTable: " + table.getId());
        }
        return primary;
    }

    /**
     * @param table the table
     * @param user  who runs it
     * @param type  {@code Primary} or {@code Secondary}
     * @return the live master row
     */
    Master master(GameTable table, User user, MasterType type) {
        return bean(MasterRepository.class).save(new Master(table, user, type));
    }

    /**
     * @param table the table
     * @param user  the co-master who used to run it
     * @return the removed {@code Secondary} row
     */
    Master removedMaster(GameTable table, User user) {
        Master row = new Master(table, user, MasterType.Secondary);
        row.setStatus(MasterRowStatus.Deleted);
        row.setDeletedAt(LocalDateTime.now());
        return bean(MasterRepository.class).save(row);
    }

    /**
     * A transition in the table's history, which is what {@code status-history} reads.
     *
     * @param table the table
     * @param from  the status it left
     * @param to    the status it reached
     * @param by    who made the change
     * @return the history row
     */
    TableStatusChange statusChange(GameTable table, GameTableStatus from, GameTableStatus to, User by) {
        return bean(TableStatusChangeRepository.class)
                .save(new TableStatusChange(table, from, to, by, "Recorded by the role matrix fixtures"));
    }

    /**
     * @param table the table
     * @param admin who reserves it in the admin queue
     * @return the table, reserved
     */
    GameTable claim(GameTable table, User admin) {
        table.claim(admin, LocalDateTime.now());
        return bean(GameTableRepository.class).save(table);
    }

    /**
     * @param table  the table
     * @param status the session's status
     * @return a session of the table, a week further on than the previous one
     */
    TableSession session(GameTable table, TableSessionStatus status) {
        sessions++;
        TableSession session = new TableSession(table, sessions, LocalDateTime.now().plusWeeks(sessions));
        session.setStatus(status);
        return bean(TableSessionRepository.class).save(session);
    }

    /**
     * A valid body for creating or editing a table: an accepted system, platform and tag, and a weekly
     * slot on Sunday, which no fixture table uses.
     *
     * @return the JSON
     */
    String tableJson() {
        return tableJson(MAX_PLAYERS);
    }

    /**
     * @param maxPlayers the capacity the body asks for
     * @return the same body as {@link #tableJson()}, with that capacity
     */
    String tableJson(int maxPlayers) {
        return """
                {"name": "%s", "description": "A table written by the role matrix",
                 "systemIds": ["%s"], "tagIds": ["%s"], "platformIds": ["%s"],
                 "totalSessions": %d, "maxPlayers": %d,
                 "schedule": [{"weekday": "Sunday", "hourtime": "20:00:00", "duration": "01:00:00"}]}
                """.formatted(
                        uniqueName("Table"),
                        accepted(CatalogType.SYSTEMS).getId(),
                        accepted(CatalogType.TAGS).getId(),
                        accepted(CatalogType.PLATFORMS).getId(),
                        TOTAL_SESSIONS,
                        maxPlayers);
    }

    // ---------------------------------------------------------------- registrations

    /** @return the {@code CANDIDATE} actor's {@code Candidate} application on the main table */
    TableRegistration candidateRegistration() {
        GameTable main = table();
        if (candidateRegistration == null) {
            candidateRegistration = registration(main, user(MatrixActor.CANDIDATE), TableRegistrationStatus.Candidate);
        }
        return candidateRegistration;
    }

    /** @return the {@code TABLE_PLAYER} actor's {@code Player} registration on the main table */
    TableRegistration playerRegistration() {
        GameTable main = table();
        if (playerRegistration == null) {
            playerRegistration = registration(main, user(MatrixActor.TABLE_PLAYER), TableRegistrationStatus.Player);
        }
        return playerRegistration;
    }

    /**
     * A registration in any status. A {@code Blocked} one carries the veto in its history, coming from
     * {@code Player}, and a {@code Rejected} one carries its rejection, both written by the table's author.
     *
     * @param table  the table
     * @param user   who applied
     * @param status the status it is in
     * @return the registration
     */
    TableRegistration registration(GameTable table, User user, TableRegistrationStatus status) {
        TableRegistration registration = new TableRegistration(table, user, "I would like to play");
        registration.setStatus(status);
        if (status == TableRegistrationStatus.Deleted) {
            registration.setDeletedAt(LocalDateTime.now());
        }
        TableRegistration saved = bean(TableRegistrationRepository.class).save(registration);
        if (status == TableRegistrationStatus.Blocked) {
            bean(RegistrationStatusChangeRepository.class).save(new RegistrationStatusChange(
                    saved, TableRegistrationStatus.Player, TableRegistrationStatus.Blocked, table.getCreatedBy(), "Vetoed"));
        }
        if (status == TableRegistrationStatus.Rejected) {
            bean(RegistrationRejectionRepository.class)
                    .save(new RegistrationRejection(saved, "Not this time", table.getCreatedBy()));
        }
        return saved;
    }

    // ---------------------------------------------------------------- requests and the queue

    /**
     * A pending request about the requester's own account: {@code General}, {@code MasterGrant} or
     * {@code TableOpen}.
     *
     * @param type      the request's type
     * @param requester who asked
     * @return the request
     */
    ApprovalRequest approval(ApprovalRequestType type, User requester) {
        return approval(type, requester.getId(), requester);
    }

    /**
     * A pending request about something else: the table of a {@code TablePause}, the registration of a
     * {@code PlayerBan}.
     *
     * @param type      the request's type
     * @param entityId  the id of what it is about
     * @param requester who asked
     * @return the request
     */
    ApprovalRequest approval(ApprovalRequestType type, String entityId, User requester) {
        return bean(ApprovalRequestRepository.class).save(
                new ApprovalRequest(type, type.entityType(), entityId, requester, "Asked by the role matrix fixtures"));
    }

    /**
     * @param request the request
     * @param admin   who reserves it in the admin queue
     * @return the request, reserved
     */
    ApprovalRequest claim(ApprovalRequest request, User admin) {
        request.claim(admin, LocalDateTime.now());
        return bean(ApprovalRequestRepository.class).save(request);
    }

    // ---------------------------------------------------------------- catalogs

    /**
     * @param type        which catalog
     * @param status      the value's status; a {@code Disabled} one is stamped as removed
     * @param canonicalId the canonical it is an alias of, or null for a canonical
     * @return a value with a unique name
     */
    CatalogValue catalog(CatalogType type, CatalogStatus status, @Nullable String canonicalId) {
        String name = uniqueName(type.singular());
        CatalogValue value = switch (type) {
            case SYSTEMS -> new GameSystem(name);
            case TAGS -> new Tag(name);
            case PLATFORMS -> new Platform(name);
        };
        value.setStatus(status);
        value.setCanonicalId(canonicalId);
        if (status == CatalogStatus.Disabled) {
            value.setDeletedAt(LocalDateTime.now());
        }
        return switch (value) {
            case GameSystem system -> bean(SystemRepository.class).save(system);
            case Tag tag -> bean(TagRepository.class).save(tag);
            case Platform platform -> bean(PlatformRepository.class).save(platform);
            default -> throw new IllegalStateException("Unknown catalog value " + value);
        };
    }

    /**
     * @param type which catalog
     * @return this world's accepted canonical value of that catalog, the one every table uses
     */
    CatalogValue accepted(CatalogType type) {
        CatalogValue existing = acceptedValues.get(type);
        if (existing != null) {
            return existing;
        }
        CatalogValue created = catalog(type, CatalogStatus.Accepted, null);
        acceptedValues.put(type, created);
        return created;
    }

    // ---------------------------------------------------------------- files

    /**
     * A file with unique content, stored on disk so its download works.
     *
     * @param owner      who uploaded it
     * @param type       its life cycle; a {@code Public} one is stamped as published
     * @param categories the cajones it belongs to
     * @return the file
     */
    StoredFile file(User owner, FileType type, FileCategory... categories) {
        return file(owner, type, uniqueBytes(), categories);
    }

    /**
     * The same as {@link #file(User, FileType, FileCategory...)} with chosen bytes, for a row that
     * uploads them again to exercise deduplication.
     *
     * @param owner      who uploaded it
     * @param type       its life cycle
     * @param content    its bytes
     * @param categories the cajones it belongs to
     * @return the file
     */
    StoredFile file(User owner, FileType type, byte[] content, FileCategory... categories) {
        String storageKey = IdGenerator.newId();
        bean(StorageService.class).store(storageKey, content);
        StoredFile file = new StoredFile(
                uniqueName("file") + ".pdf", storageKey, sha256(content), "application/pdf", content.length, type, owner);
        if (type == FileType.Public) {
            file.setPublishedAt(LocalDateTime.now());
        }
        StoredFile saved = bean(StoredFileRepository.class).save(file);
        for (FileCategory category : categories) {
            bean(FileCategoryLinkRepository.class).save(new FileCategoryLink(saved.getId(), category));
        }
        return saved;
    }

    /**
     * @param table     the table
     * @param file      the file it shares
     * @param isPrivate whether only the table's masters and the admins see it
     * @return the live {@code Preparation} link
     */
    TableFile link(GameTable table, StoredFile file, boolean isPrivate) {
        return bean(TableFileRepository.class)
                .save(new TableFile(table.getId(), file.getId(), TableFileType.Preparation, isPrivate));
    }

    /**
     * @param registration the application
     * @param file         the file attached to it
     */
    void registrationFile(TableRegistration registration, StoredFile file) {
        bean(RegistrationFileRepository.class).save(new RegistrationFile(registration.getId(), file.getId()));
    }

    /**
     * @param task the request a master wrote
     * @param file the blank form attached to it
     */
    void taskFile(TableTask task, StoredFile file) {
        bean(TaskFileRepository.class).save(new TaskFile(task.getId(), file.getId()));
    }

    /**
     * @param submission the answer
     * @param file       the file handed in with it
     */
    void submissionFile(TaskSubmission submission, StoredFile file) {
        bean(SubmissionFileRepository.class).save(new SubmissionFile(submission.getId(), file.getId()));
    }

    // ---------------------------------------------------------------- tasks

    /**
     * An open request of the table, accepting text and files.
     *
     * @param table    the table
     * @param audience who it is addressed to
     * @param target   the one player of a {@code Single} audience, or null for the others
     * @return the request
     */
    TableTask task(GameTable table, TaskAudience audience, @Nullable User target) {
        TableTask task = new TableTask(table, audience, uniqueName("Task"));
        task.setTargetUser(target);
        return bean(TableTaskRepository.class).save(task);
    }

    /**
     * @param task the request
     * @param user who answered
     * @return the answer
     */
    TaskSubmission submission(TableTask task, User user) {
        return bean(TaskSubmissionRepository.class).save(new TaskSubmission(task, user, "<p>My answer</p>"));
    }

    // ---------------------------------------------------------------- accounts, notifications, settings

    /**
     * @param user who it is addressed to
     * @return an unread notification
     */
    Notification notification(User user) {
        return bean(NotificationRepository.class).save(new Notification(
                user, NotificationType.TableFinished, NotificationParams.ofTable("Main table"), null, null));
    }

    /**
     * @param target whose account changed
     * @param role   the role granted or revoked
     * @param action what happened to it
     * @param by     who did it
     * @return the history row
     */
    UserRoleChange roleChange(User target, PlatformRole role, UserRoleChangeAction action, User by) {
        return bean(UserRoleChangeRepository.class)
                .save(new UserRoleChange(target, role(role), action, by, "Recorded by the role matrix fixtures"));
    }

    /**
     * @param target whose account changed
     * @param from   the status it left
     * @param to     the status it reached; the account itself is not touched
     * @param by     who did it
     * @return the history row
     */
    UserStatusChange accountStatusChange(User target, UserStatus from, UserStatus to, User by) {
        return bean(UserStatusChangeRepository.class)
                .save(new UserStatusChange(target, from, to, by, "Recorded by the role matrix fixtures"));
    }

    /**
     * A setting somebody changed, with its history row.
     *
     * @param key   which setting
     * @param value its new value
     * @param by    who changed it
     */
    void settingChange(SettingKey key, String value, User by) {
        bean(SystemSettingRepository.class).save(new SystemSetting(key, value, by));
        bean(SystemSettingChangeRepository.class).save(new SystemSettingChange(
                key, String.valueOf(key.defaultValue()), value, by, "Recorded by the role matrix fixtures"));
    }

    // ---------------------------------------------------------------- requests

    /**
     * The refresh call as the browser makes it: a valid CSRF pair, and the refresh cookie of the cell's
     * account. {@code ANONYMOUS} sends the CSRF pair and no cookie.
     *
     * @return the request, without path variables
     */
    MatrixRequest refreshRequest() {
        String csrf = UUID.randomUUID().toString();
        MatrixRequest request = MatrixRequest.path().cookie("XSRF-TOKEN", csrf).header("X-XSRF-TOKEN", csrf);
        if (actor != MatrixActor.ANONYMOUS) {
            request.cookie(RefreshCookieFactory.COOKIE_NAME, bean(JwtService.class).issueRefreshToken(me().getId()));
        }
        return request;
    }

    /**
     * @param prefix what the name starts with
     * @return a name no other row in the database has
     */
    String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    /** @return bytes no other file has, so an upload is never mistaken for a re-upload */
    byte[] uniqueBytes() {
        return ("%PDF-1.4 role matrix " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Keeps an id for the row's body check, which runs after the call in the same world.
     *
     * @param key a name for it
     * @param id  the id
     * @return the id, so it can be kept and used in one expression
     */
    String remember(String key, String id) {
        remembered.put(key, id);
        return id;
    }

    /**
     * @param key the name it was kept under
     * @return the id
     */
    String recall(String key) {
        String id = remembered.get(key);
        if (id == null) {
            throw new IllegalStateException("Nothing remembered as '" + key + "'");
        }
        return id;
    }

    /**
     * For a check the helpers above do not cover, such as reading a row back after the call.
     *
     * @param type the bean's type, usually a repository
     * @param <T>  the bean's type
     * @return the bean
     */
    <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    /** Gives the cell's actor its account and, when it is relational, its relation to the main table. */
    void settle() {
        if (actor != MatrixActor.ANONYMOUS) {
            me();
        }
        if (actor.belongsToTable()) {
            table();
        }
    }

    // ---------------------------------------------------------------- internals

    private User createUser(String label, List<PlatformRole> grantedRoles) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        User user = new User(UUID.randomUUID().toString().replace("-", ""), label.replace(' ', '-') + "-" + suffix);
        user.setName(label);
        user.setCountry("AR");
        User saved = bean(UserRepository.class).save(user);
        grantedRoles.forEach(role -> grant(saved, role));
        return saved;
    }

    private Role role(PlatformRole role) {
        return roles.computeIfAbsent(role, wanted -> bean(RoleRepository.class)
                .findByName(wanted.roleName())
                .orElseThrow(() -> new IllegalStateException(wanted.roleName() + " role is missing from the seed")));
    }

    private GameTable newTable(String label, GameTableStatus status, User createdBy) {
        GameTable created = new GameTable(uniqueName(label), createdBy);
        created.setStatus(status);
        created.setDescription("A table created by the role matrix fixtures");
        created.setMaxPlayers(MAX_PLAYERS);
        created.setTotalSessions(TOTAL_SESSIONS);
        created.setStartDate(LocalDate.now().plusWeeks(1));
        if (status == GameTableStatus.Finished || status == GameTableStatus.Canceled) {
            created.setClosedAt(LocalDateTime.now());
        }
        GameTable saved = bean(GameTableRepository.class).save(created);
        if (status == GameTableStatus.Unassigned) {
            return saved;
        }
        String id = saved.getId();
        bean(TableSystemRepository.class).save(new TableSystem(id, accepted(CatalogType.SYSTEMS).getId()));
        bean(TablePlatformRepository.class).save(new TablePlatform(id, accepted(CatalogType.PLATFORMS).getId()));
        bean(TableTagRepository.class).save(new TableTag(id, accepted(CatalogType.TAGS).getId()));
        Weekday weekday = Weekday.values()[slots % WEEKDAYS_FOR_FIXTURES];
        LocalTime hour = LocalTime.of(FIRST_SLOT_HOUR + slots / WEEKDAYS_FOR_FIXTURES, 0);
        bean(TableScheduleRepository.class).save(new TableSchedule(id, weekday, hour, LocalTime.of(1, 0)));
        slots++;
        return saved;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from the JVM", e);
        }
    }

    /**
     * Deletes every row a test may have written, with foreign keys off so the order does not matter.
     * The statements are built once, from the schema the migrations created, and the first build reads
     * the seeded catalog ids before any cell wrote anything.
     */
    private static void wipe(ApplicationContext context) {
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String sql : wipeStatements(statement)) {
                    statement.executeUpdate(sql);
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not empty the database between two cells", e);
        }
    }

    private static synchronized List<String> wipeStatements(Statement statement) throws SQLException {
        if (wipeStatements != null) {
            return wipeStatements;
        }
        List<String> tables = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }
        List<String> statements = new ArrayList<>();
        for (String name : tables) {
            if (KEPT_TABLES.contains(name)) {
                continue;
            }
            statements.add(SEEDED_CATALOGS.contains(name) ? deleteAllBut(statement, name) : "DELETE FROM " + name);
        }
        wipeStatements = List.copyOf(statements);
        return wipeStatements;
    }

    private static String deleteAllBut(Statement statement, String table) throws SQLException {
        List<String> seeded = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery("SELECT id FROM " + table)) {
            while (rows.next()) {
                seeded.add("'" + rows.getString(1) + "'");
            }
        }
        return seeded.isEmpty()
                ? "DELETE FROM " + table
                : "DELETE FROM " + table + " WHERE id NOT IN (" + seeded.stream().collect(Collectors.joining(",")) + ")";
    }
}
