# -*- coding: utf-8 -*-
# Slovník produktov podľa kategórií (slovenské bločky). Základ pre generovanie trénovacích dát.
# Každá položka: základný názov produktu. Generátor pridá značky, veľkosti, skratky a štýly obchodov.

V = {}

V["MASO"] = """kuracie prsia|kuracie prsné rezne|kuracie stehná|kuracie horné stehná|kuracie krídla|kurča celé|kuracie mäso mleté
|kuracie srdcia|kuracie pečienky|kuracie žalúdky|morčacie prsia|morčacie stehno|morčacie mäso mleté|bravčové karé
|bravčová krkovička|bravčové stehno|bravčové plece|bravčový bôčik|bravčové rebierka|bravčové mäso mleté|bravčová panenka
|bravčová pečeň|hovädzie zadné|hovädzie predné|hovädzia roštenka|hovädzí stejk|hovädzie mäso mleté|mleté mäso mix
|teľacie mäso|jahňacie mäso|šunka výberová|šunka dusená|šunka od kosti|šunka pražská|šunka kuracia|šunka morčacia
|šunka bravčová|saláma inovecká|saláma vysočina|saláma paprikáš|saláma pikantná|saláma gombasecká|saláma tradičná
|saláma čajová|saláma herkules|saláma poličan|saláma junior|saláma pápriková|salám milánsky|chorizo|pepperoni
|klobása domáca|klobása údená|klobása liptovská|klobása čabajská|klobása grilovacia|klobása turistická|klobáska
|párky jemné|párky bravčové|párky kuracie|párky debrecínske|viedenské párky|párok v črievku|špekačky|špekáčiky
|slanina údená|slanina anglická|slanina plátky|oravská slanina|bôčik údený|údené mäso|údené koleno|údené rebierka
|pečeňová paštéta|paštéta|tlačenka|jaternice|krvavničky|hurka|tlačenka hovädzia|mortadela|kabanos|debrecínka
|prosciutto|pancetta|bresaola|losos filet|losos údený|treska filet|pstruh|kapor|makrela údená|tuniak steak
|krevety|filé z aljašskej tresky|ryba filé|mušle|kalamáre|sleď|sardinky v oleji|tuniak v konzerve|hot dog párky
|fašírka|karbonátky|čevapčiči|bravčové rezne|kuracie rezne|tatárske mäso|kačica|kačacie stehná|husacie stehná|králik""".replace("\n", "").split("|")

V["MLIECNE"] = """mlieko polotučné|mlieko plnotučné|mlieko trvanlivé|mlieko čerstvé|mlieko bezlaktózové|mlieko odstredené
|jogurt biely|jogurt jahodový|jogurt čučoriedkový|jogurt grécky|jogurt smotanový|jogurt bifidus|jogurtový nápoj|zákys
|kefír|acidko|cmar|smotana na šľahanie|smotana na varenie|kyslá smotana|šľahačka sprej|maslo|maslo čerstvé|maslo 82%
|rastlinné maslo|margarín|nátierka|syr eidam|syr gouda|syr ementál|syr čedar|syr mozzarella|mozzarella|syr niva|niva
|bryndza|tvaroh hrudkovitý|tvaroh jemný|tvaroh mäkký|tvarohový dezert|cottage cheese|syr parmezán|parmezán|syr feta
|balkánsky syr|syr hermelín|hermelín|camembert|brie|syr údený|parenica|korbáčiky|oštiepok|syrové nite|syr tavený
|tavený syr trojuholníky|syr plátky|syr strúhaný|syr ricotta|mascarpone|lučina|žervé|termix|puding|ryžový nákyp
|vajcia|vajcia M|vajcia L|vajcia z voľného výbehu|vaječný bielok|skyr|kefírové mlieko|šľahačka|mliečna ryža|zmrzlinový jogurt
|kyslé mlieko|ovsený nápoj|sójový nápoj|mandľový nápoj|jogobella|activia|danone|rajo|tami|kunín|zvolenský""".replace("\n", "").split("|")

