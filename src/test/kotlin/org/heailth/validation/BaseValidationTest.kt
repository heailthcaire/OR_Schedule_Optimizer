package org.heailth.validation

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(BaseValidationTest.SummaryExtension::class)
open class BaseValidationTest {

    companion object {
        private val passedTests = AtomicInteger(0)
        private val failedTests = AtomicInteger(0)

        @BeforeAll
        @JvmStatic
        fun resetCounters() {
            passedTests.set(0)
            failedTests.set(0)
        }

        @AfterAll
        @JvmStatic
        fun printSummary() {
            val passed = passedTests.get()
            val failed = failedTests.get()
            val total = passed + failed
            
            println("==========================================")
            println("\nPassed tests: $passed")
            println("Failed tests: $failed")
            println("___________________")
            println("Total tests: $total\n")
        }
    }

    class SummaryExtension : TestWatcher {
        override fun testSuccessful(context: ExtensionContext) {
            passedTests.incrementAndGet()
        }

        override fun testFailed(context: ExtensionContext, cause: Throwable?) {
            failedTests.incrementAndGet()
        }

        override fun testAborted(context: ExtensionContext, cause: Throwable?) {
            // Not counted as passed or failed in the requested summary
        }

        override fun testDisabled(context: ExtensionContext, reason: Optional<String>) {
            // Not counted
        }
    }
}
