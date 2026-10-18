package sk.miro.blocky.domain

import java.text.Normalizer

/** Kategórie výdavkov. [benchmarkShare] = orientačný strop podielu na mesačných výdavkoch (heuristika, nie štatistika). */
enum class Category(private val baseLabel: String, val essential: Boolean, val benchmarkShare: Double?) {
    MASO("Mäso a ryby", true, null),
    /** šunky, salámy, klobásy, párky, paštéty, nátierky, hotové šaláty a lahôdky */
    LAHODKY("Lahôdky a údeniny", true, null),
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
    INE("Iné", false, null),

    /** Vlastné kategórie používateľa – názov, ikonu a farbu určuje [CustomCategories]. */
    CUSTOM1("Vlastná 1", false, null), CUSTOM2("Vlastná 2", false, null), CUSTOM3("Vlastná 3", false, null),
    CUSTOM4("Vlastná 4", false, null), CUSTOM5("Vlastná 5", false, null), CUSTOM6("Vlastná 6", false, null),
    CUSTOM7("Vlastná 7", false, null), CUSTOM8("Vlastná 8", false, null);

    val label: String get() = CustomCategories.labelOf(this) ?: baseLabel
    val isCustom: Boolean get() = name.startsWith("CUSTOM")
    /** vlastná kategória, ktorú používateľ nevytvoril, sa nikde nezobrazuje */
    val active: Boolean get() = !isCustom || CustomCategories.isActive(this)

    companion object {
        /** kategórie, ktoré sa zobrazujú (vstavané + vytvorené vlastné) */
        val visible: List<Category> get() = entries.filter { it.active }
        fun fromName(n: String): Category = entries.firstOrNull { it.name == n } ?: INE
        val FOOD = setOf(MASO, LAHODKY, MLIECNE, PECIVO, OVOCIE_ZELENINA, TRVANLIVE, MRAZENE_HOTOVE, NAPOJE, SLADKOSTI)
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
            "knedl", "hotove jedlo", "polievk", "instant", "bagel", "wrap", "sushi"),
        // lahôdky a údeniny až po hotových jedlách (pizza so šunkou) a pred mäsom (šunka, saláma…) aj pred zeleninou („zemiakový šalát“ nie je zelenina)
        Category.LAHODKY to listOf("sunka", "sunk.", "salam", "salami", "klobas", "parky", "parok", "parky", "spekac",
            "slanin", "spek", "pastet", "jaternic", "tlacenk", "debrecin", "mortadel", "kabanos", "udene", "udena",
            "natierk", "natie", "pomazank", "hummus", "salat vlass", "vlassky", "vlasky salat", "zemiakovy salat",
            "cestovinovy salat", "majonezovy salat", "rybaci salat", "rybi salat", "salat hotov", "hotovy salat",
            "coleslaw", "tzatziki", "chorizo", "fuet", "prosciutto", "serrano", "pancetta", "oskvark", "skvark",
            "lahod", "delikates", "hot dog park", "uherak", "lovecka", "inovec", "vysocin", "herkules",
            "pecenov", "leberk", "sekana", "bryndzova natierka", "vajickova", "tuniakova natierka"),
        Category.MASO to listOf("kurac", "kuracie", "bravc", "hovadz", "morcac", "mlete maso", "maso",
            "rezen", "stehn", "krkovic", "kare ", "file", "losos", "tuniak", "makrel", "ryba", "rybi", "kreveta",
            "hotdog", "prsia", "krid", "pecen", "kacac", "kacica", "kac.", "jahnac", "telac", "kralik", "pstruh", "kapor",
            "treska", "pangasius"),
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
    /** Výsledok zaradenia: [byAi] = kategóriu určila vstavaná AI. */
    data class Result(val category: Category, val byAi: Boolean = false)

    /** Kategórie, ktoré AI nesmie navrhnúť v obchode s tovarom (napr. „espresso“ v Lidli je káva, nie kaviareň). */
    private val notInShops = setOf(Category.RESTAURACIE, Category.UBYTOVANIE, Category.VOLNY_CAS, Category.SLUZBY)
    private val shopTypes = setOf(MerchantType.GROCERY, MerchantType.DRUGSTORE, MerchantType.PHARMACY, MerchantType.HOME)

    /**
     * Kategorizuje položku. Poradie:
     * 1) ručné pravidlo používateľa (názov položky → kategória),
     * 2) záloha za obal (vždy samostatná skupina),
     * 3) TYP PREVÁDZKY: reštaurácia / hotel / wellness / oblečenie… → celý bloček do jednej kategórie,
     * 4) zľava (záporná položka),
     * 5) vstavaná AI [ai] (ak je zapnutá a je si dosť istá),
     * 6) kľúčové slová v názve položky,
     * 7) náhradná kategória podľa typu prevádzky (napr. lekáreň → Zdravie).
     */
    fun categorizeWithSource(
        itemName: String,
        merchantType: MerchantType,
        priceCents: Long,
        userRules: Map<String, Category> = emptyMap(),
        ai: ((String) -> Category?)? = null
    ): Result {
        val n = normalize(itemName)
        userRules[n]?.let { return Result(it) }
        val padded = " $n "
        if (depositKeys.any { padded.contains(" $it") }) return Result(Category.ZALOHY)
        merchantType.single?.let { return Result(it) }
        if (priceCents < 0 && (n.contains("zlav") || n.contains("kupon") || n.isBlank())) return Result(Category.ZLAVY)
        if (ai != null && priceCents >= 0) {
            val p = ai(itemName)
            if (p != null && !(p in notInShops && merchantType in shopTypes)) return Result(p, byAi = true)
        }
        val byName = rules.firstOrNull { (_, keys) -> keys.any { k -> padded.contains(" $k") } }?.first
        return Result(byName ?: merchantType.fallback)
    }

    fun categorizeItem(
        itemName: String,
        merchantType: MerchantType,
        priceCents: Long,
        userRules: Map<String, Category> = emptyMap(),
        ai: ((String) -> Category?)? = null
    ): Category = categorizeWithSource(itemName, merchantType, priceCents, userRules, ai).category

    /** Skratka: typ prevádzky sa určí z jej názvu. */
    fun categorize(
        itemName: String,
        merchantName: String,
        priceCents: Long,
        userRules: Map<String, Category> = emptyMap()
    ): Category = categorizeItem(itemName, MerchantResolver.resolve(merchantName), priceCents, userRules)
}