V["PECIVO"] = """rožok|rožok biely|rožok grahamový|rožok cereálny|rožky|chlieb|chlieb konzumný|chlieb kváskový|chlieb ražný
|chlieb celozrnný|chlieb toastový|toastový chlieb|chlieb krájaný|chlieb slnečnicový|bageta|bageta francúzska|bagetka
|kaiserka|žemľa|bulka|hamburgerová žemľa|hot dog rožok|vianočka|pletenka|záviny|croissant|croissant maslový
|croissant s nugátom|šatôčka|koláč|koláč tvarohový|koláč makový|buchta|buchty s lekvárom|pagáč|pagáče oškvarkové
|langoš|tortilla|pita|ciabatta|focaccia|knäckebrot|chlebíček|sendvič|bábovka|štrúdľa jablková|donut|šiška
|osie hniezdo|kremeš|veterník|laskonky|bratislavský rožok|loupák|slané tyčinky pekáreň|pizza rožok|syrový rožok
|cereálna bageta|tmavé pečivo|pečivo mix|sladké pečivo|bagel|lievance|palacinky|wafle|piškóty|strúhanka""".replace("\n", "").split("|")

V["OVOCIE_ZELENINA"] = """jablká|jablká červené|jablká golden|hrušky|banány|pomaranče|mandarínky|klementínky|citróny|limetky
|grapefruit|hrozno biele|hrozno tmavé|jahody|maliny|čučoriedky|černice|ríbezle|slivky|broskyne|nektárinky|marhule
|kiwi|ananás|mango|avokádo|melón vodový|melón cukrový|granátové jablko|figy|datle|hruška|zemiaky|zemiaky nové
|zemiaky skoré|batáty|cibuľa|cibuľa červená|cibuľa jarná|cesnak|mrkva|petržlen|zeler|paradajky|cherry paradajky
|rajčiny|uhorky|uhorka šalátová|paprika červená|paprika žltá|paprika zelená|kapia|šalát ľadový|šalát hlávkový
|rukola|baby špenát|špenát|kapusta biela|kapusta červená|kel|karfiol|brokolica|cuketa|baklažán|tekvica hokkaido
|redkvička|reďkovka|kaleráb|hríby|šampiňóny|huby|pór|kukurica|fazuľka zelená|hrášok|pažítka|petržlenová vňať
|bazalka|mäta|bylinky|reďkev|cvikla|červená repa|chren|zázvor|chilli papričky|ovocie mix|zelenina mix|polievková zelenina
|olivy|sušené slivky|hrozienka|orechy vlašské|lieskové orechy|mandle""".replace("\n", "").split("|")

V["TRVANLIVE"] = """múka hladká|múka polohrubá|múka hrubá|múka celozrnná|cukor kryštálový|cukor práškový|cukor trstinový
|soľ|soľ jódovaná|ryža|ryža basmati|ryža jazmínová|ryža guľatozrnná|cestoviny|špagety|penne|fusilli|kolienka
|vretená|tagliatelle|lasagne pláty|rezance|halušky sušené|slnečnicový olej|olivový olej|repkový olej|ocot
|balzamikový ocot|kečup|horčica|majonéza|tatárska omáčka|paradajkový pretlak|pasírované paradajky|konzerva fazuľa
|fazuľa biela|šošovica|cícer|hrach lúpaný|müsli|ovsené vločky|cornflakes|kukuričné lupienky|med|džem jahodový
|lekvár|nutella|nugátová nátierka|arašidové maslo|korenie čierne|mletá paprika|rasca|majorán|oregano|bujón
|kocka bujón|droždie|prášok do pečiva|vanilkový cukor|kakao|horká čokoláda na varenie|kokos strúhaný|omáčka sójová
|omáčka na cestoviny|pesto|instantná polievka sáčok|polievka vrecúško|kuskus|bulgur|pohánka|jačmenné krúpy|puding prášok
|želatína|strúhanka|kompót|broskyne v sirupe|ananás kompót|kyslé uhorky|sterilizovaná paprika|kapusta kyslá|kukurica konzerva
|hrášok konzerva|olivy v náleve|kapary|chia semienka|ľanové semienko|slnečnicové semienka|tekvicové semienka|škrob
|ovsená kaša|ryžové chlebíčky|trvanlivé pečivo|sucháre|knedľa trvanlivá""".replace("\n", "").split("|")

