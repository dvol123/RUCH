package com.ruch.translator.translation

import android.content.Context
import android.util.Log
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Machine Translation using dictionary-based approach
 *
 * For full NLLB-200 support, ONNX models would need to be added.
 * Currently uses a comprehensive dictionary for Russian-Chinese translation.
 */
//noinspection SpellCheckingInspection
class NllbTranslator(private val context: Context) {

    companion object {
        private const val TAG = "NllbTranslator"
    }

    private var isInitialized = false

    /**
     * Initialize translator
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing Dictionary Translator...")
            isInitialized = true
            Log.i(TAG, "Dictionary Translator initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Translator: ${e.message}", e)
            false
        }
    }

    /**
     * Translate text between languages
     * @param text Source text
     * @param sourceLang Source language
     * @param targetLang Target language
     * @return Translated text
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""

        try {
            Log.d(TAG, "Translating: '$text' from ${sourceLang.getDisplayName()} to ${targetLang.getDisplayName()}")
            
            val translated = translateWithDictionary(text, sourceLang, targetLang)
            
            Log.d(TAG, "Translated to: '$translated'")
            translated
        } catch (e: Exception) {
            Log.e(TAG, "Translation error: ${e.message}", e)
            text // Return original text on error
        }
    }

    /**
     * Dictionary-based translation
     */
    private fun translateWithDictionary(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        val dictionary = when (sourceLang) {
            Language.RUSSIAN -> russianToChineseDictionary
            Language.CHINESE -> chineseToRussianDictionary
        }

        // For Chinese text, handle characters differently
        val translated = if (sourceLang == Language.CHINESE) {
            translateChineseText(text, dictionary)
        } else {
            translateRussianText(text, dictionary)
        }

        return translated
    }

    /**
     * Translate Russian text using word-by-word lookup
     */
    private fun translateRussianText(text: String, dictionary: Map<String, String>): String {
        val words = text.split(Regex("\\s+"))
        val translatedWords = words.map { word ->
            val cleanWord = word.trim(' ', '.', ',', '!', '?', ':', ';')
            val punctuation = word.substringAfter(cleanWord)
            
            // Try exact match first, then lowercase
            dictionary[cleanWord]?.let { it + punctuation }
                ?: dictionary[cleanWord.lowercase()]?.let { it + punctuation }
                ?: word
        }

        return translatedWords.joinToString("")
    }

    /**
     * Translate Chinese text by checking all dictionary entries
     */
    private fun translateChineseText(text: String, dictionary: Map<String, String>): String {
        var result = text
        
        // Sort by length (longest first) to handle multi-character words
        val sortedEntries = dictionary.entries.sortedByDescending { it.key.length }
        
        for ((chinese, russian) in sortedEntries) {
            if (result.contains(chinese)) {
                result = result.replace(chinese, russian)
            }
        }
        
        return result
    }

