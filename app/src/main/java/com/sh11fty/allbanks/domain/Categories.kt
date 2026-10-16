package com.sh11fty.allbanks.domain

/** The concise built-in category set. Subsections are added by the user later. */
object DefaultCategories {
    const val OTHER = "Other"

    data class Default(val name: String, val colour: Int, val icon: String)

    val all: List<Default> = listOf(
        Default("Groceries", 0xFF43A047.toInt(), "🛒"),
        Default("Dining & takeaways", 0xFFFB8C00.toInt(), "🍽️"),
        Default("Transport", 0xFF1E88E5.toInt(), "🚗"),
        Default("Bills & utilities", 0xFF8E24AA.toInt(), "💡"),
        Default("Shopping", 0xFFE91E63.toInt(), "🛍️"),
        Default("Entertainment", 0xFF00ACC1.toInt(), "🎬"),
        Default("Health", 0xFFE53935.toInt(), "🩺"),
        Default("Travel", 0xFF3949AB.toInt(), "✈️"),
        Default("Bank charges & fees", 0xFF6D4C41.toInt(), "🏦"),
        Default("Income", 0xFF2E7D32.toInt(), "💰"),
        Default("Savings & investments", 0xFF00897B.toInt(), "🐖"),
        Default("Cash", 0xFF7CB342.toInt(), "💵"),
        Default("Gifts & charity", 0xFFF4511E.toInt(), "🎁"),
        Default(OTHER, 0xFF78909C.toInt(), "📦"),
    )

    /** Emoji offered when you pick a category icon. */
    val iconChoices: List<String> = listOf(
        "🛒", "🍽️", "☕", "🍺", "🍕", "🚗", "🚌", "🚆", "✈️", "⛽", "🏠", "💡", "📱", "🌐", "🛍️", "👕", "🎬", "🎮",
        "🎵", "📚", "🩺", "💊", "🏋️", "🐶", "🎁", "❤️", "👶", "🎓", "🔧", "🛠️", "🏦", "💰", "💵", "🐖", "📈", "🧾",
        "🧴", "💇", "🎉", "🌳", "🏖️", "📦", "⭐", "🔁",
    )

    val names: Set<String> = all.map { it.name.lowercase() }.toSet()

    private val keywords: List<Pair<Regex, String>> = listOf(
        "fee|charge|interest|overdraft|commission" to "Bank charges & fees",
        "grocer|supermarket|food shop|convenience" to "Groceries",
        "restaurant|dining|takeaway|take away|fast food|cafe|coffee|pub|bar\\b|food & drink|food and drink|eating" to "Dining & takeaways",
        "transport|fuel|petrol|diesel|taxi|parking|train|bus\\b|rail|tfl|car\\b|vehicle|toll|ride" to "Transport",
        "bill|utilit|electric|gas\\b|water|broadband|internet|phone|mobile|council|insurance|rent|mortgage|licen|energy|tv\\b" to "Bills & utilities",
        "travel|holiday|hotel|flight|airline|accommodation|vacation" to "Travel",
        "entertain|streaming|subscription|leisure|gaming|game|cinema|music|sport event|hobby" to "Entertainment",
        "health|pharmac|medical|dental|optic|fitness|gym|wellbeing|doctor" to "Health",
        "income|salary|wage|payroll|refund|dividend|benefit|pension income|cashback" to "Income",
        "saving|invest|pension|isa\\b" to "Savings & investments",
        "cash|atm|withdrawal" to "Cash",
        "gift|charity|donation|present" to "Gifts & charity",
        "shop|retail|cloth|fashion|electronic|home|garden|general|merchandise|department|online|market|book|beauty|personal care|pet" to "Shopping",
    ).map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }

    /** Maps Endute's free-text category onto the built-in set, or null if nothing fits. */
    fun map(provider: String?): String? {
        val p = provider?.trim().orEmpty()
        if (p.isEmpty()) return null
        all.firstOrNull { it.name.equals(p, ignoreCase = true) }?.let { return it.name }
        return keywords.firstOrNull { it.first.containsMatchIn(p) }?.second
    }
}
