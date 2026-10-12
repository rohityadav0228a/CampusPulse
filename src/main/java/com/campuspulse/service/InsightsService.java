package com.campuspulse.service;

import com.campuspulse.dto.insights.AdminInsightsData;
import com.campuspulse.dto.insights.CategoryInsight;
import com.campuspulse.dto.insights.DepartmentBacklog;
import com.campuspulse.dto.insights.PeriodComparison;
import com.campuspulse.dto.insights.RecommendationItem;
import com.campuspulse.dto.insights.RecommendationPriority;
import com.campuspulse.dto.insights.SlaSummary;
import com.campuspulse.dto.insights.StatusDistribution;
import com.campuspulse.dto.insights.TrendDirection;
import com.campuspulse.model.ComplaintStatus;
import com.campuspulse.repository.ComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service calculating database-backed grievance insights, comparative period trends,
 * department unresolved backlogs, and evidence-based administrative recommendations.
 * Avoids loading complete Complaint entities into memory by utilizing JPA aggregate queries.
 */
@Service
public class InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);
    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM dd", Locale.US);

    private static final List<ComplaintStatus> UNRESOLVED_STATUSES = List.of(
            ComplaintStatus.SUBMITTED,
            ComplaintStatus.UNDER_REVIEW,
            ComplaintStatus.IN_PROGRESS
    );

    private final ComplaintRepository complaintRepository;
    private final ComplaintService complaintService;
    private final SlaEscalationService slaEscalationService;

    public InsightsService(ComplaintRepository complaintRepository, ComplaintService complaintService) {
        this(complaintRepository, complaintService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InsightsService(
            ComplaintRepository complaintRepository,
            ComplaintService complaintService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SlaEscalationService slaEscalationService) {
        this.complaintRepository = complaintRepository;
        this.complaintService = complaintService;
        this.slaEscalationService = slaEscalationService;
    }

    /**
     * Computes the complete insights dataset, period comparison, backlog metrics,
     * SLA compliance indicators, and rule-based recommendations for the administrative dashboard.
     *
     * @param timeframeDays number of days for the recent evaluation window (default 14)
     * @return fully populated AdminInsightsData object
     */
    @Transactional(readOnly = true)
    public AdminInsightsData getInsightsData(Integer timeframeDays) {
        int windowDays = (timeframeDays != null && timeframeDays > 0) ? timeframeDays : 14;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(windowDays);
        LocalDateTime previousStart = now.minusDays(windowDays * 2L);
        LocalDateTime previousEnd = currentStart;

        // 1. Status Distribution & Unresolved Count
        StatusDistribution statusDist = computeStatusDistribution();

        // 2. Period Volume Comparison
        PeriodComparison comparison = computePeriodComparison(windowDays, currentStart, now, previousStart, previousEnd);

        // 3. Category Volume & Trend Breakdown
        List<CategoryInsight> categories = computeCategoryInsights(statusDist.totalCount(), currentStart, now, previousStart, previousEnd);

        // 4. Department Backlog Analysis
        List<DepartmentBacklog> backlogs = computeDepartmentBacklogs(statusDist.unresolvedCount());
        long highRiskCount = backlogs.stream().filter(DepartmentBacklog::highBacklogRisk).count();

        // 5. Active SLA Compliance & Overdue Status
        SlaSummary slaSummary = (slaEscalationService != null)
                ? slaEscalationService.getSlaSummary()
                : SlaSummary.of(0, 0, 0, 0, 0);

        // 6. Generate Evidence-Based Recommendations
        List<RecommendationItem> recommendations = generateRecommendations(
                statusDist,
                comparison,
                categories,
                backlogs,
                highRiskCount,
                slaSummary
        );

        boolean hasData = statusDist.totalCount() > 0;

        return new AdminInsightsData(
                windowDays,
                comparison,
                statusDist,
                categories,
                backlogs,
                recommendations,
                hasData,
                highRiskCount,
                slaSummary
        );
    }

    /**
     * Computes overall status distribution via database aggregation.
     */
    private StatusDistribution computeStatusDistribution() {
        long total = complaintRepository.countWithFilters(null, null, null);
        if (total == 0) {
            return StatusDistribution.of(0, 0, 0, 0, 0);
        }

        List<Object[]> statusRows = complaintRepository.countGroupByStatus(null, null);
        Map<ComplaintStatus, Long> countsByStatus = new HashMap<>();
        for (Object[] row : statusRows) {
            if (row != null && row.length >= 2 && row[0] instanceof ComplaintStatus st) {
                long cnt = ((Number) row[1]).longValue();
                countsByStatus.put(st, cnt);
            }
        }

        long submitted = countsByStatus.getOrDefault(ComplaintStatus.SUBMITTED, 0L);
        long underReview = countsByStatus.getOrDefault(ComplaintStatus.UNDER_REVIEW, 0L);
        long inProgress = countsByStatus.getOrDefault(ComplaintStatus.IN_PROGRESS, 0L);
        long resolved = countsByStatus.getOrDefault(ComplaintStatus.RESOLVED, 0L);

        return StatusDistribution.of(total, submitted, underReview, inProgress, resolved);
    }

    /**
     * Compares complaint counts between current period [currentStart, currentEnd)
     * and previous comparable period [previousStart, previousEnd).
     */
    private PeriodComparison computePeriodComparison(
            int windowDays,
            LocalDateTime currentStart,
            LocalDateTime currentEnd,
            LocalDateTime previousStart,
            LocalDateTime previousEnd) {

        long currentCount = complaintRepository.countBetween(currentStart, currentEnd);
        long previousCount = complaintRepository.countBetween(previousStart, previousEnd);
        long absoluteChange = currentCount - previousCount;

        String currentLabel = currentStart.format(DATE_LABEL_FORMAT) + " – " + currentEnd.format(DATE_LABEL_FORMAT);
        String previousLabel = previousStart.format(DATE_LABEL_FORMAT) + " – " + previousEnd.format(DATE_LABEL_FORMAT);

        Double percentageChange;
        TrendDirection direction;
        String summaryText;

        if (currentCount == 0 && previousCount == 0) {
            percentageChange = 0.0;
            direction = TrendDirection.STABLE;
            summaryText = String.format(Locale.US, "0 complaints in the past %d days (stable / no activity)", windowDays);
        } else if (previousCount == 0) {
            percentageChange = null;
            direction = TrendDirection.INCREASED;
            summaryText = String.format(Locale.US, "+%d complaints (new volume, no complaints in prior %d days)", currentCount, windowDays);
        } else {
            double rawPct = ((currentCount - previousCount) * 100.0) / previousCount;
            percentageChange = Math.round(rawPct * 10.0) / 10.0;
            if (percentageChange > 0.0) {
                direction = TrendDirection.INCREASED;
                summaryText = String.format(Locale.US, "+%d complaints (%+.1f%% vs prior %d days)", absoluteChange, percentageChange, windowDays);
            } else if (percentageChange < 0.0) {
                direction = TrendDirection.DECREASED;
                summaryText = String.format(Locale.US, "%d complaints (%+.1f%% vs prior %d days)", absoluteChange, percentageChange, windowDays);
            } else {
                direction = TrendDirection.STABLE;
                summaryText = String.format(Locale.US, "Unchanged complaint volume (%d complaints vs prior %d days)", currentCount, windowDays);
            }
        }

        return new PeriodComparison(
                windowDays,
                currentCount,
                previousCount,
                absoluteChange,
                percentageChange,
                direction,
                summaryText,
                currentLabel,
                previousLabel
        );
    }

    /**
     * Computes category totals and comparative window volume.
     */
    private List<CategoryInsight> computeCategoryInsights(
            long totalComplaints,
            LocalDateTime currentStart,
            LocalDateTime currentEnd,
            LocalDateTime previousStart,
            LocalDateTime previousEnd) {

        List<Object[]> allCategoryRows = complaintRepository.countGroupByCategory(null, null, null);
        Map<String, Long> totalMap = new LinkedHashMap<>();

        for (String cat : complaintService.getAllowedCategories()) {
            totalMap.put(cat, 0L);
        }
        for (Object[] row : allCategoryRows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                totalMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        // Windowed counts for current and previous periods
        List<Object[]> currentWindowRows = complaintRepository.countGroupByCategoryBetween(currentStart, currentEnd);
        Map<String, Long> currentMap = new HashMap<>();
        for (Object[] row : currentWindowRows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                currentMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        List<Object[]> prevWindowRows = complaintRepository.countGroupByCategoryBetween(previousStart, previousEnd);
        Map<String, Long> prevMap = new HashMap<>();
        for (Object[] row : prevWindowRows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                prevMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        // Identify highest category count
        long maxCount = 0;
        for (Long cnt : totalMap.values()) {
            if (cnt > maxCount) {
                maxCount = cnt;
            }
        }

        List<CategoryInsight> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : totalMap.entrySet()) {
            String cat = entry.getKey();
            long catTotal = entry.getValue();
            boolean isTop = (maxCount > 0 && catTotal == maxCount);
            list.add(CategoryInsight.of(
                    cat,
                    catTotal,
                    totalComplaints,
                    currentMap.getOrDefault(cat, 0L),
                    prevMap.getOrDefault(cat, 0L),
                    isTop
            ));
        }

        // Sort descending by total volume, then by name
        list.sort((a, b) -> {
            int cmp = Long.compare(b.totalCount(), a.totalCount());
            return cmp != 0 ? cmp : a.category().compareTo(b.category());
        });

        return list;
    }

    /**
     * Computes unresolved complaint backlogs grouped by department.
     */
    private List<DepartmentBacklog> computeDepartmentBacklogs(long totalCampusUnresolved) {
        List<Object[]> unresolvedRows = complaintRepository.countUnresolvedGroupByDepartment(UNRESOLVED_STATUSES);
        Map<String, Long> unresolvedMap = new HashMap<>();
        for (Object[] row : unresolvedRows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                unresolvedMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        List<Object[]> totalRows = complaintRepository.countGroupByDepartment(null, null, null);
        Map<String, Long> totalMap = new HashMap<>();
        for (Object[] row : totalRows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                totalMap.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        List<DepartmentBacklog> list = new ArrayList<>();
        for (String dept : complaintService.getAllowedDepartments()) {
            long unresolved = unresolvedMap.getOrDefault(dept, 0L);
            long total = totalMap.getOrDefault(dept, 0L);
            list.add(DepartmentBacklog.of(dept, unresolved, total, totalCampusUnresolved));
        }

        // Sort descending by unresolved count, then total count, then department name
        list.sort((a, b) -> {
            int cmp = Long.compare(b.unresolvedCount(), a.unresolvedCount());
            if (cmp != 0) return cmp;
            int cmpTotal = Long.compare(b.totalCount(), a.totalCount());
            return cmpTotal != 0 ? cmpTotal : a.department().compareTo(b.department());
        });

        return list;
    }

    /**
     * Evaluates measured operational metrics against transparent, rule-based recommendation guidelines.
     */
    List<RecommendationItem> generateRecommendations(
            StatusDistribution statusDist,
            PeriodComparison comparison,
            List<CategoryInsight> categories,
            List<DepartmentBacklog> backlogs,
            long highRiskCount) {
        return generateRecommendations(statusDist, comparison, categories, backlogs, highRiskCount, null);
    }

    private List<RecommendationItem> generateRecommendations(
            StatusDistribution statusDist,
            PeriodComparison comparison,
            List<CategoryInsight> categories,
            List<DepartmentBacklog> backlogs,
            long highRiskCount,
            SlaSummary slaSummary) {

        List<RecommendationItem> recs = new ArrayList<>();

        // Handle empty or minimal database
        if (statusDist.totalCount() == 0) {
            recs.add(new RecommendationItem(
                    "REC-EMPTY-DATA",
                    "Grievance Baseline Initialization",
                    RecommendationPriority.INFO,
                    "System Notice",
                    "No grievance records are currently registered in the database.",
                    "Total registered complaints: 0. Aggregated trend analysis and workload forecasting will activate once complaints are submitted.",
                    "Encourage campus students and faculty to utilize the CampusPulse grievance registration portal for campus maintenance, academic, and hostel concerns.",
                    List.of(
                            "Share the public complaint submission portal link across student batch communication groups.",
                            "Confirm administrative users have assigned credentials for logging in and reviewing incoming tickets.",
                            "Verify that automated notification settings are configured for status updates."
                    )
            ));
            return recs;
        }

        // 0. RULE: SLA Response / Resolution Overdue Breaches
        if (slaSummary != null && slaSummary.isBreached()) {
            recs.add(new RecommendationItem(
                    "REC-SLA-BREACH",
                    "Overdue SLA Response & Resolution Breaches Detected",
                    RecommendationPriority.HIGH,
                    "SLA & Escalations",
                    "Active grievances have exceeded initial response or resolution deadlines and require prompt administrative attention.",
                    String.format(Locale.US,
                            "Currently %d response overdue and %d resolution overdue complaints out of %d active unresolved cases (Compliance rate: %.1f%%).",
                            slaSummary.responseOverdueCount(),
                            slaSummary.resolutionOverdueCount(),
                            slaSummary.activeUnresolvedCount(),
                            slaSummary.complianceRate()
                    ),
                    "Direct immediate attention to escalated complaints via the Admin Complaints dashboard.",
                    List.of(
                            "Filter by 'OVERDUE' on the Admin Complaints dashboard to identify tickets requiring intervention.",
                            "Verify department email notifications were dispatched via the Notification Outbox.",
                            "Follow up with responsible departmental teams to record initial review or resolve cases."
                    )
            ));
        }

        // 1. RULE: Department Unresolved Backlogs
        for (DepartmentBacklog backlog : backlogs) {
            if (backlog.highBacklogRisk()) {
                RecommendationPriority priority = (backlog.unresolvedCount() >= 5 || backlog.campusSharePercentage() >= 40.0)
                        ? RecommendationPriority.HIGH
                        : RecommendationPriority.MEDIUM;

                String deptSlug = backlog.department().replaceAll("[^a-zA-Z0-9]", "_").toUpperCase(Locale.ROOT);
                recs.add(new RecommendationItem(
                        "REC-BACKLOG-" + deptSlug,
                        "Backlog Concentration in " + backlog.department(),
                        priority,
                        "Workload Management",
                        "A disproportionate volume of active, unresolved grievances is concentrated within " + backlog.department() + ".",
                        String.format(Locale.US,
                                "%s holds %d unresolved complaints (%.1f%% of all campus unresolved grievances), with a departmental unresolved rate of %.1f%% (%d of %d complaints pending).",
                                backlog.department(),
                                backlog.unresolvedCount(),
                                backlog.campusSharePercentage(),
                                backlog.backlogPercentage(),
                                backlog.unresolvedCount(),
                                backlog.totalCount()
                        ),
                        "Coordinate directly with " + backlog.department() + " administration to conduct a grievance audit, remove operational roadblocks, and accelerate pending resolutions.",
                        List.of(
                                "Prioritize oldest submitted tickets assigned to " + backlog.department() + " exceeding standard SLA windows.",
                                "Verify if staff capacity, spare parts, or vendor contracts are impeding resolution.",
                                "Convene a bi-weekly coordination check-in with the department grievance representative."
                        )
                ));
            }
        }

        // 2. RULE: Volume Surge Detection
        if (comparison.direction() == TrendDirection.INCREASED && comparison.currentCount() >= 3) {
            if (comparison.percentageChange() == null || comparison.percentageChange() >= 25.0) {
                RecommendationPriority priority = (comparison.percentageChange() != null && comparison.percentageChange() >= 50.0)
                        || comparison.absoluteChange() >= 5
                        ? RecommendationPriority.HIGH
                        : RecommendationPriority.MEDIUM;

                String changeDesc = (comparison.percentageChange() != null)
                        ? String.format(Locale.US, "%+.1f%%", comparison.percentageChange())
                        : String.format(Locale.US, "+%d complaints (new volume)", comparison.currentCount());

                recs.add(new RecommendationItem(
                        "REC-SURGE-ALERT",
                        "Recent Grievance Inflow Surge (" + changeDesc + " in last " + comparison.windowDays() + " days)",
                        priority,
                        "Volume Trends",
                        "Campus complaint submissions have accelerated compared to the preceding comparable window.",
                        String.format(Locale.US,
                                "Current %d-day window recorded %d complaints versus %d in the prior period (%s).",
                                comparison.windowDays(),
                                comparison.currentCount(),
                                comparison.previousCount(),
                                comparison.summaryText()
                        ),
                        "Investigate whether campus-wide infrastructure disruptions, seasonal academic deadlines, or hostel service shifts coincide with this surge.",
                        List.of(
                                "Examine category distribution for the current period to isolate the primary driver of new complaints.",
                                "Check if multiple grievances describe identical localized incidents (e.g. Wi-Fi router failures or dining hall disruptions).",
                                "Issue a proactive campus announcement if the surge reflects a recognized, campus-wide maintenance event."
                        )
                ));
            }
        }

        // 3. RULE: Volume Decline / Calming Trend
        if (comparison.direction() == TrendDirection.DECREASED
                && comparison.previousCount() >= 3
                && comparison.percentageChange() != null
                && comparison.percentageChange() <= -25.0) {

            recs.add(new RecommendationItem(
                    "REC-VOLUME-DECLINE",
                    "Moderating Grievance Inflow (" + comparison.formattedPercentage() + " in last " + comparison.windowDays() + " days)",
                    RecommendationPriority.INFO,
                    "Volume Trends",
                    "Complaint submission frequency has noticeably calmed compared to the previous period.",
                    String.format(Locale.US,
                            "Recorded %d complaints over the past %d days versus %d in the prior period (%s).",
                            comparison.currentCount(),
                            comparison.windowDays(),
                            comparison.previousCount(),
                            comparison.summaryText()
                    ),
                    "Observe whether recent remediations, maintenance repairs, or policy adjustments have contributed to the decline, while ensuring student submission channels remain accessible.",
                    List.of(
                            "Confirm that student complaint submission forms remain open and accessible.",
                            "Verify student tracking feedback on recently closed grievances to ensure lasting satisfaction.",
                            "Document preventative maintenance practices that helped lower recurrence."
                    )
            ));
        }

        // 4. RULE: Category Dominance / Friction Point
        if (statusDist.totalCount() >= 4 && !categories.isEmpty()) {
            CategoryInsight topCat = categories.get(0);
            if (topCat.totalCount() > 0 && topCat.sharePercentage() >= 35.0) {
                recs.add(new RecommendationItem(
                        "REC-CAT-DOMINANCE",
                        "Dominant Grievance Category: " + topCat.category() + " (" + topCat.sharePercentage() + "%)",
                        RecommendationPriority.MEDIUM,
                        "Category Concentration",
                        "Student grievances are heavily concentrated within " + topCat.category() + ", highlighting a recurring operational friction point.",
                        String.format(Locale.US,
                                "%s accounts for %d out of %d total campus complaints (%.1f%% of overall grievance volume).",
                                topCat.category(),
                                topCat.totalCount(),
                                statusDist.totalCount(),
                                topCat.sharePercentage()
                        ),
                        "Initiate a root-cause operational review for " + topCat.category() + " rather than treating individual complaints as isolated incidents.",
                        List.of(
                                "Audit common themes and locations mentioned in " + topCat.category() + " grievance records.",
                                "Formulate proactive preventative maintenance plans or policy updates to prevent recurrence.",
                                "Track category volume on a weekly basis to verify whether preventative actions reduce complaint frequency."
                        )
                ));
            }
        }

        // 5. RULE: Intake Triage Delay (stalled in SUBMITTED)
        if (statusDist.submittedCount() >= 3) {
            double submittedRate = (statusDist.submittedCount() * 100.0) / statusDist.totalCount();
            if (submittedRate >= 30.0 || statusDist.submittedCount() >= 5) {
                RecommendationPriority priority = (statusDist.submittedCount() >= 5)
                        ? RecommendationPriority.HIGH
                        : RecommendationPriority.MEDIUM;

                recs.add(new RecommendationItem(
                        "REC-TRIAGE-DELAY",
                        "Intake Review Bottleneck: " + statusDist.submittedCount() + " Complaints Awaiting Review",
                        priority,
                        "Triage Velocity",
                        "A substantial backlog of newly submitted grievances has not yet transitioned to active administrative review.",
                        String.format(Locale.US,
                                "%d complaints are currently in SUBMITTED status (%.1f%% of all registered grievances).",
                                statusDist.submittedCount(),
                                Math.round(submittedRate * 10.0) / 10.0
                        ),
                        "Institute a regular morning triage protocol so newly submitted complaints transition to 'Under Review' within 24 to 48 hours of submission.",
                        List.of(
                                "Designate a duty officer to review and categorize pending submissions daily.",
                                "Assign unassigned grievances to appropriate department coordinators immediately upon review.",
                                "Configure status update notifications to confirm receipt to student complainants."
                        )
                ));
            }
        }

        // 6. RULE: Strong Overall Resolution Performance
        if (statusDist.totalCount() >= 5 && statusDist.resolutionRate() >= 70.0 && highRiskCount == 0) {
            recs.add(new RecommendationItem(
                    "REC-HIGH-RESOLUTION",
                    "Strong Grievance Resolution Throughput (" + statusDist.resolutionRate() + "%)",
                    RecommendationPriority.POSITIVE,
                    "Resolution Performance",
                    "Campus administrative departments are maintaining an effective resolution rate across reported grievances.",
                    String.format(Locale.US,
                            "%d out of %d registered complaints have reached RESOLVED status (%.1f%% resolution rate) with manageable departmental backlogs.",
                            statusDist.resolvedCount(),
                            statusDist.totalCount(),
                            statusDist.resolutionRate()
                    ),
                    "Maintain current resolution workflows, document effective department practices, and archive completed cases for semester quality reporting.",
                    List.of(
                            "Recognize department coordinators demonstrating rapid resolution turnaround.",
                            "Conduct periodic audits of resolved complaints to ensure student satisfaction.",
                            "Maintain current status update cadences to sustain student trust."
                    )
            ));
        }

        // 7. Fallback: If no recommendations were triggered, provide an informational baseline overview
        if (recs.isEmpty()) {
            recs.add(new RecommendationItem(
                    "REC-STABLE-BASELINE",
                    "Balanced Grievance Distribution & Monitoring",
                    RecommendationPriority.INFO,
                    "Operational Baseline",
                    "Current grievance volume and department workloads are balanced without anomalous concentrations or severe backlogs.",
                    String.format(Locale.US,
                            "Total complaints: %d (Unresolved: %d, Resolved: %d, Resolution Rate: %.1f%%). No single department or category exceeds anomaly thresholds.",
                            statusDist.totalCount(),
                            statusDist.unresolvedCount(),
                            statusDist.resolvedCount(),
                            statusDist.resolutionRate()
                    ),
                    "Continue routine monitoring of incoming complaints and status updates across departments.",
                    List.of(
                            "Maintain regular weekly administrative reviews of active grievances.",
                            "Encourage department leads to keep complaint status histories updated promptly.",
                            "Re-check insights periodically as new operational records accumulate."
                    )
            ));
        }

        return recs;
    }
}
