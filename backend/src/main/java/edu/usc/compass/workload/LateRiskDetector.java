package edu.usc.compass.workload;

import edu.usc.compass.checkins.dao.CheckinDao;
import edu.usc.compass.common.Todo;
import edu.usc.compass.tasks.model.TaskModels.Task;
import edu.usc.compass.workload.dao.WorkloadDao;
import edu.usc.compass.workload.model.WorkloadModels.LateRiskStatus;

import java.sql.Connection;
import java.time.LocalDate;

/** DetectLateRisk (Detailed Design 3.2.4). Owner: Liv. Tests: Stress 14. */
public class LateRiskDetector {

    private final CheckinDao checkinDao;
    private final WorkloadDao workloadDao;

    public LateRiskDetector(CheckinDao checkinDao, WorkloadDao workloadDao) {
        this.checkinDao = checkinDao;
        this.workloadDao = workloadDao;
    }

    /**
     * SHARED CONTRACT (Adam shows the result on every task). Rules, in order:
     * Done -> NO_RISK; past due -> OVERDUE; due more than LATE_RISK_WINDOW_DAYS away -> NO_IMMEDIATE_RISK;
     * unresolved check-in blocker linked to the task -> AT_RISK_UNRESOLVED_BLOCKER;
     * effort points > assignees' remaining capacity before the due date -> AT_RISK_INSUFFICIENT_CAPACITY;
     * else DUE_SOON_ON_TRACK.
     * Stub returns UNKNOWN so the task board works before this is implemented.
     */
    public LateRiskStatus detectLateRisk(Connection c, Task task, LocalDate today) {
        Todo.noop("Liv", "LateRiskDetector.detectLateRisk");
        return LateRiskStatus.UNKNOWN;
    }
}
