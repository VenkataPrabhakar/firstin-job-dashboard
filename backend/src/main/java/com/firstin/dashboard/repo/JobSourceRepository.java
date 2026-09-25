package com.firstin.dashboard.repo;

import com.firstin.dashboard.model.JobPosting;
import com.firstin.dashboard.model.JobSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobSourceRepository extends JpaRepository<JobSource, Long> {

    List<JobSource> findByPostingOrderBySeenAtDesc(JobPosting posting);

    Optional<JobSource> findFirstByPostingAndSourceAndUrl(JobPosting posting, String source, String url);
}
