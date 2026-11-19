# mujDokladomat

Dokumentace k fakturační aplikaci. Zdrojáky jsou v Javě 21 a Spring Boot 3.2.5, data jsou v PostgreSQL. Uživatelské texty v aplikaci jsou česky.

| Dokument | K čemu je |
| --- | --- |
| [Jak to funguje](jak-to-funguje.md) | Účty, tarify, doklady, banky, daně a co běží samo |
| [Nasazení na VPS](vps.md) | Server, databáze, HTTPS, proměnné a zálohy |

Tajné klíče do Gitu nepatří. Patří do proměnných prostředí, nebo do `src/main/resources/application-local.properties`. Ten soubor je v `.gitignore`.
