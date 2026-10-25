package app.dropi.data

import app.dropi.L
import java.util.Locale

/**
 * Icelandic food words, so a food typed in Icelandic ("hrísgrjón", "tvær brauðsneiðar",
 * "soðnar kartöflur") is found in Dropi's own food list ([Basics]) and speed table ([Gi]),
 * without the on-device AI.
 *
 * Each row is `target|forms`: the target is a name in [Basics] or [Gi] (English, or the
 * Icelandic name a row already has), the forms are the Icelandic words for it, the usual
 * nominative first (shown as "Found: …"), then the other cases people type ("hrísgrjónum",
 * "kartöflur"). A name matches when a form appears as a whole word; the earliest one in the name
 * wins ("banani með hnetusmjöri" is a banana), then the longest ("sætar kartöflur" before
 * "kartöflur"). Typed without accents ("hrisgrjon") also works for forms of 4+ letters.
 */
object FoodNames {

    /** The [Basics]/[Gi] name for the Icelandic food named in [name], or null. */
    fun english(name: String): String? {
        synchronized(cache) { if (cache.containsKey(name)) return cache[name] }
        val n = " " + name.lowercase(Locale.ROOT) + " "
        val found = best(n, exact) ?: best(" " + strip(name.lowercase(Locale.ROOT)) + " ", stripped)
        synchronized(cache) { cache[name] = found }
        return found
    }

