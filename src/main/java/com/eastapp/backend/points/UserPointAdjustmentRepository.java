package com.eastapp.backend.points;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserPointAdjustmentRepository extends JpaRepository<UserPointAdjustment, UUID> {

    @Query("""
            select adjustment.recipient.id, sum(adjustment.pointsDelta)
            from UserPointAdjustment adjustment
            where adjustment.tenant.id = :tenantId
            group by adjustment.recipient.id
            """)
    List<Object[]> findTotalsByTenant(@Param("tenantId") UUID tenantId);

    @Query("""
            select coalesce(sum(adjustment.pointsDelta), 0)
            from UserPointAdjustment adjustment
            where adjustment.tenant.id = :tenantId
              and adjustment.recipient.id = :userId
            """)
    long totalForUser(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId
    );
}
