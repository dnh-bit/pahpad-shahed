package ir.shahed.pahpad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قانون ستارهٔ دو: «جمع همهٔ بونوس‌های مخفی» (جایگزین شرط دست‌نیافتنی
 * «میانگین دقت برخورد ۵۰٪»).
 */
class StarRulesTest {

    @Test
    fun allBonusesPassesOnlyWhenEveryBonusIsCollected() {
        assertTrue(StarRules.allBonuses(3, 3))
        assertTrue(StarRules.allBonuses(0, 0)) // ماموریت بدون بونوس، شرط خودکار پاس
        assertFalse(StarRules.allBonuses(2, 3))
        assertFalse(StarRules.allBonuses(0, 2))
    }

    @Test
    fun threeStarsWhenAllBonusesAndCleanRun() {
        val stars = StarRules.stars(
            success = true, bonusesCollected = 4, bonusesTotal = 4,
            damage = 0, elapsed = 400f, parTime = 300f
        )
        assertEquals(3, stars)
    }

    @Test
    fun missingOneBonusBlocksOnlySecondStar() {
        val stars = StarRules.stars(
            success = true, bonusesCollected = 3, bonusesTotal = 4,
            damage = 0, elapsed = 200f, parTime = 300f
        )
        assertEquals(2, stars)
    }

    @Test
    fun failureEarnsNoStars() {
        val stars = StarRules.stars(
            success = false, bonusesCollected = 5, bonusesTotal = 5,
            damage = 0, elapsed = 100f, parTime = 300f
        )
        assertEquals(0, stars)
    }

    @Test
    fun evaluateShowsRealBonusCounts() {
        val rules = StarRules.evaluate(
            success = true, targetsDestroyed = 2, targetsTotal = 2,
            bonusesCollected = 2, bonusesTotal = 3,
            damage = 0, elapsed = 250f, parTime = 300f
        )
        assertEquals(3, rules.size)
        val rule2 = rules[1]
        assertEquals("جمع بونوس‌ها", rule2.title)
        assertTrue(rule2.requirement.contains("بونوس"))
        assertFalse("یک بونوس جا‌مانده نباید پاس شود", rule2.passed)
        // متن اندازه‌گیری‌شده باید شمارش واقعی بازیکن باشد (۲ از ۳)
        assertTrue(rule2.measured.contains("۲"))
        assertTrue(rule2.measured.contains("۳"))
    }

    @Test
    fun previewListsTheNewSecondRuleWithBonusCount() {
        val preview = StarRules.preview(targetsTotal = 1, parTime = 200f, bonusesTotal = 5)
        assertEquals(3, preview.size)
        val rule2 = preview[1]
        assertEquals("جمع بونوس‌ها", rule2.title)
        assertTrue("متن پیش‌نمایش باید شرط جمع بونوس‌ها باشد: ${rule2.requirement}",
            rule2.requirement.contains("بونوس"))
        assertTrue("پیش‌نمایش باید تعداد بونوس‌ها را نشان دهد: ${rule2.measured}",
            rule2.measured.contains("۵"))
        // شرط قدیمی دیگر نباید وجود داشته باشد
        assertTrue(preview.none { it.requirement.contains("دقت برخورد") })
    }

    @Test
    fun oldAccuracyGateIsGone() {
        // هیچ‌کجای قانون نباید به «۵۰٪ دقت» اشاره کند
        val rules = StarRules.evaluate(
            success = true, targetsDestroyed = 1, targetsTotal = 1,
            bonusesCollected = 0, bonusesTotal = 0,
            damage = 1, elapsed = 999f, parTime = 100f
        )
        assertTrue(rules.none { it.requirement.contains("۵۰") })
        // دقت صفر هم ستارهٔ دوم را با بونوس کامل می‌گیرد
        assertEquals(2, StarRules.stars(true, 0, 0, 1, 999f, 100f))
    }
}
