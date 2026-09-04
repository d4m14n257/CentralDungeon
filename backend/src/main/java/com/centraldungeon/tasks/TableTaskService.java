package com.centraldungeon.tasks;

import com.centraldungeon.common.exception.ForbiddenActionException;
import com.centraldungeon.common.exception.InvalidRequestException;
import com.centraldungeon.common.exception.NotFoundException;
import com.centraldungeon.common.text.RichTextSanitizer;
import com.centraldungeon.notifications.NotificationService;
import com.centraldungeon.registrations.TableRegistration;
import com.centraldungeon.registrations.TableRegistrationRepository;
import com.centraldungeon.registrations.TableRegistrationStatus;
import com.centraldungeon.tables.GameTable;
import com.centraldungeon.tables.GameTableRepository;
import com.centraldungeon.tables.GameTableStatus;
import com.centraldungeon.tables.MasterService;
import com.centraldungeon.tables.TableSession;
import com.centraldungeon.tables.TableSessionRepository;
import com.centraldungeon.tasks.dto.ApplicableTaskResponse;
import com.centraldungeon.tasks.dto.CreateTaskRequest;
import com.centraldungeon.tasks.dto.TaskRecipientResponse;
import com.centraldungeon.tasks.dto.TaskResponse;
import com.centraldungeon.tasks.dto.UpdateTaskRequest;
import com.centraldungeon.users.User;
import com.centraldungeon.users.UserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a table asks of people: publishing it, correcting it, closing it, and working out who it
 * applies to.
 *
 * <p><b>Publishing is one act</b> (#77). Creating the row and telling the recipients happen in the
 * same transaction, because a task nobody heard about cannot be answered - and because two steps
 * would mean a state where the row exists and nobody knows, which somebody would eventually have to
 * clean up by hand. There is no draft for the same reason.
 *
 * <p><b>The audience is the feature's one real concept.</b> Everything else follows from it: who gets
 * notified, who may answer, who counts as missing, and what a given reader sees on the table. It is
 * resolved in {@link #recipientsOf} and {@link #isRecipient} and nowhere else, so the four answers
 * cannot drift apart.
 *
 * <p><b>Nothing here punishes anybody</b> (#70). {@code isMandatory} is carried to the screen and
 * read by no rule; the roster of who has not answered is a list of people to talk to. Evicting,
 * vetoing and rejecting exist elsewhere and are always somebody's decision.
 *
 * <p>Every mutation answers to <b>pertenencia and not to a role</b> (#17, #121, #135): running this
 * table is what authorizes asking things of its people, which no {@code @PreAuthorize} can see.
 */
@Service
public class TableTaskService {

    /** The {@code table_tasks} rows. */
    private final TableTaskRepository taskRepository;

    /** The answers, for the counts every task row carries. */
    private final TaskSubmissionRepository submissionRepository;

    /** Resolves the table the reads and writes start from. */
    private final GameTableRepository gameTableRepository;

    /** Resolves the session a task can be tied to, and lets it be checked against the table. */
    private final TableSessionRepository sessionRepository;

    /** The audience, resolved into people: candidates, players, or one named target. */
    private final TableRegistrationRepository registrationRepository;

    /** Answers pertenencia: a row in {@code masters}, never the platform role (#135). */
    private final MasterService masterService;

    /** Resolves the one person a {@code Single} task addresses. */
    private final UserService userService;

    /** Tells the recipients that something was asked of them (#77). */
    private final NotificationService notificationService;

    /** The whitelist of #62, applied on the way in and on the way out. */
    private final RichTextSanitizer richTextSanitizer;

    /** Entity to DTO. */
    private final TaskMapper taskMapper;

    /**
     * @param taskRepository         the {@code table_tasks} rows
     * @param submissionRepository   the answers, for the counts a task row shows
     * @param gameTableRepository    resolves the table every operation hangs off
     * @param sessionRepository      resolves the session a task may be tied to (#63)
     * @param registrationRepository turns an audience into people
     * @param masterService          answers pertenencia (#17, #121, #135)
     * @param userService            resolves the target of a {@code Single} task
     * @param notificationService    tells the recipients on publication (#77)
     * @param richTextSanitizer      the whitelist of #62
     * @param taskMapper             entity to DTO
     */
    public TableTaskService(
            TableTaskRepository taskRepository,
            TaskSubmissionRepository submissionRepository,
            GameTableRepository gameTableRepository,
            TableSessionRepository sessionRepository,
            TableRegistrationRepository registrationRepository,
            MasterService masterService,
            UserService userService,
            NotificationService notificationService,
            RichTextSanitizer richTextSanitizer,
            TaskMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.gameTableRepository = gameTableRepository;
        this.sessionRepository = sessionRepository;
        this.registrationRepository = registrationRepository;
        this.masterService = masterService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.richTextSanitizer = richTextSanitizer;
        this.taskMapper = taskMapper;
    }

    /**
     * Publishes a task and tells the people it is addressed to (#77).
     *
     * <p><b>The table's status does not gate this</b>, deliberately. Writing what will be asked of
     * applicants is one of the things a master does <em>while</em> preparing a table, and refusing it
     * until the table opens would push the most natural use out of the feature. A table with nobody
     * in it simply has no recipients yet, so the notification loop runs zero times and the task waits
     * on the table for whoever arrives.
     *
     * @param gameTableId the table doing the asking
     * @param request     what is being asked, of whom
     * @param actorId     the actor, from the token
     * @return the published task
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws InvalidRequestException  if the audience and the target contradict each other, if
     *                                  neither answer channel is open, or if the session belongs to
     *                                  another table
     * @throws NotFoundException        if the table is not there
     */
    @Transactional
    public TaskResponse publish(String gameTableId, CreateTaskRequest request, String actorId) {
        GameTable gameTable = requireLiveTable(gameTableId);
        requireMasterOf(gameTableId, actorId);
        requireAnswerable(request.acceptsText(), request.acceptsFiles());

        TableTask task = new TableTask(gameTable, request.audience(), request.title());
        task.setDescription(richTextSanitizer.sanitize(request.description()));
        task.setTargetUser(resolveTarget(gameTable, request.audience(), request.targetUserId()));
        task.setTableSession(resolveSession(gameTable, request.tableSessionId()));
        task.setAcceptsText(request.acceptsText());
        task.setAcceptsFiles(request.acceptsFiles());
        task.setMandatory(request.isMandatory());
        task.setDueAt(request.dueAt());

        TableTask saved = taskRepository.save(task);
        List<TaskRecipientResponse> recipients = recipientsOf(saved);
        for (TaskRecipientResponse recipient : recipients) {
            notificationService.notifyTaskPublished(recipient.userId(), gameTable, saved.getTitle());
        }
        return toResponse(saved, 0, 0, recipients.size());
    }

    /**
     * Corrects a published task.
     *
     * <p><b>It does not notify again.</b> #77 puts the notification at publication; a task fixed
     * three times ringing three times for a headline that never changed is how people learn to ignore
     * the bell. What has already been answered stays answered - correcting the wording of a request
     * does not invalidate what somebody sent in good faith (#76).
     *
     * @param taskId  the task
     * @param request the whole state it should end in (#189)
     * @param actorId the actor, from the token
     * @return the task after the correction
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws InvalidRequestException  same three cases as publishing
     * @throws NotFoundException        if the task is not there or was marked gone
     */
    @Transactional
    public TaskResponse update(String taskId, UpdateTaskRequest request, String actorId) {
        TableTask task = getLiveTask(taskId);
        requireMasterOf(task.getGameTable().getId(), actorId);
        requireAnswerable(request.acceptsText(), request.acceptsFiles());

        task.setTitle(request.title());
        task.setDescription(richTextSanitizer.sanitize(request.description()));
        task.setAudience(request.audience());
        task.setTargetUser(resolveTarget(task.getGameTable(), request.audience(), request.targetUserId()));
        task.setTableSession(resolveSession(task.getGameTable(), request.tableSessionId()));
        task.setAcceptsText(request.acceptsText());
        task.setAcceptsFiles(request.acceptsFiles());
        task.setMandatory(request.isMandatory());
        task.setDueAt(request.dueAt());

        return describe(task);
    }

    /**
     * Stops the task from taking answers.
     *
     * <p>What was already handed in stays readable: closing ends the intake, it does not erase the
     * history (#76). Closing an already closed task is left alone rather than refused - the end state
     * is the one that was asked for.
     *
     * @param taskId  the task
     * @param actorId the actor, from the token
     * @return the task, now closed
     * @throws ForbiddenActionException if the actor does not run the table
     * @throws NotFoundException        if the task is not there or was marked gone
     */
    @Transactional
    public TaskResponse close(String taskId, String actorId) {
        TableTask task = getLiveTask(taskId);
        requireMasterOf(task.getGameTable().getId(), actorId);
        task.setStatus(TaskStatus.Closed);
        return describe(task);
    }

    /**
     * Everything a table has asked, as the people running it see it - the Peticiones tab.
     *
     * <p>A list and not a page: it is bounded by what one table's masters chose to ask and is read as
     * one board, the same criterion the calendar and the attachments use.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @return its tasks, oldest first
     * @throws ForbiddenActionException if the actor does not run the table
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> listForTable(String gameTableId, String actorId) {
        requireMasterOf(gameTableId, actorId);
        List<TableTask> tasks =
                taskRepository.findByGameTable_IdAndStatusNotOrderByCreatedAtAsc(gameTableId, TaskStatus.Deleted);
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<String, TaskSubmissionCount> counts = countsOf(tasks);
        return tasks.stream()
                .map(task -> {
                    TaskSubmissionCount count = counts.get(task.getId());
                    return toResponse(
                            task,
                            count == null ? 0 : (int) count.submissions(),
                            count == null ? 0 : (int) count.distinctPeople(),
                            recipientsOf(task).size());
                })
                .toList();
    }

    /**
     * What this table is asking of <b>this</b> reader, for {@code /tables/:id} and
     * {@code /my/tables/:id}.
     *
     * <p><b>No pertenencia check, on purpose.</b> The {@code Candidates} tasks are readable by anybody
     * who can see the table, because what will be asked of you is half of deciding whether to apply -
     * the same reasoning #206 settled for the files a table shares. What membership does decide is the
     * rest: the {@code Players} tasks need a live Player registration, and a {@code Single} one is
     * only ever seen by its target.
     *
     * <p>The actor comes from the token and never from the URL (#121), so this cannot be pointed at
     * somebody else's list.
     *
     * @param gameTableId the table
     * @param actorId     the actor, from the token
     * @return the tasks that apply to them, oldest first. Closed ones are left out: the reader's list
     *         is what is being asked of them <em>now</em>
     * @throws NotFoundException if the table is not there or was marked gone
     */
    @Transactional(readOnly = true)
    public List<ApplicableTaskResponse> listApplicable(String gameTableId, String actorId) {
        requireLiveTable(gameTableId);
        boolean isPlayer = holdsStatus(gameTableId, actorId, TableRegistrationStatus.Player);
        boolean isCandidate = holdsStatus(gameTableId, actorId, TableRegistrationStatus.Candidate);

        List<TaskAudience> audiences = new ArrayList<>();
        audiences.add(TaskAudience.Candidates);
        if (isPlayer) {
            audiences.add(TaskAudience.Players);
            audiences.add(TaskAudience.Single);
        }

        return taskRepository
                .findByGameTable_IdAndAudienceInAndStatusOrderByCreatedAtAsc(gameTableId, audiences, TaskStatus.Open)
                .stream()
                // A Single task is its target's business and nobody else's, so it is narrowed here
                // rather than in the query: the repository resolves rows, the service decides who
                // may see them.
                .filter(task -> task.getAudience() != TaskAudience.Single || isTargetOf(task, actorId))
                .map(task -> taskMapper.toApplicable(
                        task,
                        richTextSanitizer.sanitize(task.getDescription()),
                        canSubmit(task, isPlayer, isCandidate, actorId),
                        submissionRepository
                                .findByTask_IdAndUser_IdAndDeletedAtIsNullOrderByCreatedAtAsc(task.getId(), actorId)
                                .size()))
                .toList();
    }

    /**
     * The single lookup every task read goes through, so it is the single place where a marked task
     * stops existing (#25).
     *
     * <p>Public because {@code TaskSubmissionService} needs the same entity under the same rule, and
     * a second copy of "and it must not be Deleted" is a second thing to remember. It is an internal
     * read and never exposed raw over HTTP (arquitectura.md 2.2, 2.3).
     *
     * @param taskId the task
     * @return the live task
     * @throws NotFoundException if it does not exist or was marked gone - 404 and not 403, because
     *                           "it was deleted" is not something a caller is entitled to learn
     */
    @Transactional(readOnly = true)
    public TableTask getLiveTask(String taskId) {
        TableTask task = taskRepository.findById(taskId).orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
        if (task.getStatus() == TaskStatus.Deleted) {
            throw new NotFoundException("Task not found: " + taskId);
        }
        return task;
    }

    /**
     * The audience, resolved into the people it names.
     *
     * <p>One method for the three uses - who gets notified on publication (#77), who counts as
     * missing on the master's roster, and how many people were asked - so those three answers cannot
     * disagree. It is computed at read time and never stored: a table that gains a player gains a
     * recipient, without anything having to be rewritten.
     *
     * @param task the task
     * @return the people it is addressed to. Empty when nobody holds the audience's status yet
     */
    @Transactional(readOnly = true)
    public List<TaskRecipientResponse> recipientsOf(TableTask task) {
        User target = task.getTargetUser();
        return switch (task.getAudience()) {
            case Candidates -> registrationsIn(task.getGameTable().getId(), TableRegistrationStatus.Candidate);
            case Players -> registrationsIn(task.getGameTable().getId(), TableRegistrationStatus.Player);
            // A target that stopped playing there stops being a recipient: the roster answers "who is
            // this being asked of", and somebody who left the table is not being asked anything.
            case Single -> target != null && holdsStatus(task.getGameTable().getId(), target.getId(), TableRegistrationStatus.Player)
                    ? List.of(taskMapper.toRecipient(target))
                    : List.of();
        };
    }

    /**
     * Whether somebody is among the people a task is addressed to.
     *
     * <p>The same rule {@link #recipientsOf} applies, asked about one person - which is what decides
     * whether an answer may be handed in at all.
     *
     * @param task    the task
     * @param userId  the person, always the actor from the token (#121)
     * @return true when the task is addressed to them
     */
    @Transactional(readOnly = true)
    public boolean isRecipient(TableTask task, String userId) {
        String gameTableId = task.getGameTable().getId();
        return switch (task.getAudience()) {
            case Candidates -> holdsStatus(gameTableId, userId, TableRegistrationStatus.Candidate);
            case Players -> holdsStatus(gameTableId, userId, TableRegistrationStatus.Player);
            case Single -> isTargetOf(task, userId) && holdsStatus(gameTableId, userId, TableRegistrationStatus.Player);
        };
    }

    /** The master's view of one task, with its counts read fresh. */
    private TaskResponse describe(TableTask task) {
        TaskSubmissionCount count = countsOf(List.of(task)).get(task.getId());
        return toResponse(
                task,
                count == null ? 0 : (int) count.submissions(),
                count == null ? 0 : (int) count.distinctPeople(),
                recipientsOf(task).size());
    }

    /** The mapper call, with the description sanitized on the way out as well as in (#62). */
    private TaskResponse toResponse(TableTask task, int submissionCount, int respondentCount, int recipientCount) {
        return taskMapper.toResponse(
                task, richTextSanitizer.sanitize(task.getDescription()), submissionCount, respondentCount, recipientCount);
    }

    /** The grouped count of a whole board in one round trip, keyed by task. */
    private Map<String, TaskSubmissionCount> countsOf(List<TableTask> tasks) {
        Map<String, TaskSubmissionCount> byTask = new HashMap<>();
        for (TaskSubmissionCount count : submissionRepository.countByTaskIds(tasks.stream().map(TableTask::getId).toList())) {
            byTask.put(count.taskId(), count);
        }
        return byTask;
    }

    /** Whether this reader may hand something in right now, which is what the button asks. */
    private boolean canSubmit(TableTask task, boolean isPlayer, boolean isCandidate, String actorId) {
        return switch (task.getAudience()) {
            case Candidates -> isCandidate;
            case Players -> isPlayer;
            case Single -> isPlayer && isTargetOf(task, actorId);
        };
    }

    private boolean isTargetOf(TableTask task, String userId) {
        User target = task.getTargetUser();
        return target != null && target.getId().equals(userId);
    }

    private List<TaskRecipientResponse> registrationsIn(String gameTableId, TableRegistrationStatus status) {
        return registrationRepository.findByGameTable_IdAndStatusOrderByCreatedAtAsc(gameTableId, status).stream()
                .map(TableRegistration::getUser)
                .map(taskMapper::toRecipient)
                .toList();
    }

    private boolean holdsStatus(String gameTableId, String userId, TableRegistrationStatus status) {
        return registrationRepository.existsByGameTable_IdAndUser_IdAndStatusIn(gameTableId, userId, List.of(status));
    }

    /**
     * The two ways the audience and the target can contradict each other, refused in both directions.
     *
     * <p>A {@code Single} task without a target addresses nobody, and a target on any other audience
     * is a value nothing would ever read - the kind of row that makes a later reader wonder which of
     * the two fields is the truth.
     *
     * <p>The target also has to actually play at the table. Otherwise a master could address material
     * to somebody who cannot see it, and the notification would land in the inbox of a person with no
     * way to answer.
     */
    private @Nullable User resolveTarget(GameTable gameTable, TaskAudience audience, @Nullable String targetUserId) {
        if (audience != TaskAudience.Single) {
            if (targetUserId != null) {
                throw new InvalidRequestException("A target user is only accepted when the audience is Single");
            }
            return null;
        }
        if (targetUserId == null) {
            throw new InvalidRequestException("A Single task must name the user it is addressed to");
        }
        if (!holdsStatus(gameTable.getId(), targetUserId, TableRegistrationStatus.Player)) {
            throw new InvalidRequestException("User " + targetUserId + " does not play at table " + gameTable.getId());
        }
        return userService.getById(targetUserId);
    }

    /** A task can only be tied to an evening of its own table - anything else is a row nothing can explain. */
    private @Nullable TableSession resolveSession(GameTable gameTable, @Nullable String tableSessionId) {
        if (tableSessionId == null) {
            return null;
        }
        TableSession session = sessionRepository.findById(tableSessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + tableSessionId));
        if (!session.getGameTable().getId().equals(gameTable.getId())) {
            throw new InvalidRequestException("Session " + tableSessionId + " belongs to another table");
        }
        return session;
    }

    /**
     * A task that takes neither text nor files asks for something nobody can hand in.
     *
     * <p>The database has the same {@code CHECK}, and that is the net rather than the rule: a
     * constraint violation surfaces as a 500 with a message about a constraint name, which is not an
     * answer anybody can act on.
     */
    private void requireAnswerable(boolean acceptsText, boolean acceptsFiles) {
        if (!acceptsText && !acceptsFiles) {
            throw new InvalidRequestException("A task has to accept text, files, or both");
        }
    }

    /**
     * The table, live and in a state where asking makes sense.
     *
     * <p>A table still in {@code Preparation} has nobody to ask, and a finished one has nobody left
     * to ask - in both cases the recipients resolve to an empty list, so the ask would be a row that
     * notifies nobody.
     */
    private GameTable requireLiveTable(String gameTableId) {
        GameTable gameTable = gameTableRepository.findById(gameTableId)
                .orElseThrow(() -> new NotFoundException("Table not found: " + gameTableId));
        if (gameTable.getStatus() == GameTableStatus.Deleted) {
            throw new NotFoundException("Table not found: " + gameTableId);
        }
        return gameTable;
    }

    /**
     * The membership gate (#17, #121, #135): a row in {@code masters}, not the {@code Master} role.
     *
     * @throws ForbiddenActionException if the actor does not run this table
     */
    private void requireMasterOf(String gameTableId, String actorId) {
        if (!masterService.isMasterOf(gameTableId, actorId)) {
            throw new ForbiddenActionException("User " + actorId + " does not run table " + gameTableId);
        }
    }
}
