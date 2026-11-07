package com.example.cashapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryIconTest {
    @Test fun `régi ikon kulcs`() = assertEquals(CategoryIcon.Vector("food"), CategoryIcon.parse("food"))
    @Test fun `betű`() = assertEquals(CategoryIcon.Letter("É"), CategoryIcon.parse("letter:É"))
    @Test fun `üres`() = assertNull(CategoryIcon.parse(""))
    @Test fun `üres betű`() = assertNull(CategoryIcon.parse("letter:"))

    @Test fun `oda-vissza`() {
        listOf(CategoryIcon.Vector("home"), CategoryIcon.Letter("AB")).forEach {
            assertEquals(it, CategoryIcon.parse(it.encode()))
        }
    }

    @Test fun `betű nagybetűsítve, max 2`() = assertEquals("ÉL", CategoryIcon.normalizeLetters(" élelem "))
    @Test fun `emoji egyben marad`() = assertEquals("🍕", CategoryIcon.normalizeLetters("🍕"))
}
