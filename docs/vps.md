# Nasazení na VPS

Postup je pro jeden linuxový server, na kterém běží aplikace, PostgreSQL a HTTPS. Aplikace poslouchá jen na `127.0.0.1:8080`. Ven jde nginx na portech 80 a 443.

Na veřejném serveru nastav Stripe. Bez klíčů tlačítko Premium účet zapne zdarma a peníze nestrhne.

## Co server potřebuje

- Java 21
- PostgreSQL 14 nebo novější
- nginx a certifikát (třeba Certbot)
- odchozí internet na ARES, ČNB, poštu a případně banku a Stripe
- čas v pásmu `Europe/Prague`, ať ranní úlohy sedí na český den

Porty 5432 a 8080 z internetu neotevírej. Veřejné jsou jen 22, 80 a 443.

## Databáze

```bash
sudo -u postgres psql
```

```sql
CREATE USER fakturace WITH PASSWORD 'heslo-jen-pro-tento-server';
CREATE DATABASE fakturace OWNER fakturace;
\q
```

V `pg_hba.conf` nech přístup jen z localhostu. Tabulky zakládat ručně nemusíš. Při startu je doplní Hibernate (`ddl-auto=update`). To je pohodlné pro jeden server a malý počet uživatelů. Před každým nasazením nové verze udělej zálohu, protože automatická změna schématu starou zálohu sama nevrátí.

## Sestavení

Na serveru, nebo u sebe a pak soubor nakopíruj:

```bash
mvn -B -DskipTests package
```

Vznikne `target/fakturace-0.0.1-SNAPSHOT.jar`. Na serveru ho dej třeba do `/opt/mujdokladomat/app.jar`.

## Proměnné

Soubor `/etc/mujdokladomat.env` má práva `600` a vlastníka uživatele, pod kterým služba běží. Hodnoty sem piš jen na serveru.

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fakturace
SPRING_DATASOURCE_USERNAME=fakturace
SPRING_DATASOURCE_PASSWORD=heslo-jen-pro-tento-server
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SERVER_FORWARD_HEADERS_STRATEGY=framework
TZ=Europe/Prague

APP_ADMIN_EMAIL=admin@tvoje-domena.cz
APP_ADMIN_PASSWORD=dlouhe-heslo-spravce

RESEND_API_KEY=
APP_MAIL_FROM=mujDokladomat <faktury@tvoje-domena.cz>

STRIPE_SECRET_KEY=
STRIPE_PUBLISHABLE_KEY=
STRIPE_WEBHOOK_SECRET=
STRIPE_PRICE_MONTHLY=
STRIPE_PRICE_YEARLY=

GOCARDLESS_SECRET_ID=
GOCARDLESS_SECRET_KEY=
```

`SERVER_FORWARD_HEADERS_STRATEGY=framework` je potřeba. Za nginxem si aplikace z hlaviček vezme skutečnou adresu `https://tvoje-domena.cz`. Podle ní skládá návrat z banky a z platby. Bez toho by bance poslala `http://127.0.0.1:8080`.

### Co která proměnná dělá

| Proměnná | Když chybí |
| --- | --- |
| `SPRING_DATASOURCE_*` | aplikace se nepřipojí a nenastartuje |
| `APP_ADMIN_EMAIL` | správce je `admin@minifaktura.local` |
| `APP_ADMIN_PASSWORD` | při prvním založení se heslo jednou vypíše do logu |
| `RESEND_API_KEY` a `APP_MAIL_FROM` | ověřovací kód, faktura i upomínka se jen vypíšou do logu |
| `STRIPE_SECRET_KEY` a `STRIPE_PRICE_MONTHLY` / `STRIPE_PRICE_YEARLY` | Premium se zapne ukázkově, bez platby |
| `STRIPE_WEBHOOK_SECRET` | po zaplacení se předplatné nepotvrdí z brány |
| `GOCARDLESS_SECRET_ID` a `GOCARDLESS_SECRET_KEY` | nejde živé čtení bank. Zůstane výpis CSV, GPC, XML a token Fio |

Blok `spring.mail.*` v `application.properties` se v kódu nepoužívá. Pošta jde jen přes Resend. Odesílací adresa musí být u Resend ověřená, jinak API zprávu odmítne.

Token Fio se do tohoto souboru nepíše. Uživatel s Premium ho vloží v menu Platby.

Heslo správce se použije jen při založení. Změna v souboru existující účet nepřepíše.

## Služba

`/etc/systemd/system/mujdokladomat.service`:

```ini
[Unit]
Description=mujDokladomat
After=network.target postgresql.service

[Service]
User=mujdokladomat
WorkingDirectory=/opt/mujdokladomat
EnvironmentFile=/etc/mujdokladomat.env
ExecStart=/usr/bin/java -jar /opt/mujdokladomat/app.jar
Restart=on-failure
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now mujdokladomat
sudo journalctl -u mujdokladomat -n 80
```

Při prvním startu si v logu najdi řádek `>>> Admin účet …`. Když jsi heslo nenastavil, je v tom řádku a podruhé se neukáže.

Kontrola z serveru:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/login
```

Čekaná odpověď je `200`.

## nginx a HTTPS

```nginx
server {
    listen 80;
    server_name tvoje-domena.cz;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name tvoje-domena.cz;

    client_max_body_size 6m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

Certbot doplní řádky `ssl_certificate`. `client_max_body_size 6m` kryje nahrání výpisu a JSON, limit v aplikaci je 5 MB.

Ve Stripe nastav webhook na `https://tvoje-domena.cz/billing/webhook` a události předplatného. Tajemství z webhooku dej do `STRIPE_WEBHOOK_SECRET`.

Návrat z banky je `https://tvoje-domena.cz/payments/bank/callback`. Uživatel musí být ve stejném prohlížeči pořád přihlášený, jinak ho návrat pošle na přihlášení.

## Zálohy

Jednou denně, mimo šestou a sedmou hodinu, kdy běží úlohy:

```bash
pg_dump -U fakturace -d fakturace -Fc -f /var/backups/mujdokladomat-$(date +%F).dump
```

Soubor stáhni i mimo server. Obnova je `pg_restore` do prázdné databáze stejného jména a pak start aplikace.

Novou verzi nasazuj takhle: záloha, výměna jaru, `systemctl restart mujdokladomat`, kontrola logu a stránky `/login`.

## Po spuštění

1. Přihlas se účtem správce a ověř `/admin`.
2. Založ jeden vlastní účet přes registraci a ověř, že přijde kód, nebo že je v logu.
3. Vystav zkušební fakturu a zkontroluj QR platbu.
4. Až budeš chtít skutečné Premium, založ ve Stripe dvě ceny (199 Kč měsíčně a 1 990 Kč ročně) a jejich `price_…` dej do proměnných.
5. Až budeš chtít čtení bank, založ klíče v GoCardless Bank Account Data a restartuj službu.

Aplikace nemá samostatný instalační průvodce. Všechno z této stránky se nastavuje na serveru, ne v obrazovkách pro OSVČ.
