package com.sprintflowinsight.repository;
import com.sprintflowinsight.model.SharedReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SharedReportRepository extends JpaRepository<SharedReport,Long>{
 Optional<SharedReport> findByTokenHash(String tokenHash);
 List<SharedReport> findByProjectIdOrderByCreatedAtDesc(Long projectId);
 void deleteByProjectId(Long projectId);
 void deleteByCreatedBy(String createdBy);
}
