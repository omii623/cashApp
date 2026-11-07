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

## 5. Navigáció
Alsó sáv: **Főoldal | Tranzakciók | Kimutatások | Továbbiak**
- Továbbiak: Számlák, Kategóriák, Ismétlődő tételek, Költségkeretek (csak ha legalább 1 kategóriánál be van kapcsolva), Pénznemek, Beállítások

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

## 9. Megvalósítási sorrend
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
