package com.hms.service.help;

import com.hms.entity.HelpFeedback;
import com.hms.entity.HelpFeedback.Kind;
import com.hms.repository.HelpFeedbackRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.security.UserAuthenticationDetails;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Feedback on in-app help: answers rated, and questions the assistant could not answer.
 *
 * Staff may type anything into help, including patient details. A question is therefore scrubbed
 * before it is stored — email addresses and any run of three or more digits (phone numbers, ids,
 * dates) are removed and it is cut to a short length — and no name is kept with it.
 */
@Service
public class HelpFeedbackService {

    /** Per login per day; beyond this, feedback is quietly ignored. */
    static final int DAILY_LIMIT = 200;
    static final int MAX_QUESTION = 160;

    /** Input is cut to this length before any pattern runs, so no pattern can be made slow. */
    static final int MAX_INPUT = 400;
    private static final String NOT_HELPFUL_KEY = "notHelpful";
    private static final ZoneId ZONE = ZoneId.systemDefault();
    // Possessive quantifiers: the patterns never backtrack.
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]++@[\\w-]++(?:\\.[\\w-]++)++");
    /** Three or more digits, optionally split by single separators: phones, ids, dates. */
    private static final Pattern NUMBERS = Pattern.compile("\\d(?:[\\s/.:-]?+\\d){2,}+");
    private static final Pattern SPACES = Pattern.compile("\\s++");
    private static final Pattern ID = Pattern.compile("^[a-z0-9-]{1,60}$");
    private static final Pattern SCREEN = Pattern.compile("^[a-z0-9:_-]{1,60}$");

    private final HelpFeedbackRepository repository;
    private final SecurityContextHelper securityHelper;

    public HelpFeedbackService(HelpFeedbackRepository repository, SecurityContextHelper securityHelper) {
        this.repository = repository;
        this.securityHelper = securityHelper;
    }

    /** Records one piece of feedback from the signed-in user. Returns false if it was ignored. */
    @Transactional
    public boolean submit(String kind, String entryId, String question, String screen) {
        Kind k = parseKind(kind);
        UserAuthenticationDetails me = securityHelper.getCurrentUserDetails();
        String scope = HelpProgressService.scopeKey(me);
        if (repository.countByScopeKeyAndCreatedAtAfter(scope, LocalDateTime.now(ZONE).minusDays(1)) >= DAILY_LIMIT) {
            return false;
        }
        HelpFeedback f = new HelpFeedback();
        f.setKind(k.name());
        f.setHospitalId(me.getHospitalId());
        f.setScopeKey(scope);
        f.setRole(me.getRole() == null ? "UNKNOWN" : me.getRole());
        f.setScreen(screen != null && SCREEN.matcher(screen).matches() ? screen : null);
        if (k == Kind.UNANSWERED) {
            String clean = scrub(question);
            if (clean.isEmpty()) throw new IllegalArgumentException("A question is required");
            f.setQuestion(clean);
            f.setNormalized(normalise(clean));
        } else {
            if (entryId == null || !ID.matcher(entryId).matches()) {
                throw new IllegalArgumentException("Which answer was rated is required");
            }
            f.setEntryId(entryId);
        }
        repository.save(f);
        return true;
    }

    /** What the Super Admin sees: open questions, most asked first, and how each answer is rated. */
    @Transactional(readOnly = true)
    public Map<String, Object> insights(int days) {
        int period = Math.max(1, Math.min(days, 365));
        LocalDateTime since = LocalDateTime.now(ZONE).minusDays(period);

        List<Object[]> open = repository.openQuestionsSince(since, PageRequest.of(0, 100));
        List<String> keys = open.stream().map(r -> (String) r[0]).toList();
        Map<String, TreeSet<String>> roles = new LinkedHashMap<>();
        if (!keys.isEmpty()) {
            for (Object[] r : repository.rolesFor(since, keys)) {
                roles.computeIfAbsent((String) r[0], x -> new TreeSet<>()).add((String) r[1]);
            }
        }
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Object[] r : open) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("key", r[0]);
            q.put("question", r[1]);
            q.put("count", ((Number) r[2]).longValue());
            q.put("lastAsked", r[3]);
            q.put("roles", roles.getOrDefault(r[0], new TreeSet<>()));
            questions.add(q);
        }

        List<Map<String, Object>> answers = new ArrayList<>();
        for (Object[] r : repository.ratingsSince(since)) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("entryId", r[0]);
            a.put("helpful", ((Number) r[1]).longValue());
            a.put(NOT_HELPFUL_KEY, ((Number) r[2]).longValue());
            answers.add(a);
        }
        answers.sort((a, b) -> Long.compare((long) b.get(NOT_HELPFUL_KEY), (long) a.get(NOT_HELPFUL_KEY)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", period);
        out.put("questions", questions);
        out.put("answers", answers);
        return out;
    }

    /** Marks every report of this question as answered, so it leaves the list. */
    @Transactional
    public int resolve(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Which question is required");
        return repository.resolve(key);
    }

    static String scrub(String question) {
        if (question == null) return "";
        String s = question.length() > MAX_INPUT ? question.substring(0, MAX_INPUT) : question;
        s = EMAIL.matcher(s).replaceAll("[email]");
        s = NUMBERS.matcher(s).replaceAll("[number]");
        s = SPACES.matcher(s).replaceAll(" ").trim();
        return s.length() > MAX_QUESTION ? s.substring(0, MAX_QUESTION).trim() : s;
    }

    static String normalise(String question) {
        String s = question.toLowerCase().replaceAll("\\[(email|number)]", " ")
                .replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > MAX_QUESTION ? s.substring(0, MAX_QUESTION) : s;
    }

    private static Kind parseKind(String kind) {
        try {
            return Kind.valueOf(kind == null ? "" : kind.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown feedback kind");
        }
    }
}
