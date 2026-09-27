# HexVG-DatabaseAddon

> Addon do Skripta obsługujący operacje bazodanowe na serwerze **VenomGrave**

🇵🇱 Polski | [🇬🇧 English](README.md)

![version](https://img.shields.io/badge/wersja-1.2.0-blue)
![paper](https://img.shields.io/badge/Paper-1.21.x%20%7C%2026.x-green)
![java](https://img.shields.io/badge/Java-21%2B-orange)
![skript](https://img.shields.io/badge/Skript-2.6.4%2B-purple)
![papi](https://img.shields.io/badge/PlaceholderAPI-opcjonalne-yellow)
![license](https://img.shields.io/badge/licencja-MIT-gray)

---

## O projekcie

HexVG-DatabaseAddon to addon do Skripta stworzony na potrzeby serwera VenomGrave. Sporo pracy poszło w to, żeby zapytania działały porządnie. W pewnym momencie musieliśmy przepisać cały system zapytań, żeby pozbyć się deadlocków, które freezowały serwer. W najnowszej wersji zapytania działają na własnej puli wątków pluginu, więc wynik jest gotowy od razu w następnej linijce skryptu, bez `wait`.

Plugin pozwala pisać skrypty, które komunikują się z MySQL lub SQLite bez żadnej znajomości Javy. Połączenie, pula połączeń HikariCP, obsługa błędów, rollbacki transakcji, blokowanie graczy i tworzenie tabel: to wszystko jest po stronie pluginu. W Skripcie piszesz tylko, co chcesz zrobić z danymi.

---

## Funkcje

- Obsługa **MySQL**, **MariaDB** i **SQLite**
- Zapytania na **osobnej puli wątków**, więc **nie trzeba `wait`** przed odczytem wyniku
- **Transakcje** z automatycznym rollbackiem. Błąd dowolnego zapytania cofa **wszystko**, bez częściowych zapisów
- **Ochrona przed double-spendem**: druga równoległa transakcja tego samego gracza nie wystartuje
- **System blokowania graczy**: zapobiega race conditions przy duplikatach komend
- **Gwarantowane tworzenie tabel**: `db ensure table` czeka, aż tabela powstanie, bez race conditions na starcie
- **Integracja z PlaceholderAPI**: wartości z bazy dostępne w scoreboardach, tablistach, hologramach
- Ochrona przed **SQL injection** przez PreparedStatement
- Walidacja nazw tabel i kolumn
- **Wyniki zapytań osobno dla każdego gracza** (komendy, `on join`, `on death`, GUI…)
- **Samoczynne sprzątanie**: porzucone transakcje i blokady wygasają po 30 s, a wyjście gracza cofa jego transakcję
- **Automatyczne ponowne łączenie** po restarcie MySQL
- **Tryb debug** z logowaniem zapytań i czasem wykonania
- Wszystkie biblioteki spakowane w jarze, bez dodatkowych zależności

---

## Wymagania

| Wymaganie | Wersja |
|-----------|--------|
| Paper | 1.21.x / 26.x |
| Skript | 2.6.4+ (testowane na 2.15 i 2.16.2) |
| Java | 21+ |
| PlaceholderAPI | opcjonalne |

---

## Konfiguracja

```yaml
debug: false

database:
  type: SQLITE   # SQLITE lub MYSQL

  sqlite:
    file: database.db

  mysql:
    host: localhost
    port: 3306
    database: nazwa_bazy
    username: root
    password: ""
    pool-size: 5   # 1-20, dotyczy tylko MySQL
```

---

## Składnia Skript

### Tworzenie tabeli (zalecane)

Czeka, aż tabela powstanie. Bezpieczne w `on skript load`, bez `wait ticks` i bez race conditions, nawet gdy kilku graczy dołączy jednocześnie.

```skript
on skript load:
    db ensure table "players" with query "CREATE TABLE IF NOT EXISTS players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16), coins INT DEFAULT 0)"
```

### Pobieranie danych

Wynik jest dostępny od razu po zapytaniu.

```skript
execute db query "SELECT * FROM players WHERE uuid = ?" with values {_uuid}
set {_coins} to column "coins" from row 1 of last db query result
set {_rows} to db row count of last db query result
set {_names::*} to all db values of column "name" from last db query result
```

> Wiersze liczone są od **1**. Jeśli zapytanie się nie powiedzie, wynik jest pusty (0 wierszy), a nie z poprzedniego zapytania.

### Dodawanie rekordu

```skript
db insert into table "players" columns "uuid" and "coins" values {_uuid} and "0"
```

Albo z listami:

```skript
set {_cols::1} to "uuid"
set {_cols::2} to "coins"
set {_vals::1} to {_uuid}
set {_vals::2} to "0"
db insert into table "players" columns {_cols::*} values {_vals::*}
```

### Aktualizacja i usuwanie

```skript
db update table "players" set "coins" to "%{_new}%" where "uuid" = {_uuid}
db delete from table "players" where "uuid" = {_uuid}
```

### Transakcje

Kilka zapytań jako jedna atomowa operacja: albo wszystko się zapisuje, albo nic.

```skript
db begin transaction

db update table "players" set "coins" to "%{_new}%" where "uuid" = {_uuid}
db insert into table "purchases" columns {_cols::*} values {_vals::*}

db commit transaction

if last db transaction failed:
    send "&cCoś poszło nie tak. Coiny nie zostały pobrane." to player
    stop

send "&aZakup zakończony sukcesem!" to player
```

> **Nie używaj `wait` między `db begin transaction` a `db commit transaction`.** Przy SQLite w tym czasie wszystkie inne zapytania czekają na koniec transakcji.

Co się dzieje przy błędach:

- Błąd zapytania albo zła nazwa tabeli/kolumny w transakcji → **rollback całości**. Kolejne zapytania aż do `db commit transaction` są pomijane.
- Jeśli `db begin transaction` się nie uda (np. ten gracz ma już aktywną transakcję), **reszta skryptu się nie wykona**.
- Transakcja otwarta dłużej niż 30 s (np. `stop` przed commitem) jest cofana automatycznie. Wyjście gracza z serwera też cofa jego transakcję.

### Blokowanie gracza

Zapobiega wielokrotnemu wywołaniu komendy, zanim poprzednie wykonanie się skończy.

```skript
if player is db locked:
    send "&cPoczekaj chwilę przed ponownym użyciem tej komendy." to player
    stop
db lock player

# ... zapytania ...

db unlock player
```

> Blokada wygasa sama po 30 s, gdyby skrypt nie doszedł do `db unlock`.

### Sprawdzanie tabeli

```skript
check db table "players"
if db table "players" exists:
    send "Tabela istnieje"
```

### PlaceholderAPI

Jeśli PlaceholderAPI jest zainstalowane, ekspansja rejestruje się automatycznie. Ustaw wartość ze Skripta po zapytaniu i działa wszędzie, gdzie PAPI jest obsługiwane.

```skript
execute db query "SELECT coins FROM players WHERE uuid = ?" with values {_uuid}
set {_coins} to column "coins" from row 1 of last db query result
db set placeholder "coins" to "%{_coins}%" for player
```

| Placeholder | Opis |
|---|---|
| `%hexvgdb_<klucz>%` | wartość ustawiona przez `db set placeholder` |
| `%hexvgdb_connected%` | `true` / `false`, status połączenia z bazą |
| `%hexvgdb_locked%` | `true` / `false`, czy gracz ma aktywny lock |

Wartości placeholderów są czyszczone po wyjściu gracza, więc ustawiaj je w `on join`.

---

## Ważne: wydajność

- **`wait` nie jest już potrzebny.** Każdy efekt bazodanowy czeka na wynik, więc kolejna linijka skryptu ma już dane.
- Na czas zapytania skrypt wstrzymuje tick serwera. Przy SQLite i lokalnym MySQL to zwykle milisekundy (200 zapytań ≈ 0,08 s w testach).
- Jeśli baza przestanie odpowiadać, zapytanie kończy się błędem po ok. **4 s**. Serwer się nie zawiesza na stałe, a po powrocie bazy plugin sam się ponownie łączy.
- Tekst od gracza przekazuj **zawsze** przez `with values`, nigdy przez sklejanie SQL-a.
- MySQL 8: jeśli pojawi się „Public Key Retrieval is not allowed”, użyj użytkownika z `mysql_native_password` albo połączenia SSL.

---

## Komendy

| Komenda | Opis | Uprawnienie |
|---------|------|-------------|
| `/hexvgdb status` | Status połączenia z bazą | `hexvg.database.admin` |
| `/hexvgdb debug` | Włącza / wyłącza tryb debug | `hexvg.database.admin` |
| `/hexvgdb reload` | Przeładowuje konfigurację | `hexvg.database.admin` |

Domyślnie dostępne tylko dla operatorów.

---

## Przykładowe skrypty

W repozytorium znajdują się dwa przykłady:

- [`example.sk`](../example.sk): system coinów z SELECT, INSERT, UPDATE, DELETE, transakcjami i lockami
- [`example_papi.sk`](../example_papi.sk): system statystyk (coiny, kills, rank) z pełną integracją PlaceholderAPI

---

## Autorzy

Stworzony dla serwera **VenomGrave** przez HexVG Team.  
Błędy i propozycje: https://github.com/VenomGrave/HexVG-DatabaseAddon/issues
