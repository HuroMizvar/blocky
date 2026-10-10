# Bločky – skener bločkov eKasa a analýza výdavkov (Android)

Naskenujete QR kód z pokladničného dokladu → aplikácia stiahne z portálu Finančnej správy SR
(eKasa) všetky položky a ceny → každú položku zaradí do kategórie → mesačne vyhodnotí výdavky
a navrhne, kde ušetriť.

## Rýchle skenovanie
Aplikácia sa vždy otvorí rovno na skeneri (aj po návrate z pozadia po viac ako 1 minúte).
Po rozpoznaní QR telefón zavibruje a sken sa hneď uloží – aplikáciu môžete okamžite zavrieť.
Položky z eKasa sa načítajú na pozadí (WorkManager, `data/FetchWorker.kt`); bez signálu
alebo pri offline doklade, ktorý ešte nie je v eKasa, to aplikácia skúša automaticky znova.
Čakajúce skeny sú vidieť v záložke **Bločky**.

## Funkcie
| Oblasť | Čo robí |
|---|---|
| Skenovanie | CameraX + ML Kit (offline rozpoznanie QR), baterka, ochrana proti duplicitám |
| Načítanie dokladu | eKasa API – obchod, adresa, dátum, položky, množstvá, ceny, DPH. Online (`O-…`) aj offline (OKP) QR |
| Kategorizácia | 18 kategórií, slovenské kľúčové slová + typ obchodu (pumpa, reštaurácia, lekáreň). Ručná oprava s pamäťou pre rovnaké názvy |
| Mesačný report | Spolu, vs. minulý mesiac, vs. priemer posledných ≤3 mesiacov, lineárna projekcia do konca mesiaca, kategórie, obchody, top položky |
| Odporúčania | Prekročený / ohrozený rozpočet · nárast kategórie >25 % nad priemer · podiel alkoholu/tabaku/sladkostí/reštaurácií nad orientačným stropom · rovnaký produkt lacnejšie v inom obchode · zdražené položky · drobné impulzívne nákupy · často kupované položky · nevrátené zálohy |
| Rozpočty | Mesačný limit na kategóriu |
| Export | CSV (oddeľovač `;`, UTF‑8 – otvorí sa priamo v Exceli) |
| Ručný záznam | Výdavok bez QR (trh, služby) |

Všetky dáta sú len v telefóne (Room/SQLite). Sieť sa používa iba na dotaz do eKasa.

Všetky sumy „úspory“ sú **odhady** a v aplikácii sú tak označené. Orientačné stropy podielov
kategórií sú heuristika aplikácie (`Category.benchmarkShare`), nie štatistické normy – upravte podľa seba.

## Ako získať APK a aktualizácie

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
