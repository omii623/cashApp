# CashApp – Terv

## 1. Technológia
- Kotlin, Jetpack Compose (Material 3)
- Room (lokális adatbázis), offline működés – **csak lokális, 1 eszköz**, nincs szinkron
- Kézi DI (`di/AppContainer`), MVVM + Flow
- Retrofit – árfolyam API: **fawazahmed0 currency-api** (ingyenes, kulcs nélkül, HUF/RSD/EUR/USD mind benne van; az ECB-alapú Frankfurter **nem** ismeri az RSD-t)
- DataStore – beállítások
- WorkManager + értesítések – ismétlődő tételek, költségkeret figyelmeztetés

## 2. Fő funkciók
1. **Bevétel** rögzítése
2. **Kiadás** rögzítése
3. **Pénzváltás** (egyik pénznemről/számláról a másikra)
4. Kategóriák / alkategóriák kezelése (új, módosít, töröl, sorrend)
5. Számlák (pénztárcák) kezelése – mindegyiknek saját pénznem
6. Pénznemek + árfolyamok (lekérés + kézi felülírás)
7. **Ismétlődő tételek** – esedékesség napján jóvá kell hagyni
8. **Havi költségkeret** – opcionális, kategóriánként
9. Kimutatások, szűrés, keresés
10. Export/import (CSV, JSON mentés)
11. **Magánkölcsönök** – tartozások és kölcsönadások (kamat nélkül)
12. **Hitelkalkulátor** – törlesztőrészlet, előtörlesztés, infláció
13. **Banki kölcsönök** – futó hitelek nyilvántartása törlesztési tervvel

## 3. Adatmodell

| Tábla | Mezők |
|---|---|
| **Account** | id, név, pénznem, kezdő egyenleg, ikon/szín, archivált |
| **Currency** | kód, név, szimbólum, tizedesek, aktív |
| **Category** | id, név, típus (BEVÉTEL / KIADÁS), ikon, szín, sorrend, archivált |
| **Subcategory** | id, categoryId, név, sorrend, archivált |
| **Transaction** | id, típus (BEVÉTEL / KIADÁS / VÁLTÁS), dátum, összeg, pénznem, accountId, categoryId, subcategoryId, megjegyzés |
| **ExchangeDetail** (csak váltásnál) | transactionId, célszámla, cél összeg, cél pénznem, használt árfolyam, árfolyam forrása (API / kézi) |
| **ExchangeRate** (cache) | alap, cél, árfolyam, dátum, forrás |
| **RecurringRule** | id, típus (BEVÉTEL / KIADÁS), összeg, pénznem, accountId, categoryId, subcategoryId, megjegyzés, gyakoriság (napi / heti / havi / éves + intervallum), kezdő dátum, záró dátum (opc.), következő esedékesség, aktív |
| **PendingRecurring** | id, ruleId, esedékes dátum, állapot (VÁR / JÓVÁHAGYVA / KIHAGYVA), transactionId (jóváhagyás után) |
| **Budget** | id, categoryId, havi keret, pénznem, figyelmeztetési küszöb (%, alap 80), aktív |
| **Loan** (magánkölcsön, DB v4) | id, személy neve, irány (TARTOZOM / NEKEM TARTOZNAK), pénznem, nyitó összeg, nyitó visszafizetett rész, kezdés, megjegyzés, lezárás dátuma, szín, ikon |
| **BankLoan** (DB v5) | id, név, bank neve, fajta (LAKÁS / SZEMÉLYI / AUTÓ / EGYÉB), pénznem (= terhelt számla pénzneme), hitelösszeg, THM, futamidő (hónap), első esedékesség (az eredeti szerződés szerint, lehet múltbeli), felülírt havi részlet (opc.), követés előtt befizetett részletek, bank szerinti hátralék (opc.), accountId, categoryId, subcategoryId (opc.), recurringRuleId (opc.), lezárás dátuma, megjegyzés, szín, ikon |
| **BankLoanEvent** (DB v5) | id, bankLoanId, dátum, hányadik részlet után, típus (ELŐTÖRLESZTÉS / KAMATVÁLTOZÁS), összeg, büntető, mód (CSÖKKENŐ RÉSZLET / RÖVIDEBB FUTAMIDŐ), új THM, transactionId (opc.) |

- A Transaction kiegészítései: `loanId`, `loanOpening`, `loanAmountMinor` (magánkölcsön, v4); `bankLoanId` (banki hitel, v5).

- Összeg: `Long` (legkisebb egység), nem `Double`.
- Törlés: ha a kategóriához tartozik tranzakció → archiválás vagy áthelyezés másik kategóriába (rákérdez).
- Első indításkor a `promt.txt` kategóriái/alkategóriái betöltődnek alapértelmezettként.
- Alap pénznemek (első indításkor betöltve):

