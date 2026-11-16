package edu.usc.compass.workload;

import edu.usc.compass.workload.model.WorkloadModels.AlertStatus;

import java.math.BigDecimal;

/** Request bodies for workload routes (Detailed Design 3.5.4). Owner: Liv. */
public final class WorkloadDtos {

    private WorkloadDtos() {
    }

    /** hoursPerWeek 0-80. */
    public record ReportAvailabilityRequest(BigDecimal hoursPerWeek, boolean reportsOverload) {
    }

    /** ACKNOWLEDGED or RESOLVED. */
    public record UpdateAlertRequest(AlertStatus status) {
    }
}