    // Screens ask for the same few names on every redraw (speed, drink); remember the last ones.
    private val cache = object : LinkedHashMap<String, String?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?) = size > 300
    }

    /** The Icelandic word for a [Basics]/[Gi] name ("rice" → "hrísgrjón"), or null. */
    fun icelandic(target: String): String? = shown[target]

    /** [Basics] row names for showing: Icelandic when the app is, else as they are. */
    fun shown(target: String): String = if (L.icelandic) icelandic(target) ?: target else target

    private class Form(val text: String, val target: String, val regex: Regex)

    private fun best(n: String, forms: List<Form>): String? {
        var pick: Form? = null
        var at = Int.MAX_VALUE
        for (f in forms) {
            val m = f.regex.find(n) ?: continue
            val i = m.range.first
            if (i < at || (i == at && f.text.length > (pick?.text?.length ?: 0))) { pick = f; at = i }
        }
        return pick?.target
    }

    private fun boundary(s: String) = Regex("(?<![\\p{L}\\d])${Regex.escape(s)}(?![\\p{L}])")

    /** Icelandic letters to plain ones, for names typed without accents. */
    fun strip(s: String): String {
        val b = StringBuilder(s.length + 4)
        for (c in s) when (c) {
            'á' -> b.append('a'); 'é' -> b.append('e'); 'í' -> b.append('i'); 'ó' -> b.append('o')
            'ú' -> b.append('u'); 'ý' -> b.append('y'); 'ö' -> b.append('o'); 'ð' -> b.append('d')
            'þ' -> b.append("th"); 'æ' -> b.append("ae")
            else -> b.append(c)
        }
        return b.toString()
    }

    private val rows: List<Pair<String, List<String>>> = TABLE.lines().mapNotNull { l ->
        val t = l.trim()
        if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
        val bar = t.indexOf('|')
        if (bar <= 0) return@mapNotNull null
        t.substring(0, bar).trim() to t.substring(bar + 1).split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
    }

    private val shown: Map<String, String> = rows.associate { (target, forms) -> target to forms.first() }

    private val exact: List<Form> = rows.flatMap { (target, forms) -> forms.map { Form(it, target, boundary(it)) } }

    // Short words ("ís", "te", "kók") only match with their accents: plain they're other words.
    private val stripped: List<Form> = rows.flatMap { (target, forms) ->
        forms.map { strip(it) }.filter { it.length >= 4 }.distinct().map { Form(it, target, boundary(it)) }
    }

    /** Check with [FoodNamesTest]: every target must be a [Basics] or [Gi] name. */
    internal val targets: Set<String> get() = shown.keys

    private const val TABLE = """
        # Eggs, meat, fish
        egg|egg, eggið, eggi, eggja, eggjum, eggin
        scrambled eggs|eggjahræra, eggjahræru, hrærð egg, hrærðum eggjum
        omelette|eggjakaka, eggjaköku, eggjakökur, ommeletta, ommelettu
        bacon|beikon, beikoni, beikons, beikonið
        sausage|bjúga, bjúgu, bjúgum, bjúgna
        hot dog|pylsa, pylsu, pylsur, pylsum, pylsna, pulsa, pulsu, pulsur, pulsum
        ham|skinka, skinku, skinkur, skinkum, skinkusneið, skinkusneiðar
        chicken|kjúklingur, kjúkling, kjúklingi, kjúklings, kjúklinga, kjúklingabringa, kjúklingabringu, kjúklingabringur, kjúklingabringum, kjúklingaleggir, kjúklingaleggjum
        salmon|lax, laxi, laxinn, laxins, laxaflak, laxaflaki, laxaflök, reyktur lax, reyktum laxi
        cod|þorskur, þorsk, þorski, þorsks, þorskhnakki, þorskhnakka, þorskhnakkar, ýsa, ýsu, ýsuflak, ýsuflaki, fiskur, fisk, fiski, fisks, fiskflak, fiskflaki, steiktur fiskur, steiktum fiski
        tuna|túnfiskur, túnfisk, túnfiski, túnfisks
        lamb|lambakjöt, lambakjöti, lambakjöts, lambalæri, lambalæris, lambahryggur, lambahrygg, lambakótilettur, lambakótilettum, lamb, lambi, lambs
        beef|nautakjöt, nautakjöti, nautakjöts, nautasteik, nautasteikur, nautasteikin, nautahakk, nautahakki, hakk, hakki, steik, steikina, steikur
        pork chop|svínakótiletta, svínakótilettu, svínakótilettur, svínakjöt, svínakjöti, pörusteik, pörusteikina, hamborgarhryggur, hamborgarhrygg
        turkey|kalkúnn, kalkún, kalkúni, kalkúns, kalkúnabringa, kalkúnabringu, kalkúnaskinka
        meatballs|kjötbollur, kjötbollum, kjötbolla, kjötbollu
        shrimp|rækjur, rækjum, rækja, rækju, rækjusalat
        tofu|tófú, tofu
        hangikjöt|hangikjöt, hangikjöti, hangikjöts
        plokkfiskur|plokkfiskur, plokkfisk, plokkfiski, plokkfisks
        fiskibollur|fiskibollur, fiskibollum, fiskibolla, fiskibollu
        harðfiskur|harðfiskur, harðfisk, harðfiski, harðfisks
        liver sausage|lifrarpylsa, lifrarpylsu, lifrarpylsur, lifrarpylsum, slátur, slátri
        blood pudding|blóðmör, blóðmörs, blóðmörinn

        # Dairy
        cheese|ostur, ost, osti, osts, ostsneið, ostsneiðar, ostsneiðum, rifinn ostur, rifnum osti
        cream cheese|rjómaostur, rjómaost, rjómaosti
        butter|smjör, smjöri, smjörs, smjörvi, smjörva
        milk|mjólk, mjólkur, mjólkin, mjólkina, nýmjólk, nýmjólkur, léttmjólk, léttmjólkur, undanrenna, undanrennu, mjólkurglas
        oat milk|haframjólk, haframjólkur
        skyr|skyr, skyri, skyrs, skyrið, hreint skyr, hreinu skyri
        vanilla skyr|vanilluskyr, vanilluskyri
        berry skyr|bláberjaskyr, bláberjaskyri, jarðarberjaskyr, jarðarberjaskyri
        greek yogurt|grísk jógúrt, gríska jógúrt, grískri jógúrt, grískt jógúrt
        yogurt|jógúrt, jógurt, jógúrtið, jógúrtina
        ab mjólk|ab-mjólk, ab mjólk, ab-mjólkur
        súrmjólk|súrmjólk, súrmjólkur, súrmjólkina
        kókómjólk|kókómjólk, kókómjólkur, kókómjólkina
        ice cream|ís, ísinn, ísnum, rjómaís, rjómaísinn, ísbox, ís í brauðformi, ísblóm

        # Bread and cereal
        rye bread|rúgbrauð, rúgbrauði, rúgbrauðs, rúgbrauðsneið, rúgbrauðsneiðar, gróft brauð, grófu brauði, heilhveitibrauð, heilhveitibrauði
        white bread|hvítt brauð, hvítu brauði, fransbrauð, fransbrauði, fransbrauðs, franskbrauð, franskbrauði
        sourdough|súrdeigsbrauð, súrdeigsbrauði, súrdeigsbrauðs
        toast|ristað brauð, ristuðu brauði, ristaða brauðið, ristabrauð, ristabrauði, ristaðar brauðsneiðar
        bread|brauð, brauði, brauðs, brauðið, brauðsneið, brauðsneiðar, brauðsneiðum, brauðsneiðina
        bread roll|rúnstykki, rúnstykkið, rúnstykkjum, rúnstykkis, brauðbolla, brauðbollu, brauðbollur, brauðbollum
        flatkaka|flatkaka, flatköku, flatkökur, flatkökum
        laufabrauð|laufabrauð, laufabrauði, laufabrauðs
        hrökkbrauð|hrökkbrauð, hrökkbrauði, hrökkbrauðs, hrökkbrauðsneið
        rice cake|hrískaka, hrísköku, hrískökur, hrískökum
        crackers|kex, kexi, kexið, kexkökur, kexkökum, saltkex, tekex
        croissant|smjördeigshorn, smjördeigshorni, smjördeigshorns
        bagel|beygla, beyglu, beyglur, beyglum
        baguette|snittubrauð, snittubrauði, baguette
        pita|píta, pítu, pítur, pítubrauð, pítubrauði
        tortilla|tortilla, tortillu, tortillur, tortillum, vefja, vefju, vefjur, vefjum
        chicken wrap|kjúklingavefja, kjúklingavefju, kjúklingavefjur
        naan|naan, naanbrauð, nanbrauð, naanbrauði
        porridge|hafragrautur, hafragraut, hafragrauti, hafragrautar, grautur, graut, grauti
        oatmeal|haframjöl, haframjöli, haframjöls, hafrar, hafra, höfrum
        overnight oats|næturhafrar, næturhöfrum, næturgrautur, næturgraut
        muesli|múslí, músli, múslíi
        granola|granóla, granola, granólað
        cornflakes|kornflögur, kornflögum, kornflex, kornfleks
        cheerios|seríós, serjós, cheerios
        rice|hrísgrjón, hrísgrjónum, hrísgrjóna, hrísgrjónin, soðin hrísgrjón, soðnum hrísgrjónum
        brown rice|hýðishrísgrjón, hýðishrísgrjónum, brún hrísgrjón, brúnum hrísgrjónum
        jasmine rice|jasmín hrísgrjón, jasmínhrísgrjón, jasmínhrísgrjónum
        rice noodles|hrísgrjónanúðlur, hrísgrjónanúðlum
        grjónagrautur|grjónagrautur, grjónagraut, grjónagrauti, grjónagrautar, hrísgrjónagrautur, hrísgrjónagraut, hrísgrjónagrauti
        pasta|pasta, pastað, pastanu, pöstu, pastarétt, pastaréttur
        spaghetti|spagettí, spaghettí, spagettíi, spaghettíi, spaghetti
        macaroni|makkarónur, makkarónum, makkarónu
        lasagne|lasanja, lasanjað, lasanjanu, lasagna, lasagne
        noodles|núðlur, núðlum, núðlu, eggjanúðlur, núðlusúpa, núðlusúpu
        couscous|kúskús, couscous
        quinoa|kínóa, kínúa, kínóað
        bulgur|búlgur, bulgur

        # Potatoes and vegetables
        sweet potato|sæt kartafla, sætar kartöflur, sætri kartöflu, sætum kartöflum, sætkartafla, sætkartöflur, sætkartöflum
        caramelised potatoes|brúnaðar kartöflur, brúnuðum kartöflum, brúnuðu kartöflurnar, sykurbrúnaðar kartöflur, sykurbrúnuðum kartöflum
        fries|franskar, frönskum, franskarnar, franskar kartöflur, frönskum kartöflum
        mashed potato|kartöflumús, kartöflumúsar, kartöflumúsina, kartöflustappa, kartöflustöppu
        baked potato|bökuð kartafla, bakaðri kartöflu, bakaðar kartöflur, bökuðum kartöflum, ofnbakaðar kartöflur, ofnbökuðum kartöflum
        boiled potatoes|soðnar kartöflur, soðnum kartöflum, soðin kartafla, soðinni kartöflu
        potato|kartafla, kartöflu, kartöflur, kartöflum, kartaflna, kartöflurnar, kartöflunum
        crisps|snakk, snakki, snakkpoki, kartöfluflögur, kartöfluflögum, flögur, flögum
        popcorn|popp, poppi, poppkorn, poppkorni, örbylgjupopp
        corn|maís, maísinn, maískorn, maískorni, maísstöngull, maísstöngul
        peas|grænar baunir, grænum baunum, grænar ertur, ertur, ertum
        baked beans|bakaðar baunir, bökuðum baunum
        chickpeas|kjúklingabaunir, kjúklingabaunum
        lentils|linsubaunir, linsubaunum, linsur, linsum
        kidney beans|nýrnabaunir, nýrnabaunum
        beans|baunir, baunum, bauna
        carrot|gulrót, gulrætur, gulrótum, gulrótar, gulrótin
        broccoli|spergilkál, spergilkáli, brokkolí, brokkólí, brokkolíi
        tomato|tómatur, tómat, tómati, tómatar, tómötum, tómata
        cucumber|agúrka, agúrku, agúrkur, agúrkum, gúrka, gúrku, gúrkur, gúrkum
        avocado|lárpera, lárperu, lárperur, lárperum, avókadó, avocado
        salad|salat, salati, salatið, salatinu, grænt salat, grænu salati
        caesar salad|sesarsalat, sesarsalati, caesarsalat, caesarsalati
        hummus|hummus, hummusi, hummusinn

        # Fruit
        banana|banani, banana, bananar, bönunum, banananum, bananinn
        apple|epli, eplið, eplum, epla, eplinu
        orange|appelsína, appelsínu, appelsínur, appelsínum, appelsínan
        pear|pera, peru, perur, perum, peran
        grapes|vínber, vínberin, vínberjum, vínberja
        strawberries|jarðarber, jarðarberin, jarðarberjum, jarðarberja
        blueberries|bláber, bláberin, bláberjum, bláberja
        raspberries|hindber, hindberin, hindberjum, hindberja
        berries|ber, berin, berjum, berja, krækiber, krækiberjum
        mango|mangó, mangóið, mangói
        pineapple|ananas, ananasinn, ananasi
        watermelon|vatnsmelóna, vatnsmelónu, vatnsmelónur, vatnsmelónum
        kiwi|kíví, kívíið, kívíi
        mandarin|mandarína, mandarínu, mandarínur, mandarínum, klementína, klementínu, klementínur, klementínum
        peach|ferskja, ferskju, ferskjur, ferskjum
        raisins|rúsínur, rúsínum, rúsína, rúsínu
        dates|döðlur, döðlum, daðla, döðlu

        # Sweet things
        cake|kaka, köku, kökur, kökum, kakan, kökuna, terta, tertu, tertur, tertum, kökusneið, kökusneiðar, súkkulaðikaka, súkkulaðiköku, súkkulaðikökur, skúffukaka, skúffuköku, skúffukökur, gulrótarkaka, gulrótarköku
        dark chocolate|dökkt súkkulaði, dökku súkkulaði, suðusúkkulaði, suðusúkkulaðis, 70% súkkulaði
        milk chocolate|mjólkursúkkulaði, mjólkursúkkulaðis
        chocolate|súkkulaði, súkkulaðis, súkkulaðið, súkkulaðistykki, súkkulaðistykkis
        lakkrís|lakkrís, lakkrísinn, lakkrísnum, lakkríss
        sweets|nammi, namminu, nammið, sælgæti, sælgætis, bland í poka
        cookie|smákaka, smáköku, smákökur, smákökum, kexkaka, kexköku
        muffin|múffa, múffu, múffur, múffum, bollakaka, bollaköku, bollakökur
        doughnut|kleinuhringur, kleinuhring, kleinuhringir, kleinuhringjum, kleinuhrings, ástarpungur, ástarpungar, ástarpungum
        kleina|kleina, kleinu, kleinur, kleinum
        pancakes|pönnukaka, pönnuköku, pönnukökur, pönnukökum, amerískar pönnukökur, amerískum pönnukökum
        waffle|vaffla, vöfflu, vöfflur, vöfflum
        cinnamon bun|kanilsnúður, kanilsnúð, kanilsnúði, kanilsnúðar, kanilsnúðum, snúður, snúð, snúði, snúðar, snúðum
        danish pastry|vínarbrauð, vínarbrauði, vínarbrauðs, vínarbrauðið
        honey|hunang, hunangi, hunangs
        jam|sulta, sultu, sultur, sultum, berjasulta, berjasultu, rabarbarasulta, rabarbarasultu
        sugar|sykur, sykri, sykurs, strásykur, púðursykur, sykurmoli, sykurmola
        nutella|nutella, nutellu
        peanut butter|hnetusmjör, hnetusmjöri, hnetusmjörs
        almonds|möndlur, möndlum, mandla, möndlu
        nuts|hnetur, hnetum, hneta, hnetu, hnetublanda, hnetublöndu, kasjúhnetur, kasjúhnetum

        # Drinks
        coffee|kaffi, kaffið, kaffis, kaffinu, kaffibolli, kaffibolla, svart kaffi, svörtu kaffi
        latte|latte, kaffi latte, lattè
        cappuccino|cappuccino, kapútsínó
        tea|te, teið, teinu, tebolli, tebolla
        orange juice|appelsínusafi, appelsínusafa, appelsínusafinn, trópí, svali, svala
        apple juice|eplasafi, eplasafa, eplasafinn
        juice|safi, safa, safinn, ávaxtasafi, ávaxtasafa, djús
        smoothie|þeytingur, þeyting, þeytingi, boost, búst
        cola|kók, kóki, kókið, kóla, pepsi, gos, gosi, gosið, gosdrykkur, gosdrykk, gosdrykkir
        appelsín|appelsín, appelsíns, appelsínið
        malt|malt, malti, maltið, maltöl
        energy drink|orkudrykkur, orkudrykk, orkudrykkir, orkudrykkjum
        sports drink|íþróttadrykkur, íþróttadrykk, íþróttadrykkir
        bjór|bjór, bjórinn, bjórs, bjórnum, bjórar
        wine|vín, víni, vínglas, vínglasi, rauðvín, rauðvíni, hvítvín, hvítvíni, freyðivín
        milkshake|mjólkurhristingur, mjólkurhristing, mjólkurhristingi, sjeik, sjeikinn, sjeiki
        hot chocolate|heitt súkkulaði, heitu súkkulaði, kakó, kakói, kakóbolli, kakóbolla

        # Meals and sauces
        pizza|pítsa, pítsu, pítsur, pítsum, pizza, pizzu, pizzur, flatbaka, flatböku, pítsusneið, pítsusneiðar
        burger|hamborgari, hamborgara, hamborgarar, hamborgurum, borgari, borgara, borgarar
        sushi|sushi, súsí
        kebab|kebab, kebabb, kebabinn
        taco|taco, takkó, tacos, takkós
        curry|karrí, karrý, karríréttur, karrírétt, karrýréttur, karrýrétt
        chili con carne|chili con carne, chili
        soup|súpa, súpu, súpur, súpan, súpuna
        kjötsúpa|kjötsúpa, kjötsúpu, kjötsúpur
        fiskisúpa|fiskisúpa, fiskisúpu, humarsúpa, humarsúpu
        ketchup|tómatsósa, tómatsósu, ketchup
        mayonnaise|majónes, majónesi, mæjónes, mæjónesi
        remúlaði|remúlaði, remúlaðið
        sósa|sósa, sósu, sósan, sósuna, brún sósa, brúnni sósu
    """
}

