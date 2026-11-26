package edu.usc.compass.workload;

import edu.usc.compass.common.Rules;
import edu.usc.compass.common.Todo;
import edu.usc.compass.tasks.model.TaskModels.AssignmentRow;
import edu.usc.compass.tasks.model.TaskModels.TaskStatus;
import edu.usc.compass.workload.model.WorkloadModels.Availability;
import edu.usc.compass.workload.model.WorkloadModels.MemberWorkload;
import edu.usc.compass.workload.model.WorkloadModels.WorkloadFlag;
import edu.usc.compass.workload.model.WorkloadModels.WorkloadResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure workload math, no database (Detailed Design 3.2.3). Owner: Liv.
 * Tests: White box 9, 13, 14; Stress 13.
 */
public class WorkloadCalculator {

    /**
     * 1. Ignore Done tasks. Points from EffortLevel.points().
     * 2. Split each task's points among assignees: equally, or by allocationPercent when set (totals 100).
     * 3. assignedShare = member points / total points; expectedShare = member hours / total hours.
     * 4. OVERLOAD if assignedShare - expectedShare > Rules.OVERLOAD_THRESHOLD (strictly greater) or the member
     *    reports overload; AVAILABLE_CAPACITY if expectedShare - assignedShare > threshold; else BALANCED.
     * 5. Total points or total hours of zero -> WorkloadResult(insufficientData = true, empty list).
     *
     * @param displayNames userId -> display name for the result rows
     */
    public WorkloadResult calculate(List<AssignmentRow> activeAssignments, List<Availability> availability,
                                    Map<Long, String> displayNames) {
        
    	Map<Long, Double> pointsByUser = new HashMap<>();
        Map<Long, Availability> availabilityByUser = new HashMap<>();
        Set<Long> userIds = new LinkedHashSet<>();

        double totalPoints = 0;
        double totalHours = 0;
        
        for(AssignmentRow row : activeAssignments) {
        	if(row.status() == TaskStatus.DONE) {
        		continue;
        	}
        	
        	double taskPoints = row.effort().points();
            double memberPoints;

            if (row.allocationPercent() != null) {
                memberPoints = taskPoints * row.allocationPercent().doubleValue() / 100.0;
            } 
            else {
                memberPoints = taskPoints / row.assigneeCount();
            }

            long userId = row.userId();

            double previousPoints = pointsByUser.getOrDefault(userId, 0.0);
            pointsByUser.put(userId, previousPoints + memberPoints);

            userIds.add(userId);
            totalPoints += memberPoints;
        }
        
     // Store availability and calculate total available hours.
        for (Availability member : availability) {
        	
            availabilityByUser.put(member.userId(), member);
            userIds.add(member.userId());
            totalHours += member.hoursPerWeek().doubleValue();
        }

        if (totalPoints == 0 || totalHours == 0) {
            return new WorkloadResult(true, List.of());
        }

        List<MemberWorkload> members = new ArrayList<>();

        for (long userId : userIds) {
            double assignedPoints = pointsByUser.getOrDefault(userId, 0.0);

            Availability memberAvailability = availabilityByUser.get(userId);

            double hours = 0;
            boolean reportsOverload = false;

            if (memberAvailability != null) {
                hours = memberAvailability.hoursPerWeek().doubleValue();
                reportsOverload = memberAvailability.reportsOverload();
            }

            double assignedShare = assignedPoints / totalPoints;
            double expectedShare = hours / totalHours;

            WorkloadFlag flag;

            if (reportsOverload || assignedShare - expectedShare > Rules.OVERLOAD_THRESHOLD) {
                flag = WorkloadFlag.OVERLOAD;
            } 
            else if (expectedShare - assignedShare > Rules.OVERLOAD_THRESHOLD) {
                flag = WorkloadFlag.AVAILABLE_CAPACITY;
            } 
            else {
                flag = WorkloadFlag.BALANCED;
            }

            members.add(new MemberWorkload(
                    userId,
                    displayNames.get(userId),
                    assignedPoints,
                    assignedShare,
                    expectedShare,
                    flag));
        }

        return new WorkloadResult(false, members);
        
    }
}
