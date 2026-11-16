package edu.usc.compass.workload;

import edu.usc.compass.common.Todo;
import edu.usc.compass.common.db.Database;
import edu.usc.compass.common.jobs.Job;
import edu.usc.compass.workload.dao.WorkloadDao;

import java.time.Clock;
import java.time.Duration;

/**
 * Daily sweep: for every active project run DriftDetector, and LateRiskDetector for incomplete tasks due
 * within LATE_RISK_WINDOW_DAYS (raising/resolving LATE_RISK alerts). Owner: Liv. Tests: Stress 14.
 * Must be safe when runs overlap (AlertDao.insertIfNoneOpen handles duplicates).
 */
public class RiskDetectionJob implements Job {

    private final Database db;
    private final WorkloadDao workloadDao;
    private final DriftDetector drift;
    private final LateRiskDetector lateRisk;
    private final AlertService alerts;
    private final Clock clock;

    public RiskDetectionJob(Database db, WorkloadDao workloadDao, DriftDetector drift, LateRiskDetector lateRisk,
                            AlertService alerts, Clock clock) {
        this.db = db;
        this.workloadDao = workloadDao;
        this.drift = drift;
        this.lateRisk = lateRisk;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "risk-detection";
    }

    @Override
    public Duration interval() {
        return Duration.ofHours(24);
    }

    @Override
    public void run() {
        Todo.noop("Liv", "RiskDetectionJob.run");
    }
}
