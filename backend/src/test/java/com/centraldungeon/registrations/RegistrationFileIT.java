package com.centraldungeon.registrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.files.FileService;
import com.centraldungeon.files.FileType;
import com.centraldungeon.files.dto.FileResponse;
import com.centraldungeon.files.dto.FileUsageResponse;
import com.centraldungeon.files.dto.UploadFileRequest;
import com.centraldungeon.registrations.dto.CreateRegistrationRequest;
import com.centraldungeon.registrations.dto.RegistrationResponse;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.users.Role;
import com.centraldungeon.users.RoleRepository;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserRepository;
import com.centraldungeon.users.UserRole;
import com.centraldungeon.users.UserRoleRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The half of the character-sheet-on-an-application story (#60 uso 2) only a real database can
 * answer: the fourth usage source's join, and #247's read-side filter, against real SQL and a real
 * composite key over {@code registration_files}.
 *
 * <p>Wired with {@code @DynamicPropertySource}, not {@code @ServiceConnection} - see
 * {@code RegistrationServiceIT} for why.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class RegistrationFileIT {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private FileService fileService;

    @Autowired
    private RegistrationFileRepository registrationFileRepository;

    @Autowired
    private GameTableRepository gameTableRepository;

    @Autowired
    private MasterService masterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private GameTable table;
    private GameTable otherTable;
    private User master;
    private User otherMaster;
    private User candidate;
    private User otherCandidate;

    @BeforeEach
    void setUp() {
        Role playerRole = roleRepository.findByName("Player").orElseThrow();

        master = userRepository.save(new User(randomDiscordId(), "Master"));
        otherMaster = userRepository.save(new User(randomDiscordId(), "Other Master"));
        candidate = userRepository.save(new User(randomDiscordId(), "Candidate"));
        otherCandidate = userRepository.save(new User(randomDiscordId(), "Other Candidate"));
        userRoleRepository.save(new UserRole(candidate, playerRole));
        userRoleRepository.save(new UserRole(otherCandidate, playerRole));

        GameTable newTable = new GameTable("Hijos del Vacío", master);
        newTable.setStatus(GameTableStatus.Opened);
        table = gameTableRepository.save(newTable);
        masterService.createPrimary(table, master);

        GameTable newOtherTable = new GameTable("La Cripta", otherMaster);
        newOtherTable.setStatus(GameTableStatus.Opened);
        otherTable = gameTableRepository.save(newOtherTable);
        masterService.createPrimary(otherTable, otherMaster);
    }

    /**
     * The join of #232's fourth source against real SQL: an enum literal written into the JPQL
     * itself, over a composite key, is exactly what a stubbed repository has no opinion about.
     */
    @Test
    void theFourthUsageSourceReportsTheUseWithTheTablesName() {
        FileResponse sheet = uploadAsCandidate("ficha.pdf", "la elfa");
        registrationService.apply(table.getId(), candidate.getId(), new CreateRegistrationRequest(null, List.of(sheet.id())));

        var mine = fileService.listMine(candidate.getId(), null, null, PageRequest.of(0, 20));

        assertThat(mine.content())
                .filteredOn(file -> file.id().equals(sheet.id()))
                .singleElement()
                .extracting(FileResponse::usages, list(FileUsageResponse.class))
                .extracting(FileUsageResponse::category, FileUsageResponse::contextName)
                .containsExactly(tuple("PlayerApplication", "Hijos del Vacío"));
    }

    /**
     * #247's whole reason to exist, proven against the database: withdrawing does not delete or mark
     * the {@code registration_files} row, but the file it pointed at stops being a use as soon as the
     * application is no longer live.
     */
    @Test
    void withdrawingLeavesTheRowUntouchedButTheFileReportsAsUnused() {
        FileResponse sheet = uploadAsCandidate("ficha.pdf", "la elfa");
        RegistrationResponse application =
                registrationService.apply(table.getId(), candidate.getId(), new CreateRegistrationRequest(null, List.of(sheet.id())));

        registrationService.withdraw(application.id(), candidate.getId());

        // The row is still there, still Current: withdrawing is deliberately not a cascade (#247).
        RegistrationFileId key = new RegistrationFileId(application.id(), sheet.id());
        assertThat(registrationFileRepository.findById(key))
                .isPresent()
                .get()
                .extracting(RegistrationFile::getStatus)
                .isEqualTo(RegistrationFileStatus.Current);

        // But the read side no longer counts it: a withdrawn application is not a live one.
        var mine = fileService.listMine(candidate.getId(), null, null, PageRequest.of(0, 20));
        assertThat(mine.content())
                .filteredOn(file -> file.id().equals(sheet.id()))
                .singleElement()
                .extracting(FileResponse::usages, list(FileUsageResponse.class))
                .isEmpty();
    }

    /** The seventh way a file is reachable (#211's sister): the table's own master opens it. */
    @Test
    void theTablesMasterReadsTheCandidatesAttachedFile() {
        String fileId = givenAnApplicationWithAFile();

        FileResponse read = fileService.findById(fileId, master.getId());

        assertThat(read.id()).isEqualTo(fileId);
    }

    /** Wide as the table applied to, and no wider: a stranger table's master gets 404, not the file. */
    @Test
    void theMasterOfAnotherTableGets404OnTheCandidatesFile() {
        String fileId = givenAnApplicationWithAFile();

        assertThatThrownBy(() -> fileService.findById(fileId, otherMaster.getId())).isInstanceOf(NotFoundException.class);
    }

    /** Somebody else applying elsewhere has no door into a candidate's own attached file. */
    @Test
    void anotherCandidateGets404OnSomebodyElsesAttachedFile() {
        String fileId = givenAnApplicationWithAFile();

        assertThatThrownBy(() -> fileService.findById(fileId, otherCandidate.getId())).isInstanceOf(NotFoundException.class);
    }

    private String givenAnApplicationWithAFile() {
        FileResponse sheet = uploadAsCandidate("ficha.pdf", "la elfa");
        registrationService.apply(table.getId(), candidate.getId(), new CreateRegistrationRequest(null, List.of(sheet.id())));
        return sheet.id();
    }

    private FileResponse uploadAsCandidate(String filename, String content) {
        return fileService.upload(pdf(filename, content), new UploadFileRequest(FileType.Private, null), candidate.getId())
                .file();
    }

    private static MockMultipartFile pdf(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
    }

    /** discord_id is VARCHAR(32); a UUID with the dashes stripped fits exactly. */
    private static String randomDiscordId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