V["MRAZENE_HOTOVE"] = """pizza mrazená|pizza šunková|pizza margherita|pizza salami|pizza quattro formaggi|lasagne|hranolky
|hranolky mrazené|krokety|bryndzové halušky|pirohy|pirohy s bryndzou|knedle|knedľa kysnutá|zemiakové knedle|gnocchi
|zeleninová zmes mrazená|mrazený hrášok|mrazená brokolica|mrazený špenát|rybie prsty|kuracie nugety|kuracie stripsy
|hotové jedlo|polievka|polievka hotová|vývar|guláš konzerva|sviečková hotová|rizoto hotové|šalát hotový|cestovinový šalát
|vlašský šalát|zemiakový šalát|sushi|wrap|sendvič hotový|bageta plnená|tortellini|ravioli|plnené cestoviny|hamburger mrazený
|cesto lístkové|cesto na pizzu|palacinky mrazené|knedle s ovocím|zmrazené ovocie|mrazené maliny|instantné rezance
|hotdog|burrito|quiche|tvarohové knedle|kuracie krídla pečené|pečené kura|grilované kura|bagel sendvič""".replace("\n", "").split("|")

V["NAPOJE"] = """minerálna voda|minerálka|voda neperlivá|voda perlivá|voda jemne perlivá|pramenitá voda|dojčenská voda
|kofola|coca cola|coca cola zero|pepsi|fanta|sprite|mirinda|7up|tonic|limonáda|ochutená voda|džús pomarančový
|džús jablkový|džús multivitamín|nektár|100% šťava|smoothie|sirup malinový|sirup bazový|ľadový čaj|ice tea
|energetický nápoj|red bull|monster|semtex|big shock|káva zrnková|káva mletá|káva instantná|kapsule kávové|kávové kapsuly
|cappuccino instantné|čaj čierny|čaj zelený|čaj ovocný|čaj bylinkový|čaj mätový|kakaový nápoj|rajec|budiš|mattoni
|salvator|korytnica|fatra|kláštorná|vinea|relax|hello|rauch|cappy|toma|jacobs|tchibo|nescafe|lavazza|dallmayr
|isostar|iontový nápoj|kombucha|kvas|nealko pivo|nealkoholické pivo""".replace("\n", "").split("|")

V["ALKOHOL"] = """pivo|pivo svetlé|pivo výčapné|pivo ležiak|pivo tmavé|pivo plechovka|pivo fľaša|radler|cider|víno biele
|víno červené|víno ružové|víno suché|víno polosladké|víno sladké|sekt|prosecco|šampanské|frizzante|vodka|borovička
|slivovica|hruškovica|marhuľovica|rum|tuzemák|whisky|whiskey|bourbon|gin|likér|jägermeister|becherovka|fernet
|tatratea|demänovka|griotka|tequila|koňak|brandy|vermút|martini|aperol|porto|medovina|pálenka|stock|absinth
|zlatý bažant|šariš|smädný mních|kelt|corgon|pilsner urquell|kozel|staropramen|budweiser|heineken|gambrinus
|velkopopovický|krušovice|birell|tokaj|frankovka|veltlín|rizling|müller thurgau|cabernet|merlot|chardonnay|sauvignon
|svätovavrinecké|irsai oliver|tramín|dunaj""".replace("\n", "").split("|")

V["TABAK"] = """cigarety|marlboro red|marlboro gold|camel blue|L&M red|LM blue|west red|petra|chesterfield|winston
|pall mall|lucky strike|davidoff|philip morris|parliament|heets|terea|iqos|glo|neo sticks|tabak|tabak na cigarety
|cigaretové papieriky|filtre do cigariet|zapaľovač|e-cigareta|náplň do e-cigarety|vape|elfbar|cigara|nikotínové vrecúška
|velo|zyn""".replace("\n", "").split("|")

