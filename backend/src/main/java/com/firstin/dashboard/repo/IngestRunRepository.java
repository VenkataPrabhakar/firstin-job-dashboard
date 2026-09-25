package com.firstin.dashboard.repo;

import com.firstin.dashboard.model.IngestRun;
import com.firstin.dashboard.model.RunStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * One row per pipeline {@code run_id} — the id is the primary key, so
 * replays upsert rather than duplicate.
 */
public interface IngestRunRepository extends JpaRepository<IngestRun, String> {

    /** Latest completed run; {@code lastPull} reads its {@code completedAt}. */
    Optional<IngestRun> findFirstByStatusOrderByCompletedAtDesc(RunStatus status);
}
