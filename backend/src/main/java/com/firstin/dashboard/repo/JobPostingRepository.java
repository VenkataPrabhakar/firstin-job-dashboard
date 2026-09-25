package com.firstin.dashboard.repo;

import com.firstin.dashboard.model.Engagement;
import com.firstin.dashboard.model.JobPosting;
import com.firstin.dashboard.model.VisaStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * All dynamic filters (tab, search) are JPA Specifications — never
 * string-concatenated SQL (docs/DESIGN.md §9).
 */
public interface JobPostingRepository
        extends JpaRepository<JobPosting, String>, JpaSpecificationExecutor<JobPosting> {

    /**
     * Builds the listing filter for the dashboard tabs plus optional
     * case-insensitive title/company search.
     *
     * @param tab     today | c2c | w2 | fulltime | visa
     * @param query   free text, matched case-insensitively against title + company
     * @param todayCutoff postings with firstSeen at/after this are "today" (last 24h)
     */
    static Specification<JobPosting> listingFilter(String tab, String query, Instant todayCutoff) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            switch (tab) {
                case "today" -> predicates.add(cb.greaterThanOrEqualTo(root.get("firstSeen"), todayCutoff));
                case "c2c" -> predicates.add(cb.equal(root.get("engagement"), Engagement.C2C));
                case "w2" -> predicates.add(cb.equal(root.get("engagement"), Engagement.W2));
                case "fulltime" -> predicates.add(cb.equal(root.get("engagement"), Engagement.FULLTIME));
                case "visa" -> predicates.add(root.get("visaStatus").in(
                        VisaStatus.CONFIRMED, VisaStatus.OPEN));
                default -> throw new IllegalArgumentException("unknown tab: " + tab);
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("company")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