V["SLADKOSTI"] = """čokoláda mliečna|čokoláda horká|čokoláda s orieškami|milka|kinder|kinder čokoláda|kinder bueno|kinder vajíčko
|orion|figaro|horalky|tatranka|deli|fidorka|študentská pečať|lentilky|m&m|mars|snickers|twix|bounty|kitkat
|milky way|toffifee|raffaello|ferrero rocher|merci|bonboniéra|pralinky|keksy|sušienky|oblátky|piškóty kakaové
|perník|medovník|chipsy|čipsy|pringles|lays|doritos|bake rolls|krekry|slané tyčinky|pukance|arašidy solené
|kešu solené|chrumky|zemiakové lupienky|cukríky|gumené cukríky|haribo|žuvačky|orbit|mentos|lízanka|marshmallow
|zmrzlina|nanuk|zmrzlina vanilková|zmrzlina čokoládová|magnum|carte d'or|algida|dezert|torta|tortička|muffin
|cereálna tyčinka|proteínová tyčinka|müsli tyčinka|marcipán|nugát|halva|domino|mila|sedita|opavia|bebe
|lina|disko|kakaové rezy|banánky v čokoláde|kokosky""".replace("\n", "").split("|")

V["DROGERIA"] = """šampón|šampón na vlasy|kondicionér|balzam na vlasy|maska na vlasy|lak na vlasy|gél na vlasy|farba na vlasy
|mydlo|tekuté mydlo|sprchový gél|pena do kúpeľa|zubná pasta|zubná kefka|ústna voda|zubná niť|dezodorant|antiperspirant
|holiaci strojček|žiletky|pena na holenie|gél na holenie|voda po holení|krém na ruky|krém na tvár|pleťový krém
|telové mlieko|opaľovací krém|odličovač|odličovacie tampóny|vatové tyčinky|vata|plienky|plienky pampers|vlhčené obrúsky
|detské obrúsky|tampóny|vložky|intímne vložky|intímny gél|parfum|toaletná voda|kozmetika|make up|rúž|maskara|lak na nechty
|odlakovač|nivea|dove|colgate|signal|elmex|sensodyne|oral b|gillette|rexona|garnier|loreal|palmolive|fa|axe|old spice
|head shoulders|schauma|syoss|elseve|balea|isana|kondómy|náplasti kozmetické|hrebeň|gumičky do vlasov""".replace("\n", "").split("|")

V["DOMACNOST"] = """toaletný papier|papierové utierky|kuchynské utierky|servítky|vreckovky|papierové vreckovky|prací prášok
|prací gél|kapsule na pranie|aviváž|odstraňovač škvŕn|jar|pur|saponát|prostriedok na riad|tablety do umývačky
|finish|somat|soľ do umývačky|leštidlo do umývačky|čistiaci prostriedok|čistič na okná|čistič kúpeľne|domestos
|savo|bref|wc gél|wc blok|osviežovač vzduchu|vrecia na odpad|vrecká na odpad|alobal|potravinová fólia|papier na pečenie
|vrecká na zmrazovanie|batérie|batérie AA|batérie AAA|žiarovka|led žiarovka|sviečka|čajové sviečky|zápalky|špongia
|hubka na riad|drôtenka|handra|mop|metla|rukavice gumové|igelitová taška|taška|nákupná taška|vešiaky|kolíky na prádlo
|pohár|tanier|hrnček|príbor|panvica|hrniec|dóza|uzatváracie vrecká|kvetináč|zemina|hnojivo|semená|osivo|ariel|persil
|lenor|vanish|cif|ajax|mr proper|calgon|perwoll|silan|savo wc|tesa|lepiaca páska|lepidlo|nožnice|pero|zošit|papier a4""".replace("\n", "").split("|")