/**
 * Portions in the app's language: Dropi's food list and the on-device AI give portions in
 * English household words ("1 cup", "2 slices", "½ can"); in Icelandic they're shown as
 * "1 bolli", "2 sneiðar", "½ dós". Saved meals keep the English (the AI and backups read it).
 * Anything not recognised is shown as it is.
 */
object Portions {
    private enum class G { M, F, N }

    private class Unit(val one: String, val many: String, val g: G)

    private val units: Map<String, Unit> = buildMap {
        fun add(one: String, many: String, isOne: String, isMany: String, g: G) {
            val u = Unit(isOne, isMany, g)
            put(one, u); put(many, u)
        }
        add("cup", "cups", "bolli", "bollar", G.M)
        add("slice", "slices", "sneið", "sneiðar", G.F)
        add("piece", "pieces", "stykki", "stykki", G.N)
        add("bowl", "bowls", "skál", "skálar", G.F)
        add("glass", "glasses", "glas", "glös", G.N)
        add("spoon", "spoons", "matskeið", "matskeiðar", G.F)
        add("tablespoon", "tablespoons", "matskeið", "matskeiðar", G.F)
        add("tbsp", "tbsp", "msk.", "msk.", G.F)
        add("teaspoon", "teaspoons", "teskeið", "teskeiðar", G.F)
        add("tsp", "tsp", "tsk.", "tsk.", G.F)
        add("can", "cans", "dós", "dósir", G.F)
        add("bottle", "bottles", "flaska", "flöskur", G.F)
        add("carton", "cartons", "ferna", "fernur", G.F)
        add("bag", "bags", "poki", "pokar", G.M)
        add("jar", "jars", "krukka", "krukkur", G.F)
        add("handful", "handfuls", "handfylli", "handfylli", G.F)
        add("portion", "portions", "skammtur", "skammtar", G.M)
        add("serving", "servings", "skammtur", "skammtar", G.M)
        add("plate", "plates", "diskur", "diskar", G.M)
        add("ladle", "ladles", "ausa", "ausur", G.F)
        add("wedge", "wedges", "sneið", "sneiðar", G.F)
        add("scoop", "scoops", "kúla", "kúlur", G.F)
        add("square", "squares", "biti", "bitar", G.M)
        add("bar", "bars", "stykki", "stykki", G.N)
        add("box", "boxes", "pakki", "pakkar", G.M)
        add("egg", "eggs", "egg", "egg", G.N)
        add("cookie", "cookies", "smákaka", "smákökur", G.F)
        add("cracker", "crackers", "kexkaka", "kexkökur", G.F)
        add("pancake", "pancakes", "pönnukaka", "pönnukökur", G.F)
        add("waffle", "waffles", "vaffla", "vöfflur", G.F)
        add("bun", "buns", "bolla", "bollur", G.F)
        add("roll", "rolls", "rúnstykki", "rúnstykki", G.N)
        add("wrap", "wraps", "vefja", "vefjur", G.F)
        add("pita", "pitas", "píta", "pítur", G.F)
        add("taco", "tacos", "taco", "taco", G.N)
        add("burger", "burgers", "hamborgari", "hamborgarar", G.M)
        add("sausage", "sausages", "pylsa", "pylsur", G.F)
        add("breast", "breasts", "bringa", "bringur", G.F)
        add("fillet", "fillets", "flak", "flök", G.N)
        add("chop", "chops", "kótiletta", "kótilettur", G.F)
        add("ball", "balls", "bolla", "bollur", G.F)
        add("cob", "cobs", "stöngull", "stönglar", G.M)
        add("date", "dates", "daðla", "döðlur", G.F)
        add("croissant", "croissants", "horn", "horn", G.N)
        add("bagel", "bagels", "beygla", "beyglur", G.F)
        add("muffin", "muffins", "múffa", "múffur", G.F)
        add("doughnut", "doughnuts", "kleinuhringur", "kleinuhringir", G.M)
        add("donut", "donuts", "kleinuhringur", "kleinuhringir", G.M)
        add("kiwi", "kiwis", "kíví", "kíví", G.N)
        add("clementine", "clementines", "mandarína", "mandarínur", G.F)
        add("mandarin", "mandarins", "mandarína", "mandarínur", G.F)
        add("peach", "peaches", "ferskja", "ferskjur", G.F)
        add("cake", "cakes", "kaka", "kökur", G.F)
        add("avocado", "avocados", "lárpera", "lárperur", G.F)
        add("cucumber", "cucumbers", "agúrka", "agúrkur", G.F)
        add("banana", "bananas", "banani", "bananar", G.M)
        add("apple", "apples", "epli", "epli", G.N)
        add("orange", "oranges", "appelsína", "appelsínur", G.F)
        add("hot dog", "hot dogs", "pylsa", "pylsur", G.F)
    }

