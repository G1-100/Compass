package edu.usc.compass.workload.model;

import edu.usc.compass.common.Severity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Workload and risk records (tables member_availability, workload_alerts). Owner: Liv. */
public final class WorkloadModels {

    private WorkloadModels() {
    }

    public record Availability(long projectId, long userId, BigDecimal hoursPerWeek, boolean reportsOverload, Instant updatedAt) {
    }

    public enum WorkloadFlag {
        OVERLOAD, AVAILABLE_CAPACITY, BALANCED
    }

    /** Shares are 0.0-1.0. */
    public record MemberWorkload(long userId, String displayName, double assignedPoints, double assignedShare,
                                 double expectedShare, WorkloadFlag flag) {
    }

    /** insufficientData = total effort or total availability is zero (never divide by zero). */
    public record WorkloadResult(boolean insufficientData, List<MemberWorkload> members) {
    }

    public enum AlertType {
        OVERLOAD, AVAILABLE_CAPACITY, WORKLOAD_DRIFT, LATE_RISK
    }

    public enum AlertStatus {
        OPEN, ACKNOWLEDGED, RESOLVED
    }

    public record Alert(long alertId, long projectId, Long userId, Long taskId, AlertType alertType, Severity severity,
                        String reason, String ruleVersion, AlertStatus status, Instant createdAt, Long acknowledgedBy,
                        Instant acknowledgedAt, Instant resolvedAt) {
    }

    /** Result of DetectLateRisk. SHARED — shown on every task. UNKNOWN = not calculated (stub). */
    public enum LateRiskStatus {
        NO_RISK, NO_IMMEDIATE_RISK, OVERDUE, AT_RISK_UNRESOLVED_BLOCKER, AT_RISK_INSUFFICIENT_CAPACITY,
        DUE_SOON_ON_TRACK, UNKNOWN
    }
}