V["ZDRAVIE"] = """paralen|ibalgin|nurofen|brufen|aspirin|acylpyrin|panadol|coldrex|fervex|olynth|nasivin|sinecod|stoptussin
|strepsils|orofar|septolete|acc long|mucosolvan|vitamín c|vitamín d|vitamín b komplex|multivitamín|magnézium
|horčík|zinok|železo|omega 3|probiotiká|enterol|smecta|hylak|endiaron|lopedium|ranitidín|anacid|rennie|espumisan
|bepanthen|fenistil|panthenol|dezinfekcia|náplasť|obväz|gáza|teplomer|tlakomer|test tehotenský|antigénový test
|kvapky do očí|kvapky do nosa|sirup proti kašľu|pastilky|tablety|kapsuly|masť|gél na kĺby|voltaren|diclofenac
|lieky|doplatok za lieky|recept|kontaktné šošovky|roztok na šošovky|kompresné pančuchy|ortéza|inhalátor""".replace("\n", "").split("|")

V["ZVIERATA"] = """krmivo pre psov|krmivo pre mačky|granule pre psov|granule pre mačky|kapsička pre mačky|konzerva pre psov
|pedigree|whiskas|felix|friskies|kitekat|purina|royal canin|brit|gourmet|sheba|dreamies|pamlsky pre psov
|maškrty pre psov|žuvacia kosť|stelivo pre mačky|podstielka|seno pre králiky|krmivo pre vtáky|zrno pre vtáky|krmivo pre rybičky
|obojok|vôdzka|hračka pre psa|šampón pre psov|antiparazitárny obojok|krmivo pre hlodavce""".replace("\n", "").split("|")

V["RESTAURACIE"] = """obedové menu|denné menu|menu|polievka dňa|hlavné jedlo|vyprážaný syr|vyprážaný rezeň|bryndzové halušky porcia
|pizza porcia|burger|hamburger menu|cheeseburger|big mac|mcmenu|happy meal|hranolky porcia|kebab|gyros|langoš porcia
|espresso|cappuccino|latte|latte macchiato|flat white|americano|káva so sebou|čaj porcia|limonáda domáca|pivo 0,5l čapované
|točené pivo|kofola čapovaná|dezert dňa|palacinka|zmrzlina kopček|obsluha|prepitné|servis|balné|obal so sebou|tringelt
|šalát caesar|risotto|steak|grilované kuracie prsia|rezeň s prílohou|príloha|ryža príloha|hranolky príloha|tatárska omáčka porcia
|polievka|vývar s rezancami|cesnačka|kapustnica|segedínsky guláš|guláš|sviečková|vyprážaný karfiol|chicken wings
|nachos|tortilla wrap|bageta zapekaná|toast šunka syr|panini|croissant s kávou|raňajky|brunch|degustačné menu""".replace("\n", "").split("|")

V["UBYTOVANIE"] = """ubytovanie|nocľah|izba dvojlôžková|izba jednolôžková|apartmán|pobyt|pobyt s polpenziou|polpenzia|plná penzia
|raňajky v cene|miestny poplatok|daň z ubytovania|rekreačný poplatok|kúpeľný poplatok|prístelka|parkovanie hotel
|wellness balík|víkendový pobyt|záloha za pobyt|chata|penzión""".replace("\n", "").split("|")

V["VOLNY_CAS"] = """vstupenka|vstupné|lístok do kina|kino|divadlo|koncert|wellness|sauna|bazén|aquapark|fitness
|permanentka fitness|jednorazový vstup|skipas|lyžiarsky lístok|lanovka|bowling|biliard|squash|tenis kurt|masáž
|kúpalisko|zoo|múzeum|galéria|výstava|escape room|laser game|minigolf|požičovňa bicyklov|plaváreň|solárium
|kurz|tanečný kurz|jazdenie na koni|paintball|kolotoč|zábavný park""".replace("\n", "").split("|")