| Kód | Név | Szimbólum | Tizedesek |
|---|---|---|---|
| HUF | Magyar forint | Ft | 0 |
| RSD | Szerb dinár | din | 0 |
| EUR | Euró | € | 2 |
| USD | Amerikai dollár | $ | 2 |

  A tizedesek száma pénznemenként átállítható.

## 4. Képernyők

### 4.1 Főoldal (Dashboard)
- Teljes egyenleg alap pénznemben (átváltva) + számlánkénti egyenlegek
- Aktuális hónap: bevétel / kiadás / egyenleg
- **3 nagy gomb: Bevétel | Kiadás | Váltás**
- **„Jóváhagyásra vár” kártya** (ha van esedékes ismétlődő tétel) – darabszám, kattintásra a lista
- Utolsó 3–5 tranzakció

### 4.2 Bevétel / Kiadás felvétele (közös űrlap)
- Összeg (nagy számbillentyűzet)
- Pénznem (számla alapján előtöltve, módosítható)
- Számla
- Kategória → alkategória (ikonos rács, gyorsválasztás)
- Dátum (alapból ma), megjegyzés
- Ha a pénznem ≠ számla pénznem: árfolyam mező (lekért, felülírható)

### 4.3 Pénzváltás
- Forrás számla + összeg
- Cél számla (más pénznem)
- Árfolyam: **„Aktuális lekérése”** gomb → kitölti; mező szabadon átírható
- Cél összeg automatikusan számolva; ha a cél összeget írom át → árfolyam újraszámol
- Opcionális díj/jutalék (kiadásként „Egyéb kiadás”-ba könyvelve)
- Jelölés: API vagy kézi árfolyam

### 4.4 Tranzakciók listája
- Napok szerint csoportosítva
- Szűrők: időszak, típus, számla, kategória, alkategória, pénznem
- Keresés megjegyzésben
- Elemre kattintva szerkesztés, húzással törlés (visszavonás snackbar)

### 4.5 Kimutatások
- Időszak választó (hét / hónap / év / egyedi)
- Kördiagram: kiadás kategóriánként → kattintásra alkategóriák
- Oszlopdiagram: havi bevétel vs. kiadás
- Pénznemenként vagy alap pénznemre átváltva

### 4.6 Kategóriák kezelése
- Két fül: Bevétel / Kiadás
- Lista kibontható alkategóriákkal
- Kategória **és alkategória** is: hozzáadás, átnevezés, ikon/szín, törlés/archiválás, húzással sorrend
- Alkategória áthelyezhető másik kategória alá
- Kategória szerkesztő (kiadás kategóriánál): **Havi költségkeret kapcsoló** + összeg, pénznem, küszöb %

### 4.7 Számlák
- Lista egyenleggel, új / szerkesztés / archiválás
- Számla részletei: saját tranzakciólista

### 4.8 Pénznemek és árfolyamok
- Aktív pénznemek ki/bekapcsolása
- **Alap pénznem** kiválasztása (HUF / RSD / EUR / USD)
- Árfolyam tábla: minden pár a 4 pénznem között (HUF↔RSD, HUF↔EUR, HUF↔USD, RSD↔EUR, RSD↔USD, EUR↔USD), utolsó lekérés ideje, „Frissítés” gomb
- Bármely árfolyam kézzel felülírható (kézi érték megjelölve, visszaállítható API-ra)

### 4.9 Ismétlődő tételek
- **Szabályok lista:** név, összeg, gyakoriság, következő esedékesség, aktív kapcsoló
- Új / szerkesztés: ugyanaz az űrlap, mint a Bevétel/Kiadás + gyakoriság, kezdő/záró dátum
- **Jóváhagyásra vár lista:**
  - Jóváhagy → tranzakció létrejön (összeg, dátum, számla előtte módosítható)
  - Kihagy → nem jön létre tranzakció
  - Elhalaszt → későbbi napra
- Elmaradt (nem jóváhagyott) tételek megmaradnak a listában, nem vesznek el
- Tranzakció **soha nem jön létre automatikusan**

### 4.10 Költségkeret (opcionális)
- **Nincs globális kapcsoló** – kategóriánként kapcsolható be a kategória szerkesztőben
- Csak azoknál a kategóriáknál jelenik meg keret (űrlap, főoldal, lista), ahol be van kapcsolva
- Nézet: keret / elköltött / maradék, progress sáv
- Előzmény: korábbi hónapok teljesülése

