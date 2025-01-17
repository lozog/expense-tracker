package com.lozog.expensetracker.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarHelper {
    companion object {
        fun parseDatestring(input: String): LocalDate? {
            // List of possible input formats
            val inputFormatters = listOf(
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
            )

            // Attempt to parse using each formatter
            for (formatter in inputFormatters) {
                try {
                    return LocalDate.parse(input, formatter)
                } catch (e: Exception) {
                    // Ignore and try the next formatter
                }
            }
            return null
        }
    }
}