V["OBLECENIE"] = """tričko|tričko pánske|tričko dámske|košeľa|blúzka|sveter|mikina|bunda|kabát|vesta|nohavice|rifle|džínsy
|tepláky|legíny|sukňa|šaty|šortky|kraťasy|ponožky|pančuchy|pančucháče|spodná bielizeň|boxerky|trenky|podprsenka
|nohavičky|pyžamo|plavky|čiapka|šál|rukavice|opasok|topánky|tenisky|čižmy|sandále|papuče|obuv|kabelka|batoh
|peňaženka|detské oblečenie|body|overal|termoprádlo""".replace("\n", "").split("|")

V["ELEKTRONIKA"] = """nabíjačka|usb kábel|kábel usb c|slúchadlá|bezdrôtové slúchadlá|powerbanka|myš|klávesnica|pamäťová karta
|usb kľúč|flash disk|reproduktor|bluetooth reproduktor|ochranné sklo|kryt na mobil|puzdro na telefón|smart hodinky
|mobilný telefón|tablet|notebook|monitor|televízor|tlačiareň|toner|cartridge|router|predlžovačka|rozbočovač|adaptér
|varná kanvica|mixér|fén|žehlička|vysávač|kávovar|hriankovač|mikrovlnka|elektrický zubná kefka|holiaci strojček elektrický
|baterka led|žiarovka smart|kamera|dobíjacia karta|kredit|predplatená karta|sim karta""".replace("\n", "").split("|")

V["SLUZBY"] = """strih|strihanie|pánsky strih|dámsky strih|farbenie vlasov|melír|holenie|manikúra|pedikúra|kozmetické ošetrenie
|čistenie odevov|chemické čistenie|oprava obuvi|oprava|servis|poštovné|doporučený list|balík|poštová známka|kopírovanie
|tlač|laminovanie|kľúče výroba|parkovné|parkovanie|parkovací lístok|cestovný lístok|lístok mhd|električenka|taxi
|jazda taxi|bolt jazda|vlak lístok|autobus lístok|poplatok|správny poplatok|kolok|fotografie na doklady|čistiareň|práčovňa
|požičovné|nájom|členský poplatok""".replace("\n", "").split("|")

V["PHM"] = """natural 95|natural 98|benzín|nafta|diesel|motorová nafta|lpg|cng|adblue|verva 98|verva diesel|maxx motion 95
|maxx motion diesel|v-power|v-power diesel|efix|efix diesel|ba 95|ba 98|palivo|tankovanie|pohonné hmoty|nafta prémium
|super 95|super plus 98|evolution diesel|dynamic diesel""".replace("\n", "").split("|")

V["AUTO_MOTO"] = """motorový olej|olej 5w30|olej 5w40|olej 10w40|olej 0w20|kvapalina do ostrekovačov|zimná zmes do ostrekovačov
|letná zmes do ostrekovačov|nemrznúca zmes|antifríz|chladiaca kvapalina|brzdová kvapalina|stierače|stieracie lišty
|diaľničná známka|e-známka|autokozmetika|autošampón|vosk na auto|osviežovač do auta|vôňavka do auta|autobatéria
|autožiarovka|žiarovka h7|žiarovka h4|pneumatiky|prezutie pneumatík|výmena oleja|servis auta|stk|emisná kontrola
|umývanie auta|autoumyváreň|program umývania|vysávač samoobslužný|škrabka na ľad|odmrazovač skiel|rozmrazovač
|tažné lano|lekárnička do auta|výstražný trojuholník|reflexná vesta|olejový filter|vzduchový filter|peľový filter
|brzdové platničky|prilba|motocyklový olej|reťaz na motocykel|snehové reťaze|autokoberce|nabíjačka do auta""".replace("\n", "").split("|")

for k in V:
    V[k] = [x.strip() for x in V[k] if x.strip()]