### 4.11 Beállítások
- Alap pénznem, téma (világos/sötét), nyelv
- Értesítések: ismétlődő tételek (időpont), költségkeret figyelmeztetés be/ki
- Export / import (CSV, JSON backup)
- Adatok törlése

### 4.12 Kölcsönök
Továbbiak → **Kölcsönök** hub: Magánkölcsönök | Banki kölcsönök | Kalkulátor

**Magánkölcsönök** (kész, 1.2.0)
- Kivel, irány, pénznem, rögzítés előtti összeg és abból már visszafizetett rész (külön könyvelhető)
- Mozgások (kölcsön / visszafizetés) a bevétel/kiadás űrlappal azonos felületen, bármely számlán és pénznemben
- Hátralék a kölcsön pénznemében; lezárás maradék hátralékkal is, újranyitás

**Hitelkalkulátor** (kész)
- Annuitásos havi részlet (havi kamat = THM / 12), teljes visszafizetés, hitelköltség
- Előtörlesztés adott hónap után büntetővel és küszöbbel: csökkenő részlet vagy rövidebb futamidő
- Infláció: a részletek és a megtakarítás mai értéken

**Banki kölcsönök** (kész, 1.3.0)
- **Lista:** összesítő kártya (teljes hátralék, havi teher, hitelek száma); hitelenként kártya: ikon, név + bank, hátralék, progress sáv (befizetett / összes hónap), következő részlet dátuma és összege (3 napon belül kiemelve); FAB → új hitel
- **Részletek:**
  - progress kör (% visszafizetve, hátralék / hitelösszeg)
  - 2×2 info rács: havi részlet, THM, lejárat, eddig kamatra ment
  - gombok: **Részlet befizetve**, **Előtörlesztés**
  - kibontható **törlesztési terv**: dátum, részlet, tőke / kamat bontás; befizetett sorok pipával, halványítva
  - ⋮ menü: szerkesztés, kamatváltozás, lezárás / újranyitás, törlés
- **Új / szerkesztés:**
  - név, bank, fajta, hitelösszeg, THM, futamidő, első törlesztés dátuma, terhelt számla, szín és ikon / betű (mint a magánkölcsönnél; alapból a fajta ikonja)
  - **Korábban felvett hitel:** az eredeti szerződés adatai, múltbeli első részlettel. Ilyenkor megjelenik:
    - **Már befizetett részletek:** a dátumból számolva (a mai napig esedékesek), átírható; befizetettnek számítanak, de nem könyvelődnek
    - **Hátralék a bank szerint** (opc., súgóban a számolt érték): ha eltér, a terv ettől folytatódik (a részlet a hátralévő hónapokra újraszámolva); a tervben az érintett sor jelölve
  - felülírható havi részlet (a bank által ténylegesen terhelt összeg)
  - élő előnézet alul: havi részlet, teljes költség (a kalkulátor számolásával)
- **Előtörlesztés:** a kalkulátor előtörlesztés-blokkja (közös composable), előre kitöltve; a két változat közül választva mentés
- A kalkulátorból **„Mentés hitelként”** → előre kitöltött új hitel űrlap

## 5. Navigáció
Alsó sáv: **Főoldal | Tranzakciók | Kimutatások | Továbbiak**
- Továbbiak: Számlák, Kategóriák, Ismétlődő tételek, Költségkeretek (csak ha legalább 1 kategóriánál be van kapcsolva), Kölcsönök (hub: magán / banki / kalkulátor), Pénznemek, Beállítások

## 6. Árfolyam logika
- Lekérés (EUR alapon, 1 hívás): `https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.json` → `huf`, `rsd`, `usd`
- Tartalék, ha ez nem elérhető: `https://latest.currency-api.pages.dev/v1/currencies/eur.json`, ha ez sem: `https://open.er-api.com/v6/latest/EUR`
- Keresztárfolyam számolva, pl. HUF→RSD = (EUR→RSD) / (EUR→HUF)
- Kézi felülírás páronként; a fordított irány automatikusan 1/árfolyam
- Cache Room-ban; offline → utolsó ismert árfolyam (dátummal jelezve)
- Tranzakció mindig az **akkor használt** árfolyamot tárolja (később nem változik)
- Kimutatásokhoz átváltás: aktuális árfolyammal

## 7. Ismétlődő tételek logika
- Napi WorkManager feladat (+ app indításkor is): ahol `következő esedékesség <= ma` → `PendingRecurring` (VÁR) létrehozása, `következő esedékesség` léptetése
- Értesítés: „X tétel vár jóváhagyásra” → megnyitja a listát
- Havi szabály 31-ére: rövidebb hónapban a hónap utolsó napja

