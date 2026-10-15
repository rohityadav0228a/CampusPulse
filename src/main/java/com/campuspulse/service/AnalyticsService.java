package com.campuspulse.service;

import com.campuspulse.dto.analytics.AnalyticsDashboardData;
import com.campuspulse.dto.analytics.AnalyticsSummary;
import com.campuspulse.dto.analytics.CategoryBreakdown;
import com.campuspulse.dto.analytics.DailyTrendPoint;
import com.campuspulse.dto.analytics.DepartmentBreakdown;
import com.campuspulse.dto.analytics.FeedbackMetrics;
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
 * Service managing analytics calculations, database aggregations,
 * trend point generation, and filtering for the administrative dashboard.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM dd", Locale.US);

    private final ComplaintRepository complaintRepository;
    private final ComplaintService complaintService;
    private final ComplaintFeedbackService feedbackService;

    public AnalyticsService(ComplaintRepository complaintRepository, ComplaintService complaintService) {
        this(complaintRepository, complaintService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AnalyticsService(ComplaintRepository complaintRepository,
                            ComplaintService complaintService,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) ComplaintFeedbackService feedbackService) {
        this.complaintRepository = complaintRepository;
        this.complaintService = complaintService;
        this.feedbackService = feedbackService;
    }

    /**
     * Computes the complete analytics dataset with optional status, category, and timeframe filters.
     * Uses database aggregate queries to compute counts without loading all rows into memory.
     *
     * @param status optional status filter
     * @param category optional category filter
     * @param days optional timeframe in days (e.g. 7, 14, 30, or null/0 for all time)
     * @return fully populated AnalyticsDashboardData object
     */
    @Transactional(readOnly = true)
    public AnalyticsDashboardData getAnalyticsData(ComplaintStatus status, String category, Integer days) {
        String effectiveCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        Integer effectiveDays = (days != null && days > 0) ? days : null;
        LocalDateTime startDate = (effectiveDays != null) ? LocalDate.now().minusDays(effectiveDays - 1).atStartOfDay() : null;

        // 1. Calculate Summary Metrics
        AnalyticsSummary summary = computeSummary(status, effectiveCategory, startDate);

        // 2. Calculate Category Breakdown
        List<CategoryBreakdown> categoryBreakdowns = computeCategoryBreakdown(status, effectiveCategory, startDate, summary.getTotal());

        // 3. Calculate Department Breakdown
        List<DepartmentBreakdown> departmentBreakdowns = computeDepartmentBreakdown(status, effectiveCategory, startDate, summary.getTotal());

        // 4. Calculate Complaint Trend Points
        List<DailyTrendPoint> trendPoints = computeTrendPoints(status, effectiveCategory, startDate, effectiveDays);

        // 5. Calculate Student Resolution Feedback Metrics
        FeedbackMetrics feedbackMetrics = (feedbackService != null)
                ? feedbackService.getFeedbackMetrics()
                : FeedbackMetrics.empty();

        return new AnalyticsDashboardData(
                summary,
                categoryBreakdowns,
                departmentBreakdowns,
                trendPoints,
                status != null ? status.name() : "",
                effectiveCategory != null ? effectiveCategory : "",
                effectiveDays != null ? effectiveDays : 0,
                feedbackMetrics
        );
    }

    private AnalyticsSummary computeSummary(ComplaintStatus statusFilter, String categoryFilter, LocalDateTime startDate) {
        long total = complaintRepository.countWithFilters(statusFilter, categoryFilter, startDate);
        if (total == 0) {
            return new AnalyticsSummary(0, 0, 0, 0, 0);
        }

        // If a specific status is filtered, only that status has count = total
        if (statusFilter != null) {
            long submitted = (statusFilter == ComplaintStatus.SUBMITTED) ? total : 0;
            long underReview = (statusFilter == ComplaintStatus.UNDER_REVIEW) ? total : 0;
            long inProgress = (statusFilter == ComplaintStatus.IN_PROGRESS) ? total : 0;
            long resolved = (statusFilter == ComplaintStatus.RESOLVED) ? total : 0;
            return new AnalyticsSummary(total, submitted, underReview, inProgress, resolved);
        }

        // Query status counts using database aggregation
        List<Object[]> statusRows = complaintRepository.countGroupByStatus(categoryFilter, startDate);
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

        return new AnalyticsSummary(total, submitted, underReview, inProgress, resolved);
    }

    private List<CategoryBreakdown> computeCategoryBreakdown(ComplaintStatus status, String categoryFilter, LocalDateTime startDate, long total) {
        List<Object[]> rows = complaintRepository.countGroupByCategory(status, categoryFilter, startDate);
        Map<String, Long> countMap = new LinkedHashMap<>();

        // Initialize with predefined categories if no specific single category filter is applied
        if (categoryFilter == null) {
            for (String cat : complaintService.getAllowedCategories()) {
                countMap.put(cat, 0L);
            }
        }

        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                String catName = row[0].toString();
                long count = ((Number) row[1]).longValue();
                countMap.put(catName, count);
            }
        }

        List<CategoryBreakdown> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : countMap.entrySet()) {
            list.add(CategoryBreakdown.of(entry.getKey(), entry.getValue(), total));
        }

        // Sort by count descending, then by category name
        list.sort((a, b) -> {
            int cmp = Long.compare(b.count(), a.count());
            return cmp != 0 ? cmp : a.category().compareTo(b.category());
        });

        return list;
    }

    private List<DepartmentBreakdown> computeDepartmentBreakdown(ComplaintStatus status, String categoryFilter, LocalDateTime startDate, long total) {
        List<Object[]> rows = complaintRepository.countGroupByDepartment(status, categoryFilter, startDate);
        Map<String, Long> countMap = new LinkedHashMap<>();

        // Initialize with predefined departments
        for (String dept : complaintService.getAllowedDepartments()) {
            countMap.put(dept, 0L);
        }

        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                String deptName = row[0].toString();
                long count = ((Number) row[1]).longValue();
                countMap.put(deptName, count);
            }
        }

        List<DepartmentBreakdown> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : countMap.entrySet()) {
            list.add(DepartmentBreakdown.of(entry.getKey(), entry.getValue(), total));
        }

        // Sort by count descending, then by department name
        list.sort((a, b) -> {
            int cmp = Long.compare(b.count(), a.count());
            return cmp != 0 ? cmp : a.department().compareTo(b.department());
        });

        return list;
    }

    private List<DailyTrendPoint> computeTrendPoints(ComplaintStatus status, String categoryFilter, LocalDateTime startDate, Integer days) {
        List<Object[]> rows = complaintRepository.countDailyTrend(status, categoryFilter, startDate);
        Map<LocalDate, Long> countMap = new HashMap<>();

        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                LocalDate date = null;
                if (row[0] instanceof LocalDate ld) {
                    date = ld;
                } else if (row[0] instanceof java.sql.Date sqlDate) {
                    date = sqlDate.toLocalDate();
                } else {
                    try {
                        date = LocalDate.parse(row[0].toString().substring(0, 10));
                    } catch (Exception ex) {
                        log.debug("Could not parse date string: {}", row[0]);
                    }
                }

                if (date != null) {
                    long cnt = ((Number) row[1]).longValue();
                    countMap.put(date, cnt);
                }
            }
        }

        if (countMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<DailyTrendPoint> points = new ArrayList<>();

        if (days != null && days > 0 && days <= 60) {
            // Fill continuous calendar days for continuous time range
            LocalDate today = LocalDate.now();
            LocalDate start = today.minusDays(days - 1);
            LocalDate curr = start;
            while (!curr.isAfter(today)) {
                long cnt = countMap.getOrDefault(curr, 0L);
                points.add(new DailyTrendPoint(
                        curr.toString(),
                        curr.format(DATE_LABEL_FORMAT),
                        cnt
                ));
                curr = curr.plusDays(1);
            }
        } else {
            // All-time or sparse range: sort recorded dates chronologically
            List<LocalDate> sortedDates = new ArrayList<>(countMap.keySet());
            Collections.sort(sortedDates);
            for (LocalDate date : sortedDates) {
                points.add(new DailyTrendPoint(
                        date.toString(),
                        date.format(DATE_LABEL_FORMAT),
                        countMap.get(date)
                ));
            }
        }

        return points;
    }
}
