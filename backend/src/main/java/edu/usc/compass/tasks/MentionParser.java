package edu.usc.compass.tasks;

import edu.usc.compass.common.Todo;
import edu.usc.compass.teams.model.TeamModels.Membership;

import java.util.List;
import java.util.Set;

/** Finds @mentions in comment text on the server. Owner: Adam. Tests: Stress 3. */
public class MentionParser {

    /**
     * Returns the user ids of team members mentioned as @DisplayName (decide and document the exact rule,
     * e.g. @ followed by a display name with spaces removed, case-insensitive). Unknown names are ignored;
     * each user appears at most once.
     */
    public Set<Long> findMentionedUserIds(String commentText, List<Membership> teamMembers) {
        throw Todo.of("Adam", "MentionParser.findMentionedUserIds");
    }
}
