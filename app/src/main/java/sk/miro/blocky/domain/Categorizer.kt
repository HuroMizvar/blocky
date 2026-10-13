package sk.miro.blocky.domain

import java.text.Normalizer

/** Kategórie výdavkov. [benchmarkShare] = orientačný strop podielu na mesačných výdavkoch (heuristika, nie štatistika). */
enum class Category(val label: String, val essential: Boolean, val benchmarkShare: Double?) {
    MASO("Mäso a ryby", true, null),
    MLIECNE("Mliečne a vajcia", true, null),
    PECIVO("Pečivo", true, null),
    OVOCIE_ZELENINA("Ovocie a zelenina", true, null),
    TRVANLIVE("Trvanlivé potraviny", true, null),
    MRAZENE_HOTOVE("Mrazené a hotové jedlá", false, null),
    NAPOJE("Nealko nápoje", false, 0.08),
    ALKOHOL("Alkohol", false, 0.07),
    TABAK("Tabak", false, 0.05),
    SLADKOSTI("Sladkosti a snacky", false, 0.08),
    DROGERIA("Drogéria a hygiena", true, null),
    DOMACNOST("Domácnosť", true, null),
    ZDRAVIE("Lekáreň a zdravie", true, null),
    ZVIERATA("Zvieratá", true, null),
    RESTAURACIE("Reštaurácie a fast food", false, 0.15),
    UBYTOVANIE("Ubytovanie", false, null),
    VOLNY_CAS("Wellness, šport, zábava", false, null),
    OBLECENIE("Oblečenie a obuv", false, null),
    ELEKTRONIKA("Elektronika", false, null),
    SLUZBY("Služby", true, null),
    PHM("Pohonné hmoty", true, null),
    AUTO_MOTO("Auto a moto", true, null),
    /** Vratné zálohy – sledujú sa ZVLÁŠŤ, nezapočítavajú sa do výdavkov. */
    ZALOHY("Zálohy za fľaše a plechovky", true, null),
    ZLAVY("Zľavy", true, null),
    INE("Iné", false, null);

    companion object {
        fun fromName(n: String): Category = entries.firstOrNull { it.name == n } ?: INE
        val FOOD = setOf(MASO, MLIECNE, PECIVO, OVOCIE_ZELENINA, TRVANLIVE, MRAZENE_HOTOVE, NAPOJE, SLADKOSTI)
    }
}

object Categorizer {

