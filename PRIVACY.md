# Zásady ochrany osobných údajov – aplikácia Bločky

*Platné od 15. 10. 2026*

Bločky je aplikácia na skenovanie pokladničných bločkov eKasa a prehľad osobných výdavkov.
Aplikácia je navrhnutá tak, aby vaše údaje zostali súkromné. Nie je oficiálnou aplikáciou Finančnej správy SR.

## 1. Aké údaje aplikácia spracúva a kde sú uložené

| Údaje | Kde sú uložené | Kto k nim má prístup |
|---|---|---|
| Bločky, položky, ceny, obchody, kategórie, rozpočty, profil domácnosti | len v súkromnom úložisku aplikácie vo vašom telefóne | len vy |
| Obraz z kamery pri skenovaní | nikam sa neukladá ani neodosiela; QR kód sa číta priamo v telefóne | nikto |
| E-bločky (PDF, obrázky) zdieľané z e-mailu | QR kód sa prečíta priamo v telefóne, súbor sa po prečítaní zahodí a nikam sa neodosiela | nikto |

Aplikácia **nepoužíva reklamy, analytiku ani reklamné identifikátory**, údaje **nepredáva** a **nepoužíva ich na trénovanie umelej inteligencie**.
Zálohovanie do cloudu Android je pre aplikáciu vypnuté.

## 2. Kedy aplikácia komunikuje so serverom

1. **Načítanie bločku z eKasa** – po naskenovaní QR kódu sa identifikátor dokladu odošle do verejnej služby
   Finančnej správy SR (ekasa.financnasprava.sk), ktorá vráti položky bločku. Iné údaje sa neposielajú.
2. **Spoločná domácnosť (voliteľné)** – ak ju zapnete, bločky a nastavenia sa synchronizujú medzi zariadeniami
   členov domácnosti cez službu Google Firebase (Cloud Firestore, umiestnenie EÚ).
   - Údaje sa **šifrujú priamo v telefóne** (AES-256-GCM). Server dostane len zašifrovaný text,
     kľúč majú iba členovia domácnosti (v pozývacom QR kóde). Google ani autor aplikácie obsah neprečítajú.
   - Na prístup sa použije **anonymný identifikátor** Firebase – bez mena, e-mailu či Google účtu.
     Meno, ktoré zadáte pre domácnosť, je tiež zašifrované.
3. **AI telefónu (voliteľné)** – na podporovaných telefónoch môžete zapnúť Google Gemini Nano (ML Kit).
   Názvy položiek sa spracujú v telefóne; Google ML Kit však posiela Googlu diagnostické údaje
   (model telefónu, verzia aplikácie, veľkosť vstupu, chybové kódy). Bez zapnutia sa ML Kit nepoužíva.
   Vstavaná AI aplikácie beží úplne v telefóne a nič neodosiela.
4. **Zdieľanie na AI analýzu (voliteľné)** – text so súhrnom výdavkov sa odošle len vtedy, keď ho vy sami
   zdieľate do inej aplikácie (napr. chatu s AI). Aplikácia ho sama nikam neposiela.

## 3. Uchovávanie a vymazanie údajov

- **V telefóne:** Nastavenia → Súkromie → *Zmazať všetko* natrvalo zmaže všetky údaje. Odinštalovanie aplikácie ich zmaže tiež.
- **Spoločná domácnosť:** Nastavenia → Spoločná domácnosť → *Zmazať údaje domácnosti zo servera* zmaže všetky
  zašifrované údaje domácnosti zo servera. *Odísť z domácnosti* odstráni vaše členstvo.
- Ak nemáte prístup k aplikácii, požiadajte o vymazanie e-mailom (kontakt nižšie) – uveďte, že ide o aplikáciu Bločky.

## 4. Vaše práva (GDPR)

Máte právo na prístup k údajom, opravu, vymazanie, obmedzenie spracúvania, prenosnosť (export CSV v aplikácii)
a podanie sťažnosti na Úrad na ochranu osobných údajov SR (dataprotection.gov.sk).
Keďže údaje sú uložené vo vašom zariadení, väčšinu práv uplatníte priamo v aplikácii.

## 5. Deti

Aplikácia nie je určená deťom do 13 rokov.

## 6. Kontakt

Autor aplikácie – kontaktný e-mail je uvedený na stránke aplikácie v Obchode Google Play.

## 7. Zmeny

O zmenách týchto zásad vás budeme informovať aktualizáciou tejto stránky a dátumu platnosti.

---

## Privacy Policy (English summary)

Bločky stores receipts, items and budgets **only on your device**. No ads, no analytics, no advertising ID, no data sales,
no AI training. The app contacts: (1) the Slovak Financial Administration eKasa service with the receipt ID to fetch receipt items;
(2) optionally Google Firebase (EU) for household sync – data is **end-to-end encrypted** on the device, with an anonymous ID;
(3) optionally Google ML Kit (Gemini Nano) on supported phones, which sends diagnostic data to Google.
Delete everything in Settings → Privacy → Delete everything; delete household server data in Settings → Household.
Contact: the e-mail listed on the app's Google Play page.
