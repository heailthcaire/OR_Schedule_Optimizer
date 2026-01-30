package org.heailth.validation

import org.heailth.model.ValidationReport

class ValidationReportWriter {
    fun generateTerminalReport(report: ValidationReport): String {
        val sb = StringBuilder()
        sb.append("\n==========================================\n")
        sb.append("         CSV VALIDATION REPORT\n")
        sb.append("==========================================\n")

        if (!report.isHeaderRowPresent) {
            sb.append("\n!!! HEADER ROW NOT FOUND !!!\n")
            sb.append("\nINFO: No valid data/header found for this file.\n")
            sb.append("==========================================\n")
            return sb.toString()
        }

        if (report.missingHeaders.isNotEmpty()) {
            sb.append("\n!!! MISSING REQUIRED COLUMNS (STRUCTURE) !!!\n")
            for (header in report.missingHeaders) {
                sb.append(String.format("  - %s\n", header))
            }
            sb.append("\nFound headers: ${report.foundHeaders}\n")
            sb.append("\nValidation cannot proceed.\n")
            sb.append("==========================================\n")
            return sb.toString()
        }

        sb.append(String.format("Valid Rows: %d\n", report.getTotalValidRows()))
        sb.append(String.format("Error Rows: %d\n", report.getTotalErrorRows()))
        sb.append("-----------\n")
        sb.append(String.format("Total Rows: %d\n", report.getTotalRowsRead()))
        sb.append(String.format("  - Accepted (Orig):  %d\n", report.getRowsAcceptedOriginal()))
        sb.append(String.format("  - Accepted (Corr):  %d\n", report.getRowsAcceptedCorrected()))
        
        if (report.getSkipReasons().isNotEmpty()) {
            sb.append("\nSkip Reasons:\n")
            for ((key, value) in report.getSkipReasons()) {
                sb.append(String.format("  - %s: %d\n", key, value))
            }
        }

        if (report.getRequiredFieldNullCounts().isNotEmpty()) {
            sb.append("\nRows Missing Required Data:\n")
            for ((key, value) in report.getRequiredFieldNullCounts()) {
                sb.append(String.format("  - %s: %d null/empty\n", key, value))
            }
        }

        if (report.getFormatViolationCounts().isNotEmpty()) {
            sb.append("\nFormat Violations:\n")
            for ((key, value) in report.getFormatViolationCounts()) {
                sb.append(String.format("  - %s: %d format errors\n", key, value))
            }
        }

        if (report.getIcd10ValidCount() > 0 || report.getIcd10InvalidCount() > 0) {
            sb.append("\nICD-10-PCS Validation:\n")
            sb.append(String.format("  - Valid (As-is):   %d\n", report.getIcd10ValidCount()))
            sb.append(String.format("  - Valid (Corrected): %d\n", report.getIcd10CorrectedCount()))
            sb.append(String.format("  - Invalid:         %d\n", report.getIcd10InvalidCount()))
        }

        sb.append("==========================================\n")
        return sb.toString()
    }
}