    // Size words, by gender: singular then plural.
    private val sizes: Map<String, Map<G, Pair<String, String>>> = mapOf(
        "small" to mapOf(G.M to ("lítill" to "litlir"), G.F to ("lítil" to "litlar"), G.N to ("lítið" to "lítil")),
        "medium" to mapOf(G.M to ("meðalstór" to "meðalstórir"), G.F to ("meðalstór" to "meðalstórar"), G.N to ("meðalstórt" to "meðalstór")),
        "large" to mapOf(G.M to ("stór" to "stórir"), G.F to ("stór" to "stórar"), G.N to ("stórt" to "stór")),
        "big" to mapOf(G.M to ("stór" to "stórir"), G.F to ("stór" to "stórar"), G.N to ("stórt" to "stór")),
        "normal" to mapOf(G.M to ("venjulegur" to "venjulegir"), G.F to ("venjuleg" to "venjulegar"), G.N to ("venjulegt" to "venjuleg")),
    )

    /** [portion] ("1 cup") at [qty] times, for showing in the app's language. */
    fun shown(portion: String, qty: Double = 1.0, english: () -> String): String =
        if (!L.icelandic) english() else icelandic(portion, qty) ?: english()

    /** The Icelandic words, or null when [portion] isn't one Dropi knows. */
    internal fun icelandic(portion: String, qty: Double = 1.0): String? {
        val p = parse(portion) ?: return null
        // Whole numbers of a "1 x" portion read as one amount: "3 sneiðar", not "3 × 1 sneið".
        if (qty != 1.0 && qty % 1.0 == 0.0 && p.count == 1.0) return words(qty, p)
        val base = words(p.count, p) ?: return null
        return if (qty == 1.0) base else "${Meal.qtyText(qty)} × $base"
    }

