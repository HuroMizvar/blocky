# Bločky – skener bločkov eKasa a analýza výdavkov (Android)

Naskenujete QR kód z pokladničného dokladu → aplikácia stiahne z portálu Finančnej správy SR
(eKasa) všetky položky a ceny → každú položku zaradí do kategórie → mesačne vyhodnotí výdavky
a navrhne, kde ušetriť.

## Súkromie
| Čo | Ako |
|---|---|
| Kde sú dáta | Len v telefóne – súkromné úložisko aplikácie (SQLite databáza `blocky.db`). Iné aplikácie k nej nemajú prístup. |
| Cloud / zálohy | Žiadne. `allowBackup=false` + `data_extraction_rules.xml` – nič do Google Drive ani pri prenose na nový telefón. |
| Reklamy, analytika, AI | Žiadne knižnice na reklamy ani analytiku; reklamné ID výslovne odstránené z manifestu; tipy počíta pravidlový kód, nie AI. |
| Kamera | QR číta ZXing priamo v telefóne (bez Google ML Kit / Play služieb). |
| Sieť | Len HTTPS: (1) eKasa Finančnej správy SR – ID dokladu → položky, (2) GitHub – číslo najnovšej verzie (vypínateľné). |
| GDPR | Export (CSV) a „Zmazať všetko“ v Nastaveniach. Dáta spracúva len používateľ na svojom zariadení. |
| AI analýza | Aplikácia sama nič neodosiela. Pripraví textový súhrn (súčty po kategóriách, bez adries/IČO/ID bločkov), používateľ ho skontroluje a sám zdieľa do AI aplikácie podľa výberu (`domain/AiExport.kt`). |