## 8. Költségkeret logika
- Elköltött = adott hónap kiadásai a kategóriában, keret pénznemére átváltva
- Küszöb (pl. 80%) és 100% elérésekor egyszeri értesítés havonta
- Kiadás rögzítésekor az űrlapon látszik a kategória maradék kerete

## 9. Banki kölcsönök logika
- **Törlesztési terv** (`domain/loan/AmortizationSchedule`): tiszta számolás, unit tesztelve
  - ugyanaz a kerekítés, mint a `LoanCalculator`-ban (havi kamat = THM / 12, részlet HALF_UP, hátralék egész egységre)
  - részletenként: dátum, részlet, kamat, tőke, hátralék; az utolsó részlet korrigálva, hogy a hátralék pontosan 0 legyen
  - események a megadott részlet után: előtörlesztés (a `prepayment()` két ága), kamatváltozás (új részlet a hátralékból a hátralévő hónapokra)
- **Ismétlődő szabály:** mentéskor a hitelhez havi kiadás szabály jön létre / frissül (kezdés = első esedékesség, záró dátum = utolsó részlet, összeg = részlet, számla, kategória nélkül). A részletek így a meglévő **Jóváhagyásra vár** listán át könyvelődnek – automatikusan most sem jön létre tranzakció.
- Jóváhagyáskor a tétel `bankLoanId`-t kap, ha a szabály banki hitelhez tartozik.
- **Befizetett részletek** = a hitel részlet tételeinek száma; hátralék és következő részlet a törlesztési tervből.
- **„Részlet befizetve”:** ha van függő tétel a következő esedékességre → jóváhagyja; különben közvetlenül könyvel + APPROVED függő sort ír, így az egyedi (ruleId, dueDate) index miatt a generátor nem duplikál.
- **Előtörlesztés:** kiadás tétel (összeg + büntető) + esemény; a szabály összege / záró dátuma frissül (záró dátum változásakor a régi függő tételek törlődnek, mától folytatódik).
- **Kamatváltozás:** esemény + a szabály összegének frissítése.
- **Lezárás:** a szabály kikapcsol; újranyitáskor visszakapcsol. **Törlés:** a hitel, az események és a szabály törlődik, a könyvelt tételek megmaradnak (valódi kiadások voltak; `bankLoanId` → NULL).
- A hitel pénzneme mindig a terhelt számla pénzneme (devizahitel egyelőre nincs).
- **Nincs kategória** (mint a magánkölcsönnél): a részletek és az előtörlesztés kategória nélküli kiadások; a listákban (tranzakciók, Jóváhagyásra vár, szabályok) a hitel neve, színe és ikonja jelöli őket. A szabály kategóriája ezért lehet NULL (DB v5).
- A hitel szabályára koppintva a hitel oldala nyílik (a szabályt a hitel tartja karban).
- A részlet tétele a tranzakciólistában szerkeszthető / törölhető; az űrlapon kategória helyett a hitel látszik, és a hitelhez kötés megmarad.
- A kimutatásokban a részletek külön „Banki kölcsönök” csoportba kerülnek (a magánkölcsönöktől elkülönítve); a tőke / kamat bontás csak a hitel oldalán látszik.
- Mentés / visszaállítás: `bank_loans` a `recurring_rules` után, `bank_loan_events` a `transactions` után; régi mentés továbbra is betölthető.

## 10. Megvalósítási sorrend
1. Projekt váz, Room séma, alap kategóriák betöltése
2. Számlák + pénznemek
3. Bevétel / Kiadás űrlap + lista
4. Pénzváltás + árfolyam API + kézi felülírás
5. Főoldal
6. Kategória kezelés (CRUD)
7. Ismétlődő tételek + jóváhagyás + értesítés
8. Kimutatások
9. Költségkeret (kategóriánként)
10. Export / import, beállítások
11. Magánkölcsönök (DB v4) – kész, 1.2.0
12. Hitelkalkulátor – kész
13. Banki kölcsönök – kész, 1.3.0:
    1. Adatréteg: `BankLoan` / `BankLoanEvent` entitás, DAO, `transactions.bankLoanId`, `MIGRATION_4_5`, mentés / visszaállítás, migrációs teszt
    2. Domain: `AmortizationSchedule` + unit tesztek
    3. `BankLoanRepository`, `RecurringRepository.approve` bővítése, műszeres tesztek
    4. Lista + szerkesztő képernyő, navigáció, hub bejegyzés engedélyezése → **MVP**
    5. Részletek képernyő: törlesztési terv, „Részlet befizetve”
    6. Előtörlesztés + kamatváltozás (közös composable a kalkulátorral)
    7. CSV export („Kölcsön” oszlop a hitel nevével), „Mentés banki hitelként” a kalkulátorból, verzió 1.3.0
