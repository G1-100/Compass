package edu.usc.compass.projects;

import edu.usc.compass.projects.model.ProjectModels.ProjectStatus;

import java.time.LocalDate;

/** Request bodies for project routes (Detailed Design 3.5.3). Owner: Solomon. */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(String name, String description, LocalDate targetDate, ProjectStatus status) {
    }

    /** PATCH: null fields are left unchanged. */
    public record UpdateProjectRequest(String name, String description, LocalDate targetDate, ProjectStatus status, int version) {
    }
}
