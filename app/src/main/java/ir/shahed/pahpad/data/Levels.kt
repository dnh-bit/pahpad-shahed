package ir.shahed.pahpad.data

import ir.shahed.pahpad.core.Theme

enum class Env { DESERT, URBAN, NAVAL, SPECIAL;

    val palette: Theme.Palette
        get() = when (this) {
            DESERT -> Theme.DESERT
            URBAN -> Theme.URBAN
            NAVAL -> Theme.NAVAL
            SPECIAL -> Theme.SPECIAL
        }
}

class LevelDef(
    val chapter: Int,
    val index: Int,
    val title: String,
    val targetName: String,
    val difficulty: Int,
    val env: Env,
    /** فاصله خط پرتاب تا هدف بر حسب متر */
    val distance: Float,
    val targets: Int = 1,
    val movingTarget: Boolean = false,
    val aaSites: Int = 0,
    val radarZones: Int = 0,
    val ewZones: Int = 0,
    /** شدت باد ۰ تا ۱ */
    val wind: Float = 0f,
    /** محدودیت زمانی بر حسب ثانیه؛ صفر یعنی بدون محدودیت */
    val timeLimit: Float = 0f,
    val bonuses: Int = 2,
    val newChallenge: String,
    val tutorial: String? = null,
    val briefing: String
) {
    val id: String get() = "L${chapter}_$index"
    val code: String get() = "$chapter-$index"
    val difficultyLabel: String get() = Theme.difficultyLabel(difficulty)
}

class ChapterDef(
    val number: Int,
    val title: String,
    val subtitle: String,
    val requiredStars: Int,
    val env: Env
)

object Levels {

    val chapters = listOf(
        ChapterDef(1, "فصل یک: آموزش", "آشنایی با کنترل پهباد", 0, Env.DESERT),
        ChapterDef(2, "فصل دو: عملیات صحرا", "اهداف نظامی در دشت و ساحل", 2, Env.DESERT),
        ChapterDef(3, "فصل سه: عملیات شهری", "پرواز میان ساختمان‌ها و جنگ الکترونیک", 10, Env.URBAN),
        ChapterDef(4, "فصل چهار: عملیات ویژه", "سخت‌ترین ماموریت‌ها؛ نیازمند ستاره", 30, Env.SPECIAL)
    )

