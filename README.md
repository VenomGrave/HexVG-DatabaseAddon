# HexVG-DatabaseAddon

> Skript addon for database operations on the **VenomGrave** server


---

## About

HexVG-DatabaseAddon is a Skript addon built for the VenomGrave server. A lot of work went into making the async architecture solid — at some point we had to rewrite the entire query system to eliminate deadlocks that were freezing the server while waiting for database responses.

The plugin lets you write Skript scripts that communicate with a MySQL or SQLite database without any Java knowledge. Connection handling, HikariCP pooling, error management, transaction rollbacks, player locking and table creation are all handled on the plugin side — in Skript you just write what you want to do with the data.

---

## Features

- **MySQL** and **SQLite** support
- Fully **asynchronous** queries — the server never freezes
- **Transaction support** with automatic rollback on failure
- **Player lock system** — prevents race conditions from duplicate command calls
- **Guaranteed table creation** — `db ensure table` blocks until the table exists, no race conditions on startup
- **PlaceholderAPI integration** — expose database values to scoreboards, tablists, holograms
- **SQL injection** protection via PreparedStatement
- Table and column name validation
- Per-player **result cache**
- **Debug mode** with query logging and execution time
- All libraries shaded inside the jar — no extra dependencies required

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Paper | 1.16.5+ |
| Skript | 2.6+ |
| Java | 11+ |
| PlaceholderAPI | optional |

---

## Installation

1. Drop `HexVG-DatabaseAddon.jar` into your `plugins/` folder
2. Start the server — the plugin will generate `config.yml`
3. Configure your database connection
4. Restart the server

> When using MySQL, make sure to create the database beforehand: `CREATE DATABASE your_database;`

---

## Configuration

```yaml
debug: false

database:
  type: SQLITE   # SQLITE or MYSQL

  sqlite:
    file: database.db

  mysql:
    host: localhost
    port: 3306
    database: your_database
    username: root
    password: ""
    pool-size: 5
```

---

## Skript Syntax

### Create a table (recommended)

Blocks until the table exists — safe to use in `on skript load`, no `wait ticks` needed, no race conditions even when multiple players join at once.

```skript
on skript load:
    db ensure table "players" with query "CREATE TABLE IF NOT EXISTS players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16), coins INT DEFAULT 0)"
```

### Execute a query

```skript
execute db query "SELECT * FROM players WHERE uuid = ?" with values {_uuid}
wait 2 ticks
set {_coins} to column "coins" from row 1 of last db query result
set {_rows} to db row count of last db query result
```

### Insert a record

```skript
set {_cols::1} to "uuid"
set {_cols::2} to "coins"
set {_vals::1} to {_uuid}
set {_vals::2} to "0"
db insert into table "players" columns {_cols::*} values {_vals::*}
```

### Update data

```skript
db update table "players" set "coins" to "%{_new}%" where "uuid" = {_uuid}
```

### Delete a record

```skript
db delete from table "players" where "uuid" = {_uuid}
```

### Transactions

Multiple queries executed as one atomic operation. If anything fails, everything is automatically rolled back.

```skript
db begin transaction

db update table "players" set "coins" to "%{_new}%" where "uuid" = {_uuid}
wait 2 ticks
db insert into table "purchases" columns {_cols::*} values {_vals::*}
wait 2 ticks

db commit transaction

if last db transaction failed:
    send "&cSomething went wrong. Your coins were not taken." to player
    stop

send "&aPurchase successful!" to player
```

> `db begin transaction` and `db commit transaction` do not require a `wait` — they block internally until the operation completes.

### Player lock

Prevents a player from triggering the same command multiple times before the previous execution finishes.

```skript
if player is db locked:
    send "&cPlease wait before using this command again." to player
    stop
db lock player

# ... your queries ...

db unlock player
```

### PlaceholderAPI

If PlaceholderAPI is installed, the expansion registers automatically. Set values from Skript after a query and they become available as placeholders anywhere on the server.

```skript
execute db query "SELECT coins FROM players WHERE uuid = ?" with values {_uuid}
wait 2 ticks
set {_coins} to column "coins" from row 1 of last db query result
db set placeholder "coins" to "%{_coins}%" for player
```

| Placeholder | Description |
|---|---|
| `%hexvgdb_<key>%` | value set via `db set placeholder` |
| `%hexvgdb_connected%` | `true` / `false` — database connection status |
| `%hexvgdb_locked%` | `true` / `false` — whether the player has an active lock |

---

## Important — wait ticks

All regular queries run asynchronously, so you need to give the database a moment before reading the result. `wait 2 ticks` is enough for regular queries.

`db ensure table`, `db begin transaction` and `db commit transaction` do **not** require a wait — they block internally.

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/hexvgdb status` | Shows database connection status | `hexvg.database.admin` |
| `/hexvgdb debug` | Toggles debug mode on/off | `hexvg.database.admin` |
| `/hexvgdb reload` | Reloads the configuration | `hexvg.database.admin` |

Default permission: **op only**

---

## Example Scripts

The repository includes two example scripts:

- `example.sk` — coin system with SELECT, INSERT, UPDATE, DELETE, transactions and player locking
- `example_papi.sk` — stats system (coins, kills, rank) with full PlaceholderAPI integration

---

## Authors

Built for the **VenomGrave** server by the HexVG team.  
Issues and suggestions: https://github.com/VenomGrave/HexVG-DatabaseAddon/issues
