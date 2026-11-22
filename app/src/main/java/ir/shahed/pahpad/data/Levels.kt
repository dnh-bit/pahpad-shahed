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
        ChapterDef(1, "فصل یک: آموزش", "آشنایی با کنترل پهپاد", 0, Env.DESERT),
        ChapterDef(2, "فصل دو: عملیات صحرا", "اهداف ارتش‌های تروریستی آمریکا و اسرائیل در دشت و ساحل", 2, Env.DESERT),
        ChapterDef(3, "فصل سه: عملیات شهری", "پرواز میان ساختمان‌ها و جنگ الکترونیک", 10, Env.URBAN),
        ChapterDef(4, "فصل چهار: عملیات ویژه", "سخت‌ترین ماموریت‌ها؛ نیازمند ستاره", 30, Env.SPECIAL)
    )

    val all: List<LevelDef> = listOf(

        // ---------------------------------------------------- فصل ۱: آموزش
        LevelDef(
            chapter = 1, index = 1,
            title = "سنگر ارتش تروریستی اسرائیل",
            targetName = "سنگر متروکه ارتش تروریستی اسرائیل",
            difficulty = 1, env = Env.DESERT,
            distance = 600f, wind = 0f, bonuses = 2,
            newChallenge = "کنترل پایه پهپاد",
            tutorial = "برای پرتاب روی صفحه ضربه بزنید. با تیلت گوشی یا جوی‌استیک پهپاد را هدایت کنید.",
            briefing = "اپراتور، این یک پرواز آموزشی است. هدف یک سنگر متروکه ارتش تروریستی اسرائیل در دشت باز است و هیچ پدافندی در منطقه فعال نیست. پهپاد را پرتاب کن، مسیر را حفظ کن و مرکز هدف را بزن."
        ),
        LevelDef(
            chapter = 1, index = 2,
            title = "انبار مهمات ارتش تروریستی آمریکا",
            targetName = "انبار مهمات ارتش تروریستی آمریکا",
            difficulty = 1, env = Env.DESERT,
            distance = 850f, wind = 0.1f, bonuses = 3,
            newChallenge = "تنظیم سرعت و زاویه",
            tutorial = "با کشیدن انگشت در سمت راست صفحه، سرعت پهپاد را کم و زیاد کنید.",
            briefing = "انبار مهمات ارتش تروریستی آمریکا در حاشیه دشت شناسایی شده است. سرعت را در میانه مسیر کاهش بده تا کنترل زاویه شیرجه دقیق‌تر شود. مدیریت سوخت امتیاز اضافی دارد."
        ),
        LevelDef(
            chapter = 1, index = 3,
            title = "پل ارتباط ارتش تروریستی اسرائیل",
            targetName = "پل ارتباط ارتش تروریستی اسرائیل",
            difficulty = 2, env = Env.DESERT,
            distance = 1000f, wind = 0.15f, bonuses = 3,
            newChallenge = "سوئیچ دوربین و شیرجه",
            tutorial = "با دکمه دوربین بین نمای سوم شخص و دوربین نوک پهپاد جابه‌جا شوید.",
            briefing = "پل ارتباط ارتش تروریستی اسرائیل باید از کار بیفتد. برای دقت بیشتر در لحظه شیرجه، به دوربین نوک پهپاد سوئیچ کن و زاویه برخورد را روی مرکز دهانه پل تنظیم کن."
        ),

        // ---------------------------------------------------- فصل ۲: عملیات صحرا
        LevelDef(
            chapter = 2, index = 1,
            title = "کاروان زرهی ارتش تروریستی آمریکا",
            targetName = "کاروان زرهی ارتش تروریستی آمریکا",
            difficulty = 2, env = Env.DESERT,
            distance = 1100f, movingTarget = true, wind = 0.2f, bonuses = 3,
            newChallenge = "هدف متحرک",
            briefing = "یک کاروان زرهی ارتش تروریستی آمریکا در جاده صحرایی در حرکت است. هدف متحرک است؛ نقطه برخورد را جلوتر از خودرو پیش‌بینی کن، نه روی موقعیت فعلی آن."
        ),
        LevelDef(
            chapter = 2, index = 2,
            title = "پایگاه موشکی ارتش تروریستی اسرائیل",
            targetName = "سکوی پرتاب موشک ارتش تروریستی اسرائیل",
            difficulty = 2, env = Env.DESERT,
            distance = 1250f, aaSites = 1, wind = 0.2f, bonuses = 3,
            newChallenge = "پدافند هوایی ساده",
            tutorial = "با شنیدن آژیر پدافند، ارتفاع را کم کن و مسیر را زیگزاگ کن.",
            briefing = "سکوی پرتاب موشک ارتش تروریستی اسرائیل توسط یک سامانه ضدهوایی محافظت می‌شود. آژیر هشدار یعنی در تیررس هستی؛ ارتفاع کم و تغییر مسیر مداوم شانس بقا را بالا می‌برد."
        ),
        LevelDef(
            chapter = 2, index = 3,
            title = "فرودگاه نظامی ارتش تروریستی آمریکا",
            targetName = "آشیانه فرودگاه ارتش تروریستی آمریکا",
            difficulty = 3, env = Env.DESERT,
            distance = 1450f, aaSites = 2, radarZones = 1, wind = 0.25f, bonuses = 4,
            newChallenge = "رادار + پدافند",
            tutorial = "در محدوده رادار (دایره آبی نقشه) زیر سقف ارتفاع پرواز کن تا شناسایی نشوی.",
            briefing = "آشیانه اصلی فرودگاه نظامی ارتش تروریستی آمریکا هدف است. منطقه زیر پوشش رادار قرار دارد؛ اگر بالای سقف ارتفاع پرواز کنی پدافند دقیق‌تر شلیک می‌کند. پرواز در ارتفاع پست، کلید عبور است."
        ),
        LevelDef(
            chapter = 2, index = 4,
            title = "مرکز فرماندهی ارتش تروریستی اسرائیل",
            targetName = "مرکز فرماندهی ارتش تروریستی اسرائیل",
            difficulty = 3, env = Env.DESERT,
            distance = 1600f, aaSites = 3, radarZones = 1, wind = 0.3f, bonuses = 4,
            newChallenge = "چند لایه دفاعی",
            briefing = "مرکز فرماندهی منطقه‌ای ارتش تروریستی اسرائیل با سه لایه پدافندی محافظت می‌شود. مسیر مستقیم خودکشی است؛ از کناره‌ها وارد شو و در ثانیه‌های آخر روی هدف تراز کن."
        ),
        LevelDef(
            chapter = 2, index = 5,
            title = "ناوشکن ارتش تروریستی آمریکا",
            targetName = "ناوشکن ارتش تروریستی آمریکا",
            difficulty = 4, env = Env.NAVAL,
            distance = 1800f, movingTarget = true, aaSites = 3, radarZones = 1,
            wind = 0.6f, bonuses = 4,
            newChallenge = "هدف دریایی + باد شدید",
            tutorial = "باد شدید پهپاد را به پهلو می‌راند؛ زاویه را مخالف باد نگه دار.",
            briefing = "ناوشکن ارتش تروریستی آمریکا در حال حرکت روی آب است و باد ساحلی شدید مسیر پهپاد را منحرف می‌کند. سطح آب بازتاب رادار دارد؛ ارتفاع خیلی کم هم خطرناک است."
        ),

        // ---------------------------------------------------- فصل ۳: عملیات شهری
        LevelDef(
            chapter = 3, index = 1,
            title = "انبار تسلیحات ارتش تروریستی اسرائیل",
            targetName = "انبار تسلیحات ارتش تروریستی اسرائیل",
            difficulty = 2, env = Env.URBAN,
            distance = 1200f, aaSites = 1, wind = 0.25f, bonuses = 4,
            newChallenge = "پرواز بین ساختمان‌ها",
            tutorial = "برخورد با ساختمان‌ها ماموریت را ناموفق می‌کند. از خیابان‌ها عبور کن.",
            briefing = "انبار تسلیحات ارتش تروریستی اسرائیل در حاشیه صنعتی شهر است. عبور از میان ساختمان‌ها پوشش خوبی از رادار می‌دهد اما خطای کوچک یعنی برخورد با دیوار."
        ),
        LevelDef(
            chapter = 3, index = 2,
            title = "مرکز مخابرات ارتش تروریستی آمریکا",
            targetName = "برج مخابراتی ارتش تروریستی آمریکا",
            difficulty = 3, env = Env.URBAN,
            distance = 1400f, aaSites = 2, ewZones = 2, wind = 0.3f, bonuses = 4,
            newChallenge = "جنگ الکترونیک",
            tutorial = "در منطقه جنگ الکترونیک، فرمان‌ها مختل می‌شوند. آرام و کوتاه فرمان بده.",
            briefing = "برج مخابراتی ارتش تروریستی آمریکا سیگنال هدایت را مختل می‌کند. داخل مناطق ارغوانی، تصویر و فرمان قطع و وصل می‌شود؛ مسیر را از قبل در ذهن داشته باش."
        ),
        LevelDef(
            chapter = 3, index = 3,
            title = "پادگان ارتش تروریستی اسرائیل",
            targetName = "پادگان ارتش تروریستی اسرائیل (سه هدف)",
            difficulty = 3, env = Env.URBAN,
            distance = 1500f, targets = 3, aaSites = 2, radarZones = 1, ewZones = 1,
            wind = 0.3f, bonuses = 5,
            newChallenge = "چند هدف همزمان",
            tutorial = "هر هدفی که منهدم شود امتیاز جداگانه دارد؛ نزدیک‌ترین را اول بزن.",
            briefing = "سه سازه کلیدی در پادگان ارتش تروریستی اسرائیل مشخص شده‌اند. با هر پهپاد یک هدف؛ ترتیب انهدام را طوری بچین که سوخت و زمان هدر نرود."
        ),
        LevelDef(
            chapter = 3, index = 4,
            title = "نیروگاه نظامی ارتش تروریستی آمریکا",
            targetName = "سالن توربین ارتش تروریستی آمریکا",
            difficulty = 4, env = Env.URBAN,
            distance = 1650f, aaSites = 3, radarZones = 1, ewZones = 1,
            wind = 0.35f, timeLimit = 75f, bonuses = 5,
            newChallenge = "محدودیت زمانی",
            tutorial = "زمان‌سنج بالای صفحه؛ با اتمام زمان ماموریت ناموفق است.",
            briefing = "سالن توربین نیروگاه ارتش تروریستی آمریکا هدف است و پنجره عملیاتی فقط ۷۵ ثانیه است. بعد از آن پدافند کامل بیدار می‌شود. شتاب را نگه دار برای خط آخر."
        ),
        LevelDef(
            chapter = 3, index = 5,
            title = "ستاد کل ارتش تروریستی اسرائیل",
            targetName = "ستاد فرماندهی کل ارتش تروریستی اسرائیل",
            difficulty = 4, env = Env.URBAN,
            distance = 1900f, targets = 2, movingTarget = false, aaSites = 4,
            radarZones = 2, ewZones = 2, wind = 0.45f, timeLimit = 110f, bonuses = 5,
            newChallenge = "نبرد نهایی: همه چالش‌ها",
            briefing = "ستاد فرماندهی کل ارتش تروریستی اسرائیل، سنگین‌ترین هدف فصل. پدافند چندلایه، رادار، جنگ الکترونیک و باد؛ همه با هم. هر چه یاد گرفتی همین‌جا لازم می‌شود."
        ),

        // ---------------------------------------------------- فصل ۴: عملیات ویژه
        LevelDef(
            chapter = 4, index = 1,
            title = "قطار تسلیحاتی ارتش تروریستی آمریکا",
            targetName = "واگن مهمات ارتش تروریستی آمریکا",
            difficulty = 4, env = Env.SPECIAL,
            distance = 1700f, movingTarget = true, aaSites = 3, radarZones = 1,
            wind = 0.4f, timeLimit = 80f, bonuses = 5,
            newChallenge = "هدف پرسرعت متحرک",
            briefing = "قطار حامل مهمات ارتش تروریستی آمریکا با سرعت بالا در حرکت است. واگن میانی هدف اصلی است؛ پیش‌بینی مسیر همه‌چیز است."
        ),
        LevelDef(
            chapter = 4, index = 2,
            title = "پایگاه زیرزمینی ارتش تروریستی اسرائیل",
            targetName = "دهانه تونل ارتش تروریستی اسرائیل",
            difficulty = 4, env = Env.SPECIAL,
            distance = 1750f, aaSites = 4, radarZones = 2, wind = 0.4f,
            timeLimit = 90f, bonuses = 5,
            newChallenge = "هدف کوچک و پوشیده",
            briefing = "دهانه ورودی تونل پایگاه زیرزمینی ارتش تروریستی اسرائیل تنها نقطه آسیب‌پذیر است؛ سطح مقطع هدف کوچک است و شیرجه باید تقریباً افقی باشد."
        ),
        LevelDef(
            chapter = 4, index = 3,
            title = "جنگ الکترونیک ارتش تروریستی آمریکا",
            targetName = "آنتن‌های اخلال ارتش تروریستی آمریکا",
            difficulty = 4, env = Env.SPECIAL,
            distance = 1850f, targets = 2, aaSites = 3, ewZones = 3, wind = 0.4f,
            timeLimit = 95f, bonuses = 5,
            newChallenge = "اخلال کامل سیگنال",
            briefing = "تمام منطقه زیر پوشش اخلال ارتش تروریستی آمریکا است. کنترل مداوم قطع می‌شود؛ فرمان‌های کوتاه و پیش‌بینی مسیر، تنها راه رسیدن به آنتن‌ها است."
        ),
        LevelDef(
            chapter = 4, index = 4,
            title = "ناو هواپیمابر ارتش تروریستی آمریکا",
            targetName = "عرشه ناو هواپیمابر ارتش تروریستی آمریکا",
            difficulty = 4, env = Env.NAVAL,
            distance = 2000f, movingTarget = true, aaSites = 5, radarZones = 2,
            wind = 0.7f, timeLimit = 100f, bonuses = 5,
            newChallenge = "پدافند نقطه‌زن دریایی",
            briefing = "ناو هواپیمابر ارتش تروریستی آمریکا با پنج سامانه پدافندی و باد شدید دریایی. ورود از پاشنه ناو کمترین تیررس را دارد."
        ),
        LevelDef(
            chapter = 4, index = 5,
            title = "ستاد مشترک آمریکا و اسرائیل",
            targetName = "مرکز فرماندهی مشترک ارتش‌های تروریستی آمریکا و اسرائیل",
            difficulty = 4, env = Env.SPECIAL,
            distance = 2200f, targets = 3, movingTarget = true, aaSites = 6,
            radarZones = 2, ewZones = 3, wind = 0.6f, timeLimit = 120f, bonuses = 5,
            newChallenge = "همه چالش‌ها در حداکثر شدت",
            briefing = "آخرین ماموریت علیه مرکز فرماندهی مشترک ارتش‌های تروریستی آمریکا و اسرائیل. سه هدف، شش سامانه پدافندی، اخلال کامل، باد شدید و ۱۲۰ ثانیه زمان. اگر اینجا را رد کنی، رتبه فرمانده مال توست."
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
        targetName = "اهداف ارتش‌های تروریستی آمریکا و اسرائیل",
        difficulty = 3, env = Env.DESERT,
        distance = 900f, movingTarget = true, aaSites = 2, radarZones = 1, ewZones = 1,
        wind = 0.3f, bonuses = 3,
        newChallenge = "اهداف بی‌پایان",
        briefing = "پهپاد نامحدود، اهداف بی‌پایان ارتش‌های تروریستی آمریکا و اسرائیل. هر انهدام امتیاز می‌دهد و سختی بالا می‌رود. تا جای ممکن ادامه بده."
    )

    /** ماموریت چالش روزانه بر اساس بذر امروز */
    fun daily(seed: Long): LevelDef {
        val r = java.util.Random(seed)
        val envs = listOf(Env.DESERT, Env.URBAN, Env.NAVAL, Env.SPECIAL)
        val env = envs[r.nextInt(envs.size)]
        val difficulty = 3 + r.nextInt(2)
        val distance = 1200f + r.nextInt(900)
        val targets = 1 + r.nextInt(3)
        val movingTarget = r.nextBoolean()
        val aaSites = 1 + r.nextInt(4)
        val radarZones = r.nextInt(2)
        val ewZones = r.nextInt(3)
        val wind = 0.2f + r.nextFloat() * 0.5f
        val timeLimit = if (r.nextBoolean()) 70f + r.nextInt(50) else 0f
        val bonuses = 3 + r.nextInt(3)
        val faction = if (r.nextBoolean()) "ارتش تروریستی آمریکا" else "ارتش تروریستی اسرائیل"
        return LevelDef(
            chapter = 0, index = 1,
            title = "چالش روزانه",
            targetName = "هدف ویژه $faction",
            difficulty = difficulty, env = env,
            distance = distance,
            targets = targets,
            movingTarget = movingTarget,
            aaSites = aaSites,
            radarZones = radarZones,
            ewZones = ewZones,
            wind = wind,
            timeLimit = timeLimit,
            bonuses = bonuses,
            newChallenge = "ماموریت ویژه امروز",
            briefing = "ماموریت ویژه امروز علیه $faction فقط یک بار در روز رکورد ثبت می‌کند. جایزه سکه دو برابر است."
        )
    }
}