    /** Just the words for one [portion], e.g. a usual amount. */
    fun shown(portion: String): String = shown(portion, 1.0) { portion }

    private class Parsed(val count: Double, val countText: String, val size: String?, val unit: String)

    private fun parse(portion: String): Parsed? {
        val t = portion.trim().lowercase(Locale.ROOT)
        val sp = t.indexOf(' ')
        if (sp <= 0) return null
        val numText = t.substring(0, sp)
        val count = when (numText) {
            "½" -> 0.5; "¼" -> 0.25; "¾" -> 0.75; "⅓" -> 1.0 / 3; "a", "an", "one" -> 1.0
            else -> numText.replace(',', '.').toDoubleOrNull() ?: return null
        }
        var rest = t.substring(sp + 1).trim()
        var size: String? = null
        val first = rest.substringBefore(' ')
        if (first in sizes && rest.contains(' ')) { size = first; rest = rest.substringAfter(' ').trim() }
        if (rest !in units) {
            // "1 medium" on its own: a medium one of the food.
            if (rest in sizes && size == null) return Parsed(count, numText, rest, "")
            return null
        }
        return Parsed(count, if (count == 1.0 && numText in setOf("a", "an", "one")) "1" else numText, size, rest)
    }

    private fun words(n: Double, p: Parsed): String? {
        val whole = n % 1.0 == 0.0
        // Fractions take the singular ("½ bolli"); whole numbers by the Icelandic rule (1, 21 singular).
        val one = !whole || L.isOne(n.toLong())
        val num = if (n == p.count) p.countText.replace('.', L.decimal) else Meal.qtyText(n)
        if (p.unit.isEmpty()) {
            val s = sizes[p.size] ?: return null
            return "$num ${(s[G.N] ?: return null).let { if (one) it.first else it.second }}"
        }
        val u = units[p.unit] ?: return null
        val noun = if (one) u.one else u.many
        val adj = p.size?.let { sizes[it]?.get(u.g) }?.let { if (one) it.first else it.second }
        return listOfNotNull(num, adj, noun).joinToString(" ")
    }
}