    // Comprehensive Russian to Chinese dictionary
    private val russianToChineseDictionary = mapOf(
        // Greetings
        "привет" to "你好", "Привет" to "你好",
        "здравствуйте" to "您好", "Здравствуйте" to "您好",
        "добрый" to "好", "утро" to "早上", "день" to "天", "вечер" to "晚上",
        "спасибо" to "谢谢", "Спасибо" to "谢谢",
        "пожалуйста" to "请", "Пожалуйста" to "请",
        "извините" to "对不起", "Извините" to "对不起",
        "до" to "到", "свидания" to "再见",
        
        // Pronouns
        "я" to "我", "Я" to "我",
        "ты" to "你", "Ты" to "你",
        "вы" to "您", "Вы" to "您",
        "он" to "他", "Он" to "他",
        "она" to "她", "Она" to "她",
        "оно" to "它",
        "мы" to "我们", "Мы" to "我们",
        "они" to "他们", "Они" to "他们",
        
        // Question words
        "как" to "怎么样", "Как" to "怎么样",
        "что" to "什么", "Что" to "什么",
        "где" to "哪里", "Где" to "哪里",
        "когда" to "什么时候", "Когда" to "什么时候",
        "кто" to "谁", "Кто" to "谁",
        "почему" to "为什么", "Почему" to "为什么",
        "куда" to "去哪里", "откуда" to "从哪里",
        "сколько" to "多少",
        
        // Common words
        "да" to "是", "Да" to "是",
        "нет" to "不是", "Нет" to "不是",
        "хорошо" to "好", "Хорошо" to "好",
        "плохо" to "不好", "Плохо" to "不好",
        "очень" to "很", "Очень" to "很",
        "тоже" to "也", "Тоже" to "也",
        "и" to "和", "а" to "而",
        "но" to "但是", "или" to "或者",
        "с" to "和", "в" to "在", "на" to "在",
        "это" to "这个", "Это" to "这个",
        "тот" to "那个",
        
        // Verbs
        "быть" to "是", "был" to "是", "была" to "是",
        "иметь" to "有", "есть" to "有", "имею" to "有",
        "делать" to "做", "делаю" to "做", "делает" to "做",
        "говорить" to "说", "говорю" to "说", "говорит" to "说",
        "сказать" to "说", "скажу" to "说",
        "знать" to "知道", "знаю" to "知道", "знает" to "知道",
        "понимать" to "理解", "понимаю" to "理解",
        "понять" to "明白", "понял" to "明白",
        "идти" to "走", "иду" to "走", "идёт" to "走",
        "пойти" to "去", "хожу" to "去",
        "прийти" to "来", "приду" to "来",
        "видеть" to "看见", "вижу" to "看见",
        "слышать" to "听见", "слышу" to "听见",
        "любить" to "爱", "люблю" to "爱", "любит" to "爱",
        "нравиться" to "喜欢", "нравится" to "喜欢",
        "хотеть" to "想要", "хочу" to "想要", "хочет" to "想要",
        "мочь" to "能", "могу" to "能", "может" to "能",
        "работать" to "工作", "работаю" to "工作",
        "учиться" to "学习", "учусь" to "学习",
        "жить" to "住", "живу" to "住",
        "думать" to "想", "думаю" to "想",
        "читать" to "读", "читаю" to "读",
        "писать" to "写", "пишу" to "写",
        
        // Nouns - People
        "человек" to "人", "люди" to "人们",
        "мужчина" to "男人", "женщина" to "女人",
        "ребёнок" to "孩子", "дети" to "孩子们",
        "друг" to "朋友", "друзья" to "朋友们",
        "семья" to "家庭",
        "отец" to "父亲", "папа" to "爸爸",
        "мать" to "母亲", "мама" to "妈妈",
        "брат" to "兄弟", "сестра" to "姐妹",
        "сын" to "儿子", "дочь" to "女儿",
        
        // Nouns - Places
        "дом" to "家", "квартира" to "公寓",
        "комната" to "房间",
        "город" to "城市", "страна" to "国家",
        "улица" to "街道",
        "магазин" to "商店",
        "ресторан" to "餐厅",
        "больница" to "医院",
        "школа" to "学校",
        "университет" to "大学",
        "офис" to "办公室",
        "работа" to "工作",
        
        // Nouns - Food
        "еда" to "食物", "пища" to "食物",
        "вода" to "水",
        "чай" to "茶", "кофе" to "咖啡",
        "молоко" to "牛奶", "сок" to "果汁",
        "хлеб" to "面包", "рис" to "米饭",
        "мясо" to "肉", "говядина" to "牛肉", "свинина" to "猪肉",
        "курица" to "鸡肉",
        "рыба" to "鱼",
        "овощи" to "蔬菜", "фрукты" to "水果",
        "яблоко" to "苹果", "банан" to "香蕉",
        
        // Nouns - Time
        "время" to "时间",
        "сегодня" to "今天", "завтра" to "明天", "вчера" to "昨天",
        "утро" to "早上", "день" to "白天", "вечер" to "晚上", "ночь" to "夜晚",
        "час" to "小时", "минута" to "分钟", "секунда" to "秒",
        "неделя" to "周", "месяц" to "月", "год" to "年",
        
        // Numbers
        "один" to "一", "два" to "二", "три" to "三",
        "четыре" to "四", "пять" to "五",
        "шесть" to "六", "семь" to "七", "восемь" to "八",
        "девять" to "九", "десять" to "十",
        "сто" to "百", "тысяча" to "千",
        
        // Adjectives
        "большой" to "大", "маленький" to "小",
        "новый" to "新", "старый" to "旧",
        "хороший" to "好", "плохой" to "坏",
        "красивый" to "漂亮", "милый" to "可爱",
        "умный" to "聪明", "глупый" to "愚蠢",
        "добрый" to "善良", "злой" to "邪恶",
        "важный" to "重要", "простой" to "简单",
        "сложный" to "复杂", "лёгкий" to "容易",
        "трудный" to "困难", "дорогой" to "贵",
        "дешёвый" to "便宜",
        "горячий" to "热", "холодный" to "冷",
        "тёплый" to "温暖", "прохладный" to "凉爽",
        
        // Weather
        "погода" to "天气",
        "солнце" to "太阳", "дождь" to "雨",
        "снег" to "雪", "ветер" to "风",
        "облако" to "云",
        
        // Common phrases
        "не" to "不", "можно" to "可以", "нельзя" to "不能",
        "нужно" to "需要", "надо" to "必须",
        "вот" to "这是", "там" to "那里", "тут" to "这里",
        "сейчас" to "现在", "потом" to "然后",
        "всегда" to "总是", "иногда" to "有时",
        "никогда" to "从不",
        
        // Transportation
        "машина" to "汽车", "автобус" to "公交车",
        "поезд" to "火车", "самолёт" to "飞机",
        "метро" to "地铁", "такси" to "出租车"
    )

