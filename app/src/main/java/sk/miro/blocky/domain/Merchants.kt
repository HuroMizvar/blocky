package sk.miro.blocky.domain

/**
 * Typ prevádzky – PRVÝ parameter kategorizácie.
 * [single] = celý bloček patrí do jednej kategórie (reštaurácia, hotel…);
 * inak sa položky triedia podľa názvu a [fallback] je kategória pre nerozpoznané položky.
 */
enum class MerchantType(val label: String, val single: Category?, val fallback: Category) {
    GROCERY("Obchod s potravinami", null, Category.INE),
    FUEL("Čerpacia stanica", null, Category.INE),
    RESTAURANT("Reštaurácia, kaviareň", Category.RESTAURACIE, Category.RESTAURACIE),
    HOTEL("Hotel, ubytovanie", Category.UBYTOVANIE, Category.UBYTOVANIE),
    WELLNESS("Wellness, šport, zábava", Category.VOLNY_CAS, Category.VOLNY_CAS),
    PHARMACY("Lekáreň", null, Category.ZDRAVIE),
    DRUGSTORE("Drogéria", null, Category.DROGERIA),
    HOME("Domácnosť, záhrada", null, Category.DOMACNOST),
    CLOTHING("Oblečenie, obuv", Category.OBLECENIE, Category.OBLECENIE),
    ELECTRONICS("Elektronika", Category.ELEKTRONIKA, Category.ELEKTRONIKA),
    SERVICES("Služby", Category.SLUZBY, Category.SLUZBY),
    OTHER("Iný obchod", null, Category.INE),
    UNKNOWN("Neznáma prevádzka", null, Category.INE);

    companion object {
        fun fromName(n: String?): MerchantType = entries.firstOrNull { it.name == n } ?: UNKNOWN
        /** Možnosti, ktoré aplikácia ponúkne pri otázke „čo je to za prevádzku?“ */
        val choices = entries.filter { it != UNKNOWN }
    }
}

object MerchantResolver {

    /** Kľúč prevádzky: IČO, ak je na bločku, inak normalizovaný názov. */
    fun key(ico: String?, name: String): String =
        ico?.trim()?.takeIf { it.isNotEmpty() }?.let { "ICO:$it" } ?: "N:" + Categorizer.normalize(name)

    // Krátke tokeny (≤ 4 znaky) sa porovnávajú ako celé slovo, dlhšie ako začiatok slova.
    private val brands: List<Pair<MerchantType, List<String>>> = listOf(
        MerchantType.FUEL to listOf("slovnaft", "omv", "shell", "orlen", "mol", "jurki", "lukoil", "avanti",
            "cerpacia stanica", "benzinka", "eurooil"),
        MerchantType.GROCERY to listOf("lidl", "kaufland", "tesco", "billa", "coop", "jednota", "terno", "fresh",
            "yeme", "penny", "hypernova", "metro", "lunys", "kraj", "delia", "milk agro", "cba", "potraviny",
            "albert", "globus", "ahold", "supermarket", "hypermarket", "mini market", "minimarket", "vecierka",
            "pekaren", "masiarstvo", "zelovoc"),
        MerchantType.RESTAURANT to listOf("mcdonald", "kfc", "burger king", "subway", "starbucks", "costa coffee",
            "bageterie", "pizza hut", "dominos", "restauracia", "restaurant", "bistro", "pizzeria",
            "kaviaren", "cafe", "caffe", "coffee", "pub", "krcma", "pivnica", "hostinec", "kebab", "gyros", "sushi",
            "jedalen", "bufet", "salas", "cukraren", "grill", "burger", "wolt", "foodora", "bolt food"),
        MerchantType.HOTEL to listOf("hotel", "penzion", "ubytovanie", "apartman", "chata", "hostel", "kemp", "camping"),
        MerchantType.WELLNESS to listOf("wellness", "aquapark", "kupele", "spa", "sauna", "fitness", "fitko", "gym",
            "bazen", "kino", "cinema", "cinemax", "lanovk", "ski", "decathlon", "sportisimo", "intersport", "hervis",
            "bowling", "zoo", "muzeum", "divadlo"),
        MerchantType.PHARMACY to listOf("lekaren", "dr max", "benu", "schneider", "pharma", "pilulka", "adc"),
        MerchantType.DRUGSTORE to listOf("dm drogerie", "dm drogeria", "drogeri", "teta", "rossmann", "douglas",
            "notino", "yves rocher", "marionnaud"),
        MerchantType.HOME to listOf("ikea", "hornbach", "obi", "merkury", "mountfield", "jysk", "pepco", "action",
            "tedi", "kik", "baumax", "bauhaus", "xxxlutz", "kika", "mobelix", "asko", "zahradnictvo", "hobby"),
        MerchantType.ELECTRONICS to listOf("datart", "nay", "alza", "planeo", "electro world", "media markt",
            "mediamarkt", "istyle", "orange", "telekom", "o2", "4ka"),
        MerchantType.CLOTHING to listOf("zara", "reserved", "lpp", "ccc", "deichmann", "sinsay", "c a", "h m",
            "hennes", "new yorker", "takko", "primark", "bata", "humanic", "gant"),
        MerchantType.SERVICES to listOf("kadernictvo", "kadernik", "barber", "salon", "autoservis", "pneuservis",
            "posta", "slovenska posta", "cistiaren", "oprava", "servis", "parkovanie", "parking", "taxi", "bolt",
            "zssk", "regiojet", "leo express", "dpb", "slovak lines")
    )

    private fun tokenMatches(padded: String, token: String): Boolean =
        if (token.length <= 4) padded.contains(" $token ") else padded.contains(" $token")

    /** Určí typ prevádzky z názvu. UNKNOWN = treba sa opýtať používateľa. */
    fun resolve(name: String): MerchantType {
        val n = " " + Categorizer.normalize(name).replace(Regex("[,.]"), " ").replace(Regex("\\s+"), " ") + " "
        // Poradie: čerpacie stanice pred obchodmi („Tesco … čerpacia stanica“), drogérie pred obchodmi.
        for ((type, tokens) in brands) if (tokens.any { tokenMatches(n, Categorizer.normalize(it)) }) return type
        return MerchantType.UNKNOWN
    }
}
