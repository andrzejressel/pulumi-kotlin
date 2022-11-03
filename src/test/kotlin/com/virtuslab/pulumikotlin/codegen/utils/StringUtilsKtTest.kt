package com.virtuslab.pulumikotlin.codegen.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class StringUtilsKtTest {
    @Test
    fun `capitalize should change the first letter`() {
        assertEquals("Word", "word".capitalize())
    }

    @Test
    fun `capitalize should be ok if the string is already capitalized`() {
        assertEquals("Word", "Word".capitalize())
    }

    @Test
    fun `decapitalize should change the first letter`() {
        assertEquals("word", "Word".decapitalize())
    }

    @Test
    fun `decapitalize should be ok if the string is already decapitalized`() {
        assertEquals("word", "Word".decapitalize())
    }

    @Suppress("unused")
    enum class ShortenTestCase(
        val inputString: String,
        val desiredLength: Int,
        val fillWith: String,
        val expectedString: String,
    ) {
        ShortenExampleFromKDoc(
            inputString = "very very very very long string",
            desiredLength = 20,
            fillWith = "....",
            expectedString = "very ver....g string",
        ),
        ShortenOddStringLengthEvenDesiredLength(
            inputString = "too long string with odd length".also { assumeTrue(it.length.isOdd()) },
            desiredLength = 22,
            fillWith = "***",
            expectedString = "too long ***odd length",
        ),
        ShortenEvenStringLengthEvenDesiredLength(
            inputString = "too long string with even length".also { assumeTrue(it.length.isEven()) },
            desiredLength = 22,
            fillWith = "---",
            expectedString = "too long ---ven length",
        ),
        ShortenOddStringLengthOddDesiredLength(
            inputString = "too long string with odd length".also { assumeTrue(it.length.isOdd()) },
            desiredLength = 23,
            fillWith = "===",
            expectedString = "too long s===odd length",
        ),
        ShortenEvenStringLengthOddDesiredLength(
            inputString = "too long string with even length".also { assumeTrue(it.length.isEven()) },
            desiredLength = 23,
            fillWith = "###",
            expectedString = "too long s###ven length",
        ),
    }

    @EnumSource(ShortenTestCase::class)
    @ParameterizedTest
    fun `shorten should work`(testCase: ShortenTestCase) {
        // when
        val shortened = testCase.inputString.shorten(testCase.desiredLength, testCase.fillWith)

        // then
        assertEquals(testCase.expectedString, shortened)
    }
}
