package com.iritech.irissample.model.export;

import java.io.File;

/**
 * Metadata for an attendance report exported by the app and kept in cache
 * for email attachment selection.
 */
public final class ExportedReport {

    public enum ReportType {
        CSV_DETAIL,
        CSV_SUMMARY,
        CSV_MATRIX,
        EXCEL
    }

    private final ReportType reportType;
    private final String subjectId;
    private final String displayName;
    private final File cacheFile;
    private final long fileSize;
    private final long exportedAt;

    public ExportedReport(
            ReportType reportType,
            String subjectId,
            String displayName,
            File cacheFile,
            long fileSize,
            long exportedAt
    ) {
        this.reportType = reportType;
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.cacheFile = cacheFile;
        this.fileSize = fileSize;
        this.exportedAt = exportedAt;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public File getCacheFile() {
        return cacheFile;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getExportedAt() {
        return exportedAt;
    }

    public String getReportTypeLabel() {
        if (reportType == null) {
            return "Báo cáo";
        }

        switch (reportType) {
            case CSV_DETAIL:
                return "CSV chi tiết";
            case CSV_SUMMARY:
                return "CSV tổng hợp";
            case CSV_MATRIX:
                return "CSV dạng bảng";
            case EXCEL:
                return "Excel";
            default:
                return "Báo cáo";
        }
    }

    public boolean isCacheFileAvailable() {
        return cacheFile != null
                && cacheFile.exists()
                && cacheFile.isFile()
                && cacheFile.length() > 0;
    }
}