# Privátne značky a výrobcovia, ktoré sa na bločkoch objavujú pred názvom (bez vplyvu na kategóriu).
STORE_BRANDS = ["K.", "MP.", "K-CLASSIC", "K-BIO", "K-FREE", "PILOS", "MILBONA", "CHEF SELECT", "TESCO", "TESCO FINEST", "BILLA",
                "CLEVER", "S BUDGET", "DOBRE Z", "BILLA BIO", "JA! NATURLICH", "COOP", "COOP JEDNOTA", "PENNY",
                "BONI", "BELBAKE", "PIKOK", "SOLEVITA", "CIEN", "W5", "LUPILU", "SNACK DAY", "FIN CARRE", "MCENNEDY",
                "FRESHONA", "CRIVIT", "SILVERCREST", "PARKSIDE", "COMBINO", "K-PURLAND", "BEVOLA", "ALESTO",
                "GOLDEN SEAFOOD", "VITASIA", "TAMARIS", "LIDL", "KAUFLAND", "DM", "BALEA"]

UNITS = {
    "MASO": ["500g", "1kg", "250g", "100g", "300g", "0,5kg", "cca 1kg", "kg", "200g", "150g", "400g", "váž."],
    "MLIECNE": ["1l", "1,5%", "3,5%", "0,5l", "150g", "250g", "125g", "400g", "200ml", "10ks", "330g", "100g", "82%"],
    "PECIVO": ["40g", "60g", "500g", "1kg", "ks", "250g", "90g", "750g", "80g"],
    "OVOCIE_ZELENINA": ["kg", "1kg", "500g", "250g", "ks", "bal.", "125g", "2kg", "váž.", "sieťka"],
    "TRVANLIVE": ["1kg", "500g", "1l", "250g", "400g", "750ml", "0,5kg", "100g", "50g", "20g", "380g", "1,5kg"],
    "MRAZENE_HOTOVE": ["400g", "350g", "750g", "1kg", "300g", "450g", "2,5kg", "500g"],
    "NAPOJE": ["1,5l", "0,5l", "2l", "0,33l", "1l", "0,25l", "6x1,5l", "500g", "250g", "100g", "PET", "plech."],
    "ALKOHOL": ["0,5l", "0,75l", "0,7l", "1l", "0,33l", "0,2l", "plech.", "fľ.", "40%", "12%", "10°", "12°", "6x0,5l"],
    "TABAK": ["20ks", "krab.", "1ks", "40g", "30g", ""],
    "SLADKOSTI": ["100g", "80g", "50g", "150g", "200g", "30g", "45g", "1l", "120ml", "90g", "250g"],
    "DROGERIA": ["250ml", "400ml", "75ml", "500ml", "150ml", "50ml", "4ks", "ks", "100ks", "10ks", "200ml"],
    "DOMACNOST": ["8 rolí", "16ks", "2ks", "1l", "500ml", "40 dávok", "60ks", "35l", "60l", "4ks", "10ks", "1ks", "20m"],
    "ZDRAVIE": ["500mg", "400mg", "20tbl", "10tbl", "30tbl", "60cps", "100ml", "10ml", "tbl.", "cps.", "1ks", "20ks"],
    "ZVIERATA": ["85g", "100g", "400g", "1kg", "3kg", "10kg", "4x85g", "12x100g", "5l", "ks"],
    "RESTAURACIE": ["", "1ks", "porcia", "0,5l", "0,3l", "300g", "150g", "0,2l", "1x"],
    "UBYTOVANIE": ["", "1 noc", "2 noci", "1 osoba", "2 osoby", "os./noc"],
    "VOLNY_CAS": ["", "1 osoba", "dospelý", "dieťa", "1h", "2h", "celodenný"],
    "OBLECENIE": ["", "M", "L", "XL", "S", "veľ. 42", "veľ. 38", "2ks", "3pack"],
    "ELEKTRONIKA": ["", "1m", "2m", "1ks", "64GB", "128GB", "20W", "4ks"],
    "SLUZBY": ["", "1x", "1ks", "1h", "60min"],
    "PHM": ["", "l", "litre", "40,12 l", "35,50 l"],
    "AUTO_MOTO": ["", "1l", "4l", "5l", "3l", "1ks", "2ks", "365 dní", "10 dní", "30 dní"],
}

from vocab2 import X as _X
for _k, _v in _X.items():
    V[_k] = list(dict.fromkeys(V[_k] + _v))