    val all: List<LevelDef> = listOf(

        // ---------------------------------------------------- فصل ۱: آموزش
        LevelDef(
            chapter = 1, index = 1,
            title = "هدف ثابت در دشت",
            targetName = "سنگر متروکه",
            difficulty = 1, env = Env.DESERT,
            distance = 600f, wind = 0f, bonuses = 2,
            newChallenge = "کنترل پایه پهباد",
            tutorial = "برای پرتاب روی صفحه ضربه بزنید. با تیلت گوشی یا جوی‌استیک پهباد را هدایت کنید.",
            briefing = "اپراتور، این یک پرواز آموزشی است. هدف یک سنگر متروکه در دشت باز است و هیچ پدافندی در منطقه فعال نیست. پهباد را پرتاب کن، مسیر را حفظ کن و مرکز هدف را بزن."
        ),
        LevelDef(
            chapter = 1, index = 2,
            title = "انبار مهمات",
            targetName = "انبار مهمات",
            difficulty = 1, env = Env.DESERT,
            distance = 850f, wind = 0.1f, bonuses = 3,
            newChallenge = "تنظیم سرعت و زاویه",
            tutorial = "با کشیدن انگشت در سمت راست صفحه، سرعت پهباد را کم و زیاد کنید.",
            briefing = "انبار مهمات در حاشیه دشت شناسایی شده است. سرعت را در میانه مسیر کاهش بده تا کنترل زاویه شیرجه دقیق‌تر شود. مدیریت سوخت امتیاز اضافی دارد."
        ),
        LevelDef(
            chapter = 1, index = 3,
            title = "پل نظامی",
            targetName = "پل نظامی",
            difficulty = 2, env = Env.DESERT,
            distance = 1000f, wind = 0.15f, bonuses = 3,
            newChallenge = "سوئیچ دوربین و شیرجه",
            tutorial = "با دکمه دوربین بین نمای سوم شخص و دوربین نوک پهباد جابه‌جا شوید.",
            briefing = "پل نظامی دشمن باید از کار بیفتد. برای دقت بیشتر در لحظه شیرجه، به دوربین نوک پهباد سوئیچ کن و زاویه برخورد را روی مرکز دهانه پل تنظیم کن."
        ),

        // ---------------------------------------------------- فصل ۲: عملیات صحرا
        LevelDef(
            chapter = 2, index = 1,
            title = "کاروان نظامی",
            targetName = "کاروان زرهی",
            difficulty = 2, env = Env.DESERT,
            distance = 1100f, movingTarget = true, wind = 0.2f, bonuses = 3,
            newChallenge = "هدف متحرک",
            briefing = "یک کاروان زرهی در جاده صحرایی در حرکت است. هدف متحرک است؛ نقطه برخورد را جلوتر از خودرو پیش‌بینی کن، نه روی موقعیت فعلی آن."
        ),
        LevelDef(
            chapter = 2, index = 2,
            title = "پایگاه موشکی",
            targetName = "سکوی پرتاب موشک",
            difficulty = 2, env = Env.DESERT,
            distance = 1250f, aaSites = 1, wind = 0.2f, bonuses = 3,
            newChallenge = "پدافند هوایی ساده",
            tutorial = "با شنیدن آژیر پدافند، ارتفاع را کم کن و مسیر را زیگزاگ کن.",
            briefing = "سکوی پرتاب موشک توسط یک سامانه ضدهوایی محافظت می‌شود. آژیر هشدار یعنی در تیررس هستی؛ ارتفاع کم و تغییر مسیر مداوم شانس بقا را بالا می‌برد."
        ),
        LevelDef(
            chapter = 2, index = 3,
            title = "فرودگاه نظامی",
            targetName = "آشیانه فرودگاه",
            difficulty = 3, env = Env.DESERT,
            distance = 1450f, aaSites = 2, radarZones = 1, wind = 0.25f, bonuses = 4,
            newChallenge = "رادار + پدافند",
            tutorial = "در محدوده رادار (دایره آبی نقشه) زیر سقف ارتفاع پرواز کن تا شناسایی نشوی.",
            briefing = "آشیانه اصلی فرودگاه نظامی هدف است. منطقه زیر پوشش رادار قرار دارد؛ اگر بالای سقف ارتفاع پرواز کنی پدافند دقیق‌تر شلیک می‌کند. پرواز در ارتفاع پست، کلید عبور است."
        ),
        LevelDef(
            chapter = 2, index = 4,
            title = "مرکز فرماندهی",
            targetName = "مرکز فرماندهی منطقه",
            difficulty = 3, env = Env.DESERT,
            distance = 1600f, aaSites = 3, radarZones = 1, wind = 0.3f, bonuses = 4,
            newChallenge = "چند لایه دفاعی",
            briefing = "مرکز فرماندهی منطقه با سه لایه پدافندی محافظت می‌شود. مسیر مستقیم خودکشی است؛ از کناره‌ها وارد شو و در ثانیه‌های آخر روی هدف تراز کن."
        ),
        LevelDef(
            chapter = 2, index = 5,
            title = "ناوگان دریایی",
            targetName = "ناوشکن دشمن",
            difficulty = 4, env = Env.NAVAL,
            distance = 1800f, movingTarget = true, aaSites = 3, radarZones = 1,
            wind = 0.6f, bonuses = 4,
            newChallenge = "هدف دریایی + باد شدید",
            tutorial = "باد شدید پهباد را به پهلو می‌راند؛ زاویه را مخالف باد نگه دار.",
            briefing = "ناوشکن دشمن در حال حرکت روی آب است و باد ساحلی شدید مسیر پهباد را منحرف می‌کند. سطح آب بازتاب رادار دارد؛ ارتفاع خیلی کم هم خطرناک است."
        ),

        // ---------------------------------------------------- فصل ۳: عملیات شهری
        LevelDef(
            chapter = 3, index = 1,
            title = "انبار تسلیحات",
            targetName = "انبار تسلیحات",
            difficulty = 2, env = Env.URBAN,
            distance = 1200f, aaSites = 1, wind = 0.25f, bonuses = 4,
            newChallenge = "پرواز بین ساختمان‌ها",
            tutorial = "برخورد با ساختمان‌ها ماموریت را ناموفق می‌کند. از خیابان‌ها عبور کن.",
            briefing = "انبار تسلیحات در حاشیه صنعتی شهر است. عبور از میان ساختمان‌ها پوشش خوبی از رادار می‌دهد اما خطای کوچک یعنی برخورد با دیوار."
        ),
        LevelDef(
            chapter = 3, index = 2,
            title = "مرکز مخابرات",
            targetName = "برج مخابراتی",
            difficulty = 3, env = Env.URBAN,
            distance = 1400f, aaSites = 2, ewZones = 2, wind = 0.3f, bonuses = 4,
            newChallenge = "جنگ الکترونیک",
            tutorial = "در منطقه جنگ الکترونیک، فرمان‌ها مختل می‌شوند. آرام و کوتاه فرمان بده.",
            briefing = "برج مخابراتی دشمن سیگنال هدایت را مختل می‌کند. داخل مناطق ارغوانی، تصویر و فرمان قطع و وصل می‌شود؛ مسیر را از قبل در ذهن داشته باش."
        ),
        LevelDef(
            chapter = 3, index = 3,
            title = "پادگان نظامی",
            targetName = "پادگان (سه هدف)",
            difficulty = 3, env = Env.URBAN,
            distance = 1500f, targets = 3, aaSites = 2, radarZones = 1, ewZones = 1,
            wind = 0.3f, bonuses = 5,
            newChallenge = "چند هدف همزمان",
            tutorial = "هر هدفی که منهدم شود امتیاز جداگانه دارد؛ نزدیک‌ترین را اول بزن.",
            briefing = "سه سازه کلیدی در پادگان مشخص شده‌اند. با هر پهباد یک هدف؛ ترتیب انهدام را طوری بچین که سوخت و زمان هدر نرود."
        ),
        LevelDef(
            chapter = 3, index = 4,
            title = "نیروگاه دشمن",
            targetName = "سالن توربین",
            difficulty = 4, env = Env.URBAN,
            distance = 1650f, aaSites = 3, radarZones = 1, ewZones = 1,
            wind = 0.35f, timeLimit = 75f, bonuses = 5,
            newChallenge = "محدودیت زمانی",
            tutorial = "زمان‌سنج بالای صفحه؛ با اتمام زمان ماموریت ناموفق است.",
            briefing = "پنجره عملیاتی فقط ۷۵ ثانیه است. بعد از آن پدافند دشمن کامل بیدار می‌شود. بوست را نگه دار برای خط آخر."
        ),
        LevelDef(
            chapter = 3, index = 5,
            title = "ستاد فرماندهی کل",
            targetName = "ستاد فرماندهی کل",
            difficulty = 4, env = Env.URBAN,
            distance = 1900f, targets = 2, movingTarget = false, aaSites = 4,
            radarZones = 2, ewZones = 2, wind = 0.45f, timeLimit = 110f, bonuses = 5,
            newChallenge = "نبرد نهایی: همه چالش‌ها",
            briefing = "ستاد فرماندهی کل، سنگین‌ترین هدف فصل. پدافند چندلایه، رادار، جنگ الکترونیک و باد؛ همه با هم. هر چه یاد گرفتی همین‌جا لازم می‌شود."
        ),

        // ---------------------------------------------------- فصل ۴: عملیات ویژه
        LevelDef(
            chapter = 4, index = 1,
            title = "قطار تسلیحاتی",
            targetName = "واگن مهمات",
            difficulty = 4, env = Env.SPECIAL,
            distance = 1700f, movingTarget = true, aaSites = 3, radarZones = 1,
            wind = 0.4f, timeLimit = 80f, bonuses = 5,
            newChallenge = "هدف پرسرعت متحرک",
            briefing = "قطار حامل مهمات با سرعت بالا در حرکت است. واگن میانی هدف اصلی است؛ پیش‌بینی مسیر همه‌چیز است."
        ),
        LevelDef(
            chapter = 4, index = 2,
            title = "پایگاه زیرزمینی",
            targetName = "دهانه تونل",
            difficulty = 4, env = Env.SPECIAL,
            distance = 1750f, aaSites = 4, radarZones = 2, wind = 0.4f,
            timeLimit = 90f, bonuses = 5,
            newChallenge = "هدف کوچک و پوشیده",
            briefing = "دهانه ورودی تونل تنها نقطه آسیب‌پذیر پایگاه است؛ سطح مقطع هدف کوچک است و شیرجه باید تقریباً افقی باشد."
        ),
        LevelDef(
            chapter = 4, index = 3,
            title = "مرکز جنگ الکترونیک",
            targetName = "آنتن‌های اخلال",
            difficulty = 4, env = Env.SPECIAL,
            distance = 1850f, targets = 2, aaSites = 3, ewZones = 3, wind = 0.4f,
            timeLimit = 95f, bonuses = 5,
            newChallenge = "اخلال کامل سیگنال",
            briefing = "تمام منطقه زیر پوشش اخلال است. کنترل مداوم قطع می‌شود؛ فرمان‌های کوتاه و پیش‌بینی مسیر، تنها راه رسیدن به آنتن‌ها است."
        ),
        LevelDef(
            chapter = 4, index = 4,
            title = "ناو هواپیمابر",
            targetName = "عرشه ناو",
            difficulty = 4, env = Env.NAVAL,
            distance = 2000f, movingTarget = true, aaSites = 5, radarZones = 2,
            wind = 0.7f, timeLimit = 100f, bonuses = 5,
            newChallenge = "پدافند نقطه‌زن دریایی",
            briefing = "ناو هواپیمابر با پنج سامانه پدافندی و باد شدید دریایی. ورود از پاشنه ناو کمترین تیررس را دارد."
        ),
        LevelDef(
            chapter = 4, index = 5,
            title = "عملیات نهایی",
            targetName = "مرکز فرماندهی راهبردی",
            difficulty = 4, env = Env.SPECIAL,
            distance = 2200f, targets = 3, movingTarget = true, aaSites = 6,
            radarZones = 2, ewZones = 3, wind = 0.6f, timeLimit = 120f, bonuses = 5,
            newChallenge = "همه چالش‌ها در حداکثر شدت",
            briefing = "آخرین ماموریت. سه هدف، شش سامانه پدافندی، اخلال کامل، باد شدید و ۱۲۰ ثانیه زمان. اگر اینجا را رد کنی، رتبه فرمانده مال توست."
        )
    )