    // Chinese to Russian dictionary
    private val chineseToRussianDictionary = mapOf(
        // Greetings
        "你好" to "привет", "您好" to "здравствуйте",
        "早上好" to "доброе утро", "晚安" to "доброй ночи",
        "谢谢" to "спасибо", "请" to "пожалуйста",
        "对不起" to "извините", "再见" to "до свидания",
        
        // Pronouns
        "我" to "я", "你" to "ты", "您" to "вы",
        "他" to "он", "她" to "она", "它" to "оно",
        "我们" to "мы", "他们" to "они",
        
        // Question words
        "怎么样" to "как", "什么" to "что",
        "哪里" to "где", "什么时候" to "когда",
        "谁" to "кто", "为什么" to "почему",
        "多少" to "сколько",
        
        // Common words
        "是" to "да", "不是" to "нет",
        "好" to "хорошо", "不好" to "плохо",
        "很" to "очень", "也" to "тоже",
        "和" to "и", "但是" to "но", "或者" to "или",
        "这个" to "это", "那个" to "то",
        "在" to "в/на",
        
        // Verbs
        "有" to "иметь", "做" to "делать",
        "说" to "говорить", "知道" to "знать",
        "理解" to "понимать", "明白" to "понять",
        "走" to "идти", "去" to "пойти", "来" to "прийти",
        "看见" to "видеть", "听见" to "слышать",
        "爱" to "любить", "喜欢" to "нравиться",
        "想要" to "хотеть", "能" to "мочь",
        "工作" to "работать", "学习" to "учиться",
        "住" to "жить", "想" to "думать",
        "读" to "читать", "写" to "писать",
        
        // Nouns - People
        "人" to "человек", "人们" to "люди",
        "男人" to "мужчина", "女人" to "женщина",
        "孩子" to "ребёнок",
        "朋友" to "друг", "家庭" to "семья",
        "父亲" to "отец", "爸爸" to "папа",
        "母亲" to "мать", "妈妈" to "мама",
        "兄弟" to "брат", "姐妹" to "сестра",
        "儿子" to "сын", "女儿" to "дочь",
        
        // Nouns - Places
        "家" to "дом", "公寓" to "квартира",
        "房间" to "комната",
        "城市" to "город", "国家" to "страна",
        "街道" to "улица", "商店" to "магазин",
        "餐厅" to "ресторан", "医院" to "больница",
        "学校" to "школа", "大学" to "университет",
        "办公室" to "офис",
        
        // Nouns - Food
        "食物" to "еда", "水" to "вода",
        "茶" to "чай", "咖啡" to "кофе",
        "牛奶" to "молоко", "果汁" to "сок",
        "面包" to "хлеб", "米饭" to "рис",
        "肉" to "мясо", "牛肉" to "говядина",
        "猪肉" to "свинина", "鸡肉" to "курица",
        "鱼" to "рыба", "蔬菜" to "овощи",
        "水果" to "фрукты", "苹果" to "яблоко",
        
        // Nouns - Time
        "时间" to "время",
        "今天" to "сегодня", "明天" to "завтра", "昨天" to "вчера",
        "早上" to "утро", "白天" to "день", "晚上" to "вечер", "夜晚" to "ночь",
        "小时" to "час", "分钟" to "минута", "秒" to "секунда",
        "周" to "неделя", "月" to "месяц", "年" to "год",
        
        // Numbers
        "一" to "один", "二" to "два", "三" to "три",
        "四" to "четыре", "五" to "пять",
        "六" to "шесть", "七" to "семь", "八" to "восемь",
        "九" to "девять", "十" to "десять",
        "百" to "сто", "千" to "тысяча",
        
        // Adjectives
        "大" to "большой", "小" to "маленький",
        "新" to "новый", "旧" to "старый",
        "漂亮" to "красивый", "可爱" to "милый",
        "聪明" to "умный", "重要" to "важный",
        "简单" to "простой", "复杂" to "сложный",
        "容易" to "лёгкий", "困难" to "трудный",
        "贵" to "дорогой", "便宜" to "дешёвый",
        "热" to "горячий", "冷" to "холодный",
        
        // Weather
        "天气" to "погода",
        "太阳" to "солнце", "雨" to "дождь",
        "雪" to "снег", "风" to "ветер", "云" to "облако",
        
        // Common phrases
        "不" to "не", "可以" to "можно", "不能" to "нельзя",
        "需要" to "нужно", "必须" to "надо",
        "这是" to "вот", "那里" to "там", "这里" to "тут",
        "现在" to "сейчас", "然后" to "потом",
        "总是" to "всегда", "有时" to "иногда",
        
        // Transportation
        "汽车" to "машина", "公交车" to "автобус",
        "火车" to "поезд", "飞机" to "самолёт",
        "地铁" to "метро", "出租车" to "такси"
    )

    /**
     * Check if translator is ready
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Release resources
     */
    fun release() {
        isInitialized = false
        Log.i(TAG, "Translator released")
    }
}
