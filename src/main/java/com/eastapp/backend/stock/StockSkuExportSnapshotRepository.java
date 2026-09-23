package com.eastapp.backend.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockSkuExportSnapshotRepository
        extends JpaRepository<StockSkuExportSnapshot, UUID> {
}