    fun byId(id: String): LevelDef? = all.firstOrNull { it.id == id }

    fun chapter(number: Int): ChapterDef? = chapters.firstOrNull { it.number == number }

    fun ofChapter(number: Int): List<LevelDef> = all.filter { it.chapter == number }

    fun next(level: LevelDef): LevelDef? {
        val i = all.indexOfFirst { it.id == level.id }
        return if (i >= 0 && i < all.size - 1) all[i + 1] else null
    }

    /** ماموریت ساختگی برای حالت بی‌نهایت */
    fun endless(): LevelDef = LevelDef(
        chapter = 0, index = 0,
        title = "حالت بی‌نهایت",
        targetName = "اهداف تصادفی",
        difficulty = 3, env = Env.DESERT,
        distance = 900f, movingTarget = true, aaSites = 2, radarZones = 1, ewZones = 1,
        wind = 0.3f, bonuses = 3,
        newChallenge = "اهداف بی‌پایان",
        briefing = "پهباد نامحدود، اهداف بی‌پایان. هر انهدام امتیاز می‌دهد و سختی بالا می‌رود. تا جای ممکن ادامه بده."
    )

    /** ماموریت چالش روزانه بر اساس بذر امروز */
    fun daily(seed: Long): LevelDef {
        val r = java.util.Random(seed)
        val envs = listOf(Env.DESERT, Env.URBAN, Env.NAVAL, Env.SPECIAL)
        val env = envs[r.nextInt(envs.size)]
        return LevelDef(
            chapter = 0, index = 1,
            title = "چالش روزانه",
            targetName = "هدف ویژه امروز",
            difficulty = 3 + r.nextInt(2), env = env,
            distance = 1200f + r.nextInt(900),
            targets = 1 + r.nextInt(3),
            movingTarget = r.nextBoolean(),
            aaSites = 1 + r.nextInt(4),
            radarZones = r.nextInt(2),
            ewZones = r.nextInt(3),
            wind = 0.2f + r.nextFloat() * 0.5f,
            timeLimit = if (r.nextBoolean()) 70f + r.nextInt(50) else 0f,
            bonuses = 3 + r.nextInt(3),
            newChallenge = "ماموریت ویژه امروز",
            briefing = "ماموریت امروز فقط یک بار در روز رکورد ثبت می‌کند. جایزه سکه دو برابر است."
        )
    }
}
