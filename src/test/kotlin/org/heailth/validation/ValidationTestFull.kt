package org.heailth.validation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

@DisplayName("Full Validation Suite")
class ValidationTestFull {

    @Nested
    @DisplayName("Stage 1: Configuration & Environment Audit")
    inner class ConfigAudit : ConfigValidationTest()

    @Nested
    @DisplayName("Stage 2: Data Structural Integrity")
    inner class StructuralIntegrity : CSVStructuralValidationTest()

    @Nested
    @DisplayName("Stage 3: Data Quality & Volumetric Thresholds")
    inner class DataQuality : CSVDataQualityValidationTest()

    @Nested
    @DisplayName("Stage 4: Process & Optimization Logic Verification")
    inner class LogicVerification : OptimizationLogicValidationTest()
}