Ikony kategórií: [Phosphor Icons](https://phosphoricons.com) (MIT), písmo: Plus Jakarta Sans (OFL) – licencie v `licenses/`.

## Kategorizácia
1. **Typ prevádzky** (obchod, pumpa, reštaurácia, hotel, wellness, lekáreň…) – podľa známych značiek/kľúčových slov; ak ho aplikácia nepozná, **opýta sa hneď po skene** a zapamätá si odpoveď (podľa IČO).
2. Reštaurácia / hotel / wellness / oblečenie / elektronika / služby → celý bloček do jednej kategórie.
3. Obchody a pumpy → každá položka podľa názvu (mäso, mliečne, alkohol, PHM…).
4. **Zálohy** za fľaše a plechovky sú vždy samostatná skupina a do výdavkov sa nepočítajú.
5. Ručná oprava položky má vždy prednosť.
6. **Zľavy** (záporné riadky) sa pripočítajú k produktu nad nimi; zľava na celý nákup sa rozpočíta pomerne (`domain/Discounts.kt`).
7. **Prečiarknutá položka** (vrátená, reklamovaná, omyl) sa nezapočítava do výdavkov.

## Rýchle skenovanie
Aplikácia sa vždy otvorí rovno na skeneri (aj po návrate z pozadia po viac ako 1 minúte).
Po rozpoznaní QR telefón zavibruje a sken sa hneď uloží – aplikáciu môžete okamžite zavrieť.
Položky z eKasa sa načítajú na pozadí (WorkManager, `data/FetchWorker.kt`); bez signálu
alebo pri offline doklade, ktorý ešte nie je v eKasa, to aplikácia skúša automaticky znova.
Čakajúce skeny sú vidieť v záložke **Bločky**.

## Funkcie
| Oblasť | Čo robí |
|---|---|
| Skenovanie | CameraX + ZXing (offline rozpoznanie QR), baterka, ochrana proti duplicitám |
| E-bločky z e-mailu | *Zdieľať → Bločky* z e-mailu (PDF, obrázok, text s `UID: O-…`) alebo tlačidlo *E-bloček z e-mailu* na skeneri. PDF sa vykreslí v telefóne (`PdfRenderer`) a QR sa prečíta cez ZXing – súbor sa nikam neposiela ani neukladá (`ReceiptImport`) |
| Načítanie dokladu | eKasa API – obchod, adresa, dátum, položky, množstvá, ceny, DPH. Online (`O-…`) aj offline (OKP) QR |
| Kategorizácia | 26 kategórií vrátane *Lahôdky a údeniny* + až 8 vlastných (názov, ikona, farba), slovenské kľúčové slová + typ obchodu (pumpa, reštaurácia, lekáreň). Ručná oprava s pamäťou pre rovnaké názvy |
| Mesačný report | Spolu, vs. minulý mesiac, vs. priemer posledných ≤3 mesiacov, lineárna projekcia do konca mesiaca, kategórie, obchody, top položky |
| Odporúčania | Prekročený / ohrozený rozpočet · nárast kategórie >25 % nad priemer · podiel alkoholu/tabaku/sladkostí/reštaurácií nad orientačným stropom · rovnaký produkt lacnejšie v inom obchode · zdražené položky · drobné impulzívne nákupy · často kupované položky · nevrátené zálohy |
| Rozpočty | Mesačný limit na kategóriu. *Nastaviť podľa môjho míňania* (`BudgetPlanner`): medián celej histórie (alebo nižšie míňanie posledných 3 mesiacov), −15 % zbytné / −10 % ostatné / −5 % nevyhnutné, zaokrúhlené nadol |
| Záloha | Zašifrovaný súbor `.blocky` (heslo → PBKDF2-SHA256 210k → AES-256-GCM): databáza + nastavenia + členstvo v domácnosti. Obnova sa dokončí pri ďalšom štarte (`Backup.applyPendingRestore`). Voliteľne týždenne do zvoleného priečinka (4 posledné) |
| Deň výplaty | Rozpočtový mesiac od zvoleného dňa (`Period`), synchronizuje sa v profile domácnosti |
| Jazyk | Slovenčina / English (Nastavenia hore). Preklad pri zobrazení cez `I18n` + `assets/i18n_en.tsv` (generuje `tools/i18n/gen.py`); názvy položiek z bločkov sa neprekladajú |
| Fixné výdavky | Nastavenia → Domácnosť: hypotéka, úver, leasing, nájom… s dňom platby; kategória `FIXNE` vždy na konci prehľadu, súčet „s fixnými výdavkami“ |
| Správca domácnosti | Zakladateľ = správca (tajomstvo správcu, na serveri len odtlačok). Kľúč správcu `BLOCKY1A:` obnoví správcu na novom telefóne; správca môže odstrániť člena a vymeniť kľúč domácnosti |
| Vyhľadávanie | Bločky podľa obchodu aj položiek, so súčtom nájdených položiek |
| Nastavenia | Záložky: Domácnosť · AI · Aktualizácie · Súkromie a dáta |
| Export | CSV (oddeľovač `;`, UTF‑8 – otvorí sa priamo v Exceli) |
| Ručný záznam | Výdavok bez QR (trh, služby) |

Všetky dáta sú len v telefóne (Room/SQLite). Sieť sa používa iba na dotaz do eKasa.

Všetky sumy „úspory“ sú **odhady** a v aplikácii sú tak označené. Orientačné stropy podielov
kategórií sú heuristika aplikácie (`Category.benchmarkShare`), nie štatistické normy – upravte podľa seba.

## Ako získať APK a aktualizácie

APK z GitHubu je **testovacia verzia** `sk.miro.blocky.test` („Bločky TEST“) – inštaluje sa vedľa verzie z Google Play a má vlastné údaje (preniesť sa dajú zálohou).


### Prvá inštalácia (raz)
1. Vytvorte na GitHube repozitár a nahrajte doň obsah tohto priečinka (vetva `main`).
2. Záložka **Actions** → workflow *Build APK* sa spustí sám (~5–8 min).
3. Záložka **Releases** → najnovší `Build N` → stiahnite `blocky-N.apk` do telefónu a nainštalujte
   (povoľte „Inštalovať z neznámych zdrojov“).
   Ak máte nainštalovanú staršiu verziu z prvého ZIP-u, najprv ju odinštalujte – mala iný podpis.

### Ďalšie verzie (automaticky)
Každý push do `main` vytvorí nový release `bN`. Aplikácia pri spustení skontroluje GitHub a ponúkne
**Stiahnuť a nainštalovať** – žiadne ručné sťahovanie. Ručne: ⚙ Nastavenia → Aktualizácie.
Pri prvej aktualizácii Android jednorazovo vypýta povolenie „Inštalovať neznáme aplikácie“ pre Bločky.

Ako to funguje:
| Časť | Kde |
|---|---|
| Číslo verzie = číslo behu GitHub Actions | `app/build.gradle.kts` (`GITHUB_RUN_NUMBER`) |
| Rovnaký podpis všetkých buildov (inak Android aktualizáciu odmietne) | `app/keystore/test.keystore` – len testovací kľúč, nie pre Google Play |
| Release + čistenie (ponechá posledných 10) | `.github/workflows/build.yml` |
| Kontrola, sťahovanie, inštalácia | `data/Updater.kt` |
| Poznámky k verzii | text commitu sa zobrazí v dialógu aktualizácie |

### Súkromný repozitár
Release súkromného repozitára sa bez prihlásenia stiahnuť nedá, preto treba token zabudovaný do APK:
1. GitHub → Settings → Developer settings → **Fine-grained tokens** → Generate:
   *Repository access*: len tento repozitár, *Permissions → Contents*: **Read-only**.
2. Repozitár → Settings → Secrets and variables → Actions → **New repository secret**
   `UPDATE_TOKEN` = token.
3. Pushnite ľubovoľnú zmenu – nový build už token obsahuje.

Token je v APK čitateľný, preto len read-only a len na tento repozitár; APK nezdieľajte.
Pri verejnom repozitári sa nič nenastavuje.

### Android Studio (voliteľné)
*File → Open* → priečinok → **Run ▶** nainštaluje build priamo cez USB. Lokálny build má verziu 1,
takže ak je v telefóne vyššia verzia z GitHubu, Android Studio ponúkne jej odinštalovanie.

Požiadavky: Android 8.0+ (API 26), JDK 17.

## Testovací režim (⚙ Nastavenia)
| Funkcia | Načo |
|---|---|
| **Nahrať demo dáta** | ~80–110 realistických bločkov za aktuálny + 3 predchádzajúce mesiace (Lidl, Billa, Tesco, Kaufland, pumpa, McDonald's, lekáreň, dm). Navrhnuté tak, aby vyvolali všetky typy odporúčaní. Označené „· demo“ |
| **Zmazať demo** | Odstráni len demo bločky, vaše skutočné ostanú |
| **Simulovaný eKasa server** | Skener prijme akýkoľvek QR (aj z obalu výrobku) a vytvorí náhodný nákup – test kamery a toku bez skutočných bločkov |
| **Obsah QR (bez kamery)** | Vložíte text QR kódu – test parsera a eKasa dotazu |
| **JSON odpoveď eKasa** | Vložíte surovú odpoveď z eKasa – ladenie parsera, keď niektorý bloček nevyjde |

Logika (kategorizácia, reporty, odporúčania, demo dáta) má unit testy v `app/src/test`; spúšťajú sa
pri každom builde a pri chybe sa release nevytvorí.

## Štruktúra
```
app/src/main/java/sk/miro/blocky/
  data/EkasaClient.kt     QR parser + volanie eKasa API + parsovanie JSON
  data/Db.kt              Room: bločky, položky, rozpočty, pravidlá kategórií
  data/Repository.kt      ukladanie, kategorizácia pri uložení, CSV export, demo/import
  data/Updater.kt         aktualizácie z GitHub Releases
  data/DemoData.kt        testovacie dáta + simulovaný eKasa server
  domain/Categorizer.kt   kategórie a kľúčové slová  ← sem dopĺňajte vlastné výrazy
  domain/Analytics.kt     mesačný report + pravidlá odporúčaní ← prahy sa ladia tu
  ui/…                    Compose obrazovky (Prehľad, Odporúčania, Bločky, Rozpočty, Skener, Nastavenia)
app/src/test/…            unit testy logiky (bežia aj v CI)
```

## Známe obmedzenia
- eKasa endpoint (`https://ekasa.financnasprava.sk/mdu/api/v1/opd/receipt/find`) nie je oficiálne
  zdokumentované verejné API – používa ho web „Overenie dokladu“. Ak ho Finančná správa zmení, URL sa
  dá prepísať v **⚙ Nastavenia → eKasa API**.
- Offline doklady sa v eKasa objavia až po odoslaní pokladnicou (až 48 h) – skúste neskôr.
- Porovnanie cien medzi obchodmi funguje len pre rovnako pomenované položky (obchody používajú rôzne názvy).
- Kategorizácia je kľúčovými slovami – prvé týždne opravte nesprávne položky, aplikácia si to zapamätá.

## Úprava bločku
- Ťuknutím na položku v bločku: zmena kategórie, **počtu kusov** (napr. na bločku 2 kávy, platili ste 1 – cena aj zľava sa prepočítajú), prečiarknutie, obnovenie podľa bločku.
- Upravené bločky a položky sú jemne podfarbené a majú štítok „upravený“.

## Kategória Auto a moto
- Autoservisy, pneuservisy, STK, autodiely a umyvárne sa celé zaradia do „Auto a moto“.
- Autopotreby (motorový olej, ostrekovače, diaľničná známka…) sa rozpoznajú v ktoromkoľvek obchode.

## Spoločná domácnosť (voliteľné)
- Až 10 členov (telefóny, tablet) nahráva bločky do spoločných výdavkov; pri bločku vidno, kto ho nahral.
- Duplicita: rovnaký bloček má u všetkých členov rovnaké (zašifrované) ID → upozornenie „Tento bloček už nahral(a) …“.
- Šifrovanie AES-256-GCM v telefóne; server (Firebase Firestore, EÚ) dostane len zašifrovaný text. Kľúč je len v pozývacom QR kóde.
- Bez Google účtu – každé zariadenie má anonymný prístup Firebase. Bez Firebase SDK (len REST), bez analytiky.
- Synchronizuje sa: bločky s položkami a úpravami, zmazania, pravidlá kategórií, typy prevádzok, rozpočty, profil domácnosti.

### Nastavenie servera (raz, ~15 minút)
1. https://console.firebase.google.com → **Vytvoriť projekt** (napr. `blocky-domacnost`), Google Analytics **vypnúť**.
2. **Authentication** → Začať → Sign-in method → **Anonymous** → Zapnúť → Uložiť.
3. **Firestore Database** → Vytvoriť databázu → umiestnenie **eur3 (Europe)** → production mode.
4. Firestore → **Rules** → vložiť obsah súboru `firestore.rules` → Publish.
5. Nastavenia projektu (ozubené koliesko) → Vaše aplikácie → **Android** → balík `sk.miro.blocky`,
   SHA-1 `B0:13:28:72:53:79:E5:7B:6F:F2:3C:4D:D0:1E:A4:B5:03:43:33:B3` → Registrovať → stiahnuť **google-services.json**.
6. Súbor `google-services.json` nahrať do koreňa repozitára na GitHube (Add file → Upload files).

## Zaraďovanie pomocou AI (voliteľné, Nastavenia → Zaraďovanie položiek)
- **Vstavaná AI** (predvolene zapnutá): Naive Bayes nad časťami slov, beží len v telefóne, nič neodosiela.
  Rozumie skratkám z bločkov a učí sa z opráv používateľa (v domácnosti sa opravy zdieľajú).
  Model: `app/src/main/assets/item_model.tsv`, tréning: `tools/ai/` (`python3 export.py <model> <expected>`).
- Poradie: ručné pravidlo → záloha → typ prevádzky → zľava → **AI** → kľúčové slová → náhradná kategória.
- **AI telefónu (Gemini Nano cez ML Kit)**: len na podporovaných telefónoch, len po zapnutí používateľom
  (ML Kit posiela Googlu diagnostiku – nie text položiek). Ak je zapnutá, má prednosť pred vstavanou AI.
- Merania (tools/ai): skutočné položky používateľa 30/31 (pravidlá samotné 8/31); úplne neznáme produkty ~55 % správne,
  ~30 % ostane nezaradených (radšej „Iné“ ako zlá kategória).
