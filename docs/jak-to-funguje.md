# Jak mujDokladomat funguje

Aplikace je pro OSVČ. Uživatel se zaregistruje podle IČO, vystavuje doklady a u placeného tarifu si k tomu připojí banku, daně a další služby. Není to účetní ani daňové poradenství. Za správnost dokladů odpovídá uživatel.

Provozuje ji Velocode Studio, Matěj Přívozník. Kontakt je na [matejprivoznik.eu](https://matejprivoznik.eu) a [velocode.eu](https://velocode.eu).

## Účet

Registrace začíná IČO. Název, sídlo a DIČ se načtou z ARES a uživatel je ručně nepřepíše. Znovu se načtou jen tlačítkem v nastavení. Na faktuře zůstane otisk dodavatele a odběratele z dne vystavení, pozdější změna v ARES starý doklad nepřepíše.

Po registraci přijde šestimístný ověřovací kód. Platí 15 minut, je pět pokusů a nový kód jde poslat po 60 sekundách. Bez klíče k poště se kód jen vypíše do logu aplikace.

První přihlášení nového účtu otevře stránku **Rychlé nastavení**. Jsou tam účet, telefon, daně, číslování a volitelné Premium položky. U každé je cesta, kudy se k ní uživatel vrátí. Přeskočení stránku zavře. Zavření prohlížeče bez uložení ji příště ukáže znovu. Účty založené dřív, ukázkový účet a správce tuhle stránku nedostanou.

Dvoufázové ověření je v Nastavení → Spravovat 2FA a používá aplikaci Authenticator.

## Tarify

| | Freemium | Premium |
| --- | --- | --- |
| Cena | 0 Kč | 199 Kč měsíčně, nebo 1 990 Kč ročně |
| Faktury | 5 za kalendářní měsíc | neomezeně |
| ARES, kontakty, sklad | ano | ano |
| Základní faktura v CZK a QR platba | ano | ano |
| Export a import faktur v JSON | ano | ano |
| Propojení banky a token Fio | ne | ano |
| Daně, odvody, CSV pro účetní, Pohoda XML | ne | ano |
| Náklady, upomínky, další firma | ne | ano |
| Záloha, dobropis, nabídka, objednávka, cizí měna, pravidelné doklady | ne | ano |
| Lustrace ISIR a nespolehlivý plátce | ne | ano |

Do limitu pěti faktur se počítá faktura, zálohová faktura a dobropis vystavené v aktuálním měsíci. Nabídka a objednávka se do limitu nepočítají, ve Freemium je ale stejně nejde vystavit. Limit je na celý účet, ne na jednu firmu.

Bez klíčů ke Stripe tlačítko Premium účet jen ukázkově zapne a peníze nestrhne. Na veřejném serveru proto Stripe klíče nastav. Postup je v [nasazení na VPS](vps.md).

## Co uživatel v aplikaci dělá

- **Přehled** ukáže počet faktur a sklad. Obrat, zisk, limit DPH a odkazy na export vidí jen Premium.
- **Faktury** mají číslo podle vzoru. Výchozí vzor je `DRUHRRRRMM-CCC`. `RRRR` je rok, `MM` měsíc, `CCC` pořadí a `DRUH` je prázdné u faktury, `Z` u zálohy, `D` u dobropisu, `N` u nabídky a `O` u objednávky.
- Na faktuře v korunách s kladnou částkou vznikne QR platba. Číslo účtu má tvar `123456789/0100`. Nabídka, objednávka a dobropis se zápornou nebo nulovou částkou QR nemají.
- Cizí měna (EUR, USD, GBP) stáhne kurz ČNB ke dni zdanitelného plnění a základ i DPH přepočte do korun.
- Veřejný odkaz `/p/…` ukáže doklad bez přihlášení. Odkaz vzniká s dokladem.
- **Platby** spárují příchozí částku s variabilním symbolem. Shoda je na symbolu nebo na číslicích z čísla dokladu a na částce v toleranci 0,5 Kč. Cizí měna se porovnává s přepočtem do korun. Spárovaný doklad se označí jako zaplacený.
- Výpis jde nahrát jako CSV, GPC (ABO) nebo camt.053. Token Fio se ukládá k firmě a do exportu osobních údajů se nedává.
- **Náklady** vstupují do odhadu zisku. **Daně** spočítají paušál, daň z příjmů a odvody za zvolený rok. Export pro účetní je `/export/ucetni.csv` a `/export/pohoda.xml`.
- **Nastavení** drží údaje z ARES, účet a kontakt, daňové volby, upomínky, souhlasy, export osobních údajů a výmaz účtu.

## Co běží samo

Časy jsou v pásmu Europe/Prague a jen pro účty s aktivním Premium.

| Kdy | Úloha | Co udělá |
| --- | --- | --- |
| 6:15 | pravidelné doklady | vystaví fakturu ze šablony, jejíž den nastal |
| 6:45 | banka | u propojené banky a tokenu Fio zkontroluje platby za posledních 30 dní |
| 7:30 | upomínky | po splatnosti označí doklad a pošle jemnou nebo formální připomínku |

Jemná upomínka je ve výchozím stavu po 3 dnech, formální po 14. Když nastanou obě najednou a žádná ještě neodešla, odešle se jen formální. Doklad bez e-mailu odběratele se označí jako po splatnosti, zpráva se neposílá.

## Správce

Účet správce má roli `ROLE_ADMIN` a vidí `/admin`. Jsou tam uživatelé, registrace, IP adresy a historie přístupů. Správce může účet zablokovat. Nemůže zablokovat sám sebe ani jiného správce.

E-mail správce je ve výchozím stavu `admin@minifaktura.local`. Když heslo nenastavíš, při prvním založení se jednou vypíše do logu. Existující účet se při dalším startu nepřepisuje.

Ukázkový účet `jan.novak@example.com` s heslem `heslo123` vznikne jen tehdy, když je databáze v tu chvíli úplně prázdná. Správce se zakládá dřív, takže při běžném prvním startu ukázkový účet nevznikne. Kdyby v databázi byl, na veřejném serveru ho smaž.

## Co aplikace venku volá

Žádný z těchto zdrojů nepotřebuje klíč v kódu. Potřebuje odchozí internet.

- ARES: `https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/ekonomicke-subjekty/`
- ČNB: denní kurz
- ISIR: `https://isir.justice.cz`
- nespolehliví plátci: `https://www.financnisprava.cz`
- Resend: `https://api.resend.com`, jen když je nastavený klíč
- GoCardless Bank Account Data: `https://bankaccountdata.gocardless.com/api/v2`, jen s klíči
- Fio: `https://fioapi.fio.cz`, token zadává uživatel v aplikaci
- Stripe: jen s klíči

Formát JSON pro přenos faktur zůstává `minifaktura-invoices`, verze 1. Soubory ke stažení se jmenují `mujdokladomat-*.json`.