    fun normalize(s: String): String {
        val noDia = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return noDia.lowercase()
            .replace(Regex("[^a-z0-9%., ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val depositKeys = listOf("zaloh", "zal.", "zal ", "vratn", "vykup obal", "vratenie obal", "pfand", "deposit")

    // Kľúčové slovo musí byť na začiatku slova ("gin" nezachytí "Ibalgin").
    // Poradie je dôležité: špecifickejšie kategórie skôr (napr. "pivo" pred "nápoje").
    private val rules: List<Pair<Category, List<String>>> = listOf(
        Category.ZLAVY to listOf("zlava", "kupon", "bonus", "vernostn"),
        // pred TRVANLIVE kvôli slovu „olej“ (motorový olej ≠ slnečnicový)
        Category.AUTO_MOTO to listOf("motorov", "motor. olej", "olej motor", "5w", "10w", "15w", "0w",
            "ostrekov", "ostrekovac", "kvapalina do ostrek", "antifriz", "nemrzn", "chladiaca kvapal",
            "autokozmet", "autoshampon", "autosampon", "auto shampon", "stierac", "dialnicna znamka", "e-znamka",
            "eznamka", "vignette", "pneu", "autobater", "autoziarov", "vonavka do auta", "osviezovac do auta",
            "autoumyvar", "umyvanie auta", "umytie auta", "car wash", "carwash", "program umyv", "vysavac auto",
            "motocykl", "prilba", "brzdova kvapal", "olejovy filter", "vzduchovy filter", "snehove retaze"),
        Category.TABAK to listOf("cigaret", "tabak", "marlboro", "camel", "l m ", "lm red", "west ", "petra ",
            "iqos", "heets", "terea", "glo ", "neo stick", "e-cig", "vape", "zapalovac"),
        Category.ALKOHOL to listOf("pivo", "lezak", "lager", "radler", "vino", "vin.", "sekt", "prosecco",
            "vodka", "borovick", "slivovic", "hruskovic", "rum", "whisk", "gin ", "liker", "jager", "becherovk",
            "fernet", "tatratea", "demanovk", "pilsner", "zlaty bazant", "smadny mnich", "kelt", "corgon",
            "staropramen", "budweiser", "heineken", "kozel", "saris", "steiger", "cider", "frisco", "tokaj",
            "frankovk", "veltlin", "rizling", "cabernet", "merlot", "chardonnay", "sauvignon", "muller"),
        Category.ZVIERATA to listOf("krmivo", "granule", "pedigree", "whiskas", "felix", "friskies", "kitekat",
            "purina", "stelivo", "podstielk", "pre psov", "pre macky", "pamlsk"),
        Category.SLADKOSTI to listOf("cokolad", "cokol", "tycink", "keks", "susienk", "oblat", "chips", "cipsy",
            "chrumk", "bonbon", "cukrik", "zuvack", "gumene", "haribo", "milka", "kinder", "orion", "horalk",
            "zmrzlin", "nanuk", "croissant cok", "muffin", "dezert", "torta",
            "pukance", "slane tycinky", "arasid", "orechy solen", "lentilk", "mars", "snickers", "twix",
            "kitkat", "kit kat", "bounty", "tatranka", "deli", "fidorka", "studentska", "pringles", "doritos", "lays",
            "bake rolls", "krekr"),
        Category.NAPOJE to listOf("kofola", "coca", "cola", "pepsi", "fanta", "sprite", "mineral",
            "voda ", "budis", "rajec", "mattoni", "salvator", "dzus", "juice", "nektar", "limonad",
            "sirup", "ladovy caj", "ice tea", "energet", "red bull", "monster", "semtex", "big shock", "kava ",
            "kava,", "caj ", "nescafe", "jacobs", "tchibo", "rauch", "relax", "hello", "vinea", "tonic", "smoothie"),
        Category.MRAZENE_HOTOVE to listOf("mrazen", "pizza", "lasagn", "hranolk", "bryndzove", "pirohy",
            "knedl", "hotove jedlo", "polievk", "instant", "bagel", "wrap", "salat hotov", "sushi"),
        Category.MASO to listOf("kurac", "kuracie", "bravc", "hovadz", "morcac", "mlete maso", "maso",
            "sunka", "salam", "klobas", "parky", "parok", "slanin", "spek", "pecen", "rezen", "stehn", "krkovic",
            "kare ", "file", "losos", "tuniak", "makrel", "ryba", "rybi", "kreveta", "pastet", "jaternic",
            "tlacenk", "hotdog", "salami", "debrecin", "mortadel", "kabanos", "prsia", "krid", "udene"),
        Category.MLIECNE to listOf("mlieko", "mliek", "jogurt", "syr ", "syr,", "syry", "syr.", "eidam", "gouda",
            "mozzarel", "niva", "bryndz", "tvaroh", "smotan", "maslo", "kefir", "acidko", "zakys", "cmar",
            "vajc", "vajic", "cottage", "parenica", "korbacik", "ostiepok", "nite", "rama", "skyr", "puding",
            "termix", "lucina", "zlatohor", "hermelin", "camembert", "parmezan", "mascarpone", "ricott"),
        Category.PECIVO to listOf("rozok", "rozky", "chlieb", "chleb", "bageta", "baget", "kaizerk", "vianock",
            "pletenk", "loupak", "croissant", "pagac", "toast", "pecivo", "zemla", "bulka", "kolac", "buchta",
            "langos", "tortilla", "knackebrot", "sendvic"),
        Category.OVOCIE_ZELENINA to listOf("jablk", "hrusk", "banan", "pomaranc", "mandarin", "citron", "limet",
            "grep", "hrozno", "jahod", "malin", "cucoried", "slivk", "brosky", "marhul", "kiwi", "ananas", "mango",
            "avokad", "melon", "zemiak", "cibul", "cesnak", "mrkv", "petrzlen", "zeler", "paradajk", "rajcin",
            "uhork", "paprik", "salat", "kapust", "karfiol", "brokolic", "cuket", "baklazan", "spenat", "rukol",
            "redkovk", "hriby", "sampinon", "huby", "tekvic", "kalerab", "pazit", "bylink", "ovocie",
            "zelenin", "kukuric"),
        Category.TRVANLIVE to listOf("muk", "cukor", "sol ", "sol,", "ryza", "ryza ", "cestovin", "spaget",
            "penne", "fusilli", "olej", "ocot", "kecup", "horcic", "majonez", "konzerv", "fazul", "sosovic",
            "cicer", "musli", "vlocky", "cornflakes", "med ", "dzem", "lekvar", "nutella", "korenie", "paprika mlet",
            "drozdie", "praskov", "omack", "bujon", "kakao", "orechy", "strukovin", "pecivo trvan",
            "paradajkovy pretlak", "pretlak", "kuskus", "bulgur", "pohanka", "jacmen"),
        Category.DROGERIA to listOf("sampon", "kondicioner", "mydlo", "sprchov", "zubn", "pasta na zuby",
            "kefka", "dezodor", "antiperspir", "holiac", "ziletk", "plienk", "vlhcene", "vata", "tampon",
            "vlozk", "krem", "pletov", "parfum", "kozmet", "nivea", "dove", "colgate", "signal", "gillette",
            "rexona", "lak na", "farba na vlasy", "odlicov"),
        Category.DOMACNOST to listOf("toaletny", "toal. papier", "kuchynske utierk", "papierove", "servitk",
            "pracie", "prasok", "gel na pranie", "avivaz", "jar ", "pur ", "jar,", "saponat", "cistiac",
            "domestos", "savo", "bref", "wc ", "vrecia", "vrecka na", "alobal", "potravinova folia", "baterie",
            "zarovk", "sviecka", "spongia", "hubka", "tablety do um", "finish", "calgonit", "somat", "mikrovlak",
            "taska", "igelit"),
        Category.ZDRAVIE to listOf("paralen", "ibalgin", "nurofen", "vitamin", "magnez", "zinok", "acc ",
            "strepsils", "olynth", "tabl.", "tbl", "kapsul", "sirup proti", "naplast", "obvaz", "lieky", "doplatok"),
        Category.PHM to listOf("natural 95", "natural 98", "nafta", "diesel", "benzin", "lpg", "cng", "adblue",
            "verva", "maxx motion", "v-power", "efix", "palivo", "tankovanie")
    )

    /**
     * Kategorizuje položku. Poradie:
     * 1) ručné pravidlo používateľa (názov položky → kategória),
     * 2) záloha za obal (vždy samostatná skupina),
     * 3) TYP PREVÁDZKY: reštaurácia / hotel / wellness / oblečenie… → celý bloček do jednej kategórie,
     * 4) zľava (záporná položka),
     * 5) kľúčové slová v názve položky,
     * 6) náhradná kategória podľa typu prevádzky (napr. lekáreň → Zdravie).
     */
    fun categorizeItem(
        itemName: String,
        merchantType: MerchantType,
        priceCents: Long,
        userRules: Map<String, Category> = emptyMap()
    ): Category {
        val n = normalize(itemName)
        userRules[n]?.let { return it }
        val padded = " $n "
        if (depositKeys.any { padded.contains(" $it") }) return Category.ZALOHY
        merchantType.single?.let { return it }
        if (priceCents < 0 && (n.contains("zlav") || n.contains("kupon") || n.isBlank())) return Category.ZLAVY
        val byName = rules.firstOrNull { (_, keys) -> keys.any { k -> padded.contains(" $k") } }?.first
        return byName ?: merchantType.fallback
    }

    /** Skratka: typ prevádzky sa určí z jej názvu. */
    fun categorize(
        itemName: String,
        merchantName: String,
        priceCents: Long,
        userRules: Map<String, Category> = emptyMap()
    ): Category = categorizeItem(itemName, MerchantResolver.resolve(merchantName), priceCents, userRules)
}
