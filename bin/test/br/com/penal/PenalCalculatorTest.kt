package br.com.penal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PenalCalculatorTest {
    private val calculator = PenalCalculator()

    @Test
    fun `calcula progressao comum primario em 16 por cento`() {
        val result = calculator.calculate(
            CalculateRequest(
                clientName = "Teste",
                prisonDate = "2026-01-01",
                years = 10,
                months = 0,
                days = 0,
                crimeSubtype = "comum_primario"
            )
        )

        assertEquals(3650, result.totalDays)
        assertEquals(0.16, result.progressionFraction)
        assertEquals(584, result.semiOpenDaysToServe)
        assertNotNull(result.paroleDate)
    }

    @Test
    fun `calcula remicao e detracao como creditos`() {
        val result = calculator.calculate(
            CalculateRequest(
                clientName = "Teste",
                prisonDate = "2026-01-01",
                years = 1,
                detractionDays = 10,
                workDays = 30,
                studyHours = 24,
                readingBooks = 1,
                extraRemissionDays = 3,
                crimeSubtype = "comum_primario"
            )
        )

        assertEquals(19, result.remissionDays)
        assertEquals(10, result.detractionDays)
        assertEquals(336, result.effectiveDays)
    }

    @Test
    fun `veda livramento para hediondo com resultado morte`() {
        val result = calculator.calculate(
            CalculateRequest(
                clientName = "Teste",
                prisonDate = "2026-01-01",
                years = 8,
                crimeSubtype = "hediondo_morte_reincidente"
            )
        )

        assertNull(result.paroleDate)
        assertEquals("Nao aplicavel", result.paroleLabel)
    }
}
