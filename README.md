# Campus Cafe – Cafeteria Management System (PAT 4)
Java + **JDBC** + **Oracle SQL** web application. No frameworks – uses the JDK's built-in web server, so there is nothing
to deploy. Matches the lab slides (Oracle XE, `ojdbc` Type-4 thin driver, `PreparedStatement`, `ResultSet`, transactions).

## 1. What the application does
| Requirement from faculty | Where it is |
|---|---|
| Login page | `/login` – username + password (SHA-256 hash checked with a `PreparedStatement`), sessions, roles ADMIN / CUSTOMER, plus `/register` |
| Accept inputs | Menu search (category, keyword, max price), order form (item quantities + payment mode), wallet top-up, admin add/edit menu |
| Process inputs | **Billing & eligibility engine** (`Dao.placeOrder`) – see rules below |
| Display outputs | Itemised bill, wallet before/after, order history, admin dashboard (revenue, sales by category, top 5 items, low stock) |
| DDL | `sql/schema.sql` – CREATE TABLE ×4, ALTER TABLE, CREATE INDEX ×2, CREATE VIEW, DROP |
| DML | INSERT (sample data + app), UPDATE (price/stock/status/wallet), DELETE (menu item), SELECT with JOIN / GROUP BY / aggregates |
| Transactions | `setAutoCommit(false)` → `commit()` / `rollback()` in order placement and order cancellation |
| Validation & errors | Blank/number/range checks; friendly messages for duplicate, check-constraint, foreign-key, connection errors |

### Order-processing rules (the "loan eligibility" equivalent)
1. Prices are read from the database (never trusted from the browser); stock and availability are checked.
2. `Subtotal = Σ price × qty`
3. Discount: **Student 10%** (bill ≥ ₹100) **or Staff 5%**, **+ 5% extra** if bill ≥ ₹500.
4. `GST = 5% × (Subtotal − Discount)`  →  `Total = Subtotal − Discount + GST`
5. **Wallet eligibility:** wallet must be ≥ Total, otherwise the order is rejected and the shortfall is shown. Cash is always allowed.
6. In ONE transaction: insert order + order lines, reduce stock (`... WHERE STOCK >= ?`), debit wallet (`... WHERE WALLET_BALANCE >= ?`).
   If anything fails, everything is rolled back.
7. Cancelling a PLACED order restocks the items and refunds the wallet (also one transaction).

Worked example: Student orders 2 × Chicken Biryani (140) + 1 × Filter Coffee (20) → subtotal 300.00, discount 30.00,
GST 13.50, **total 283.50**.

## 2. Software needed
* **JDK 17 or newer** (`java -version`, `javac -version`) – https://adoptium.net
* **Oracle Database XE 18c/21c** (or the lab's Oracle server) + SQL*Plus – the version must support identity columns (12c+).
* **Oracle JDBC driver `ojdbc11.jar`** (Type-4 thin driver).

## 3. Installation & setup – step by step
**Step 1 – Check Oracle is running and find your service name**
```
sqlplus system/<password>@localhost:1521/XEPDB1
SQL> SELECT name FROM v$database;      -- or: SELECT sys_context('USERENV','SERVICE_NAME') FROM dual;
SQL> EXIT
```
(Older labs use SID `XE` → URL `jdbc:oracle:thin:@localhost:1521:XE`.)

**Step 2 – Create the tables and sample data (DDL + DML) in SQL*Plus**
```
sqlplus system/<password>@localhost:1521/XEPDB1
SQL> @sql/schema.sql
SQL> SELECT COUNT(*) FROM MENU_ITEM;    -- should be 13
SQL> EXIT
```
*(Alternative without SQL*Plus: step 5's `setup` command runs the same file through JDBC.)*

**Step 3 – Install the JDBC driver**  
Download `ojdbc11.jar` into the `lib/` folder:
* Browser: https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.5.0.24.07/ojdbc11-23.5.0.24.07.jar  
  (or from Oracle: https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html)
* Or command line:
  ```
  curl -L -o lib/ojdbc11.jar https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.5.0.24.07/ojdbc11-23.5.0.24.07.jar
  ```

**Step 4 – Configure the connection** – edit `db.properties`
```
db.url=jdbc:oracle:thin:@//localhost:1521/XEPDB1
db.user=system
db.password=YOUR_PASSWORD
db.driver=oracle.jdbc.OracleDriver
http.port=8080
```

**Step 5 – Compile and run**
| | Linux / macOS | Windows |
|---|---|---|
| (optional) create schema via JDBC | `./run.sh setup` | `run.bat setup` |
| start the website | `./run.sh` | `run.bat` |

Manual commands (same thing):
```
javac -encoding UTF-8 -cp "lib/ojdbc11.jar" -d out src/*.java
java -cp "out:lib/ojdbc11.jar" Main          # Windows: use ; instead of :
```
You should see `Database connected: jdbc:oracle:thin:...` and `Cafeteria app running -> http://localhost:8080`.

**Step 6 – Open http://localhost:8080** and sign in:
| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Student (wallet ₹500) | `student1` | `student123` |
| Staff (wallet ₹800) | `staff1` | `staff123` |

## 4. Suggested demo / test cases for the viva
1. Wrong password → "Invalid username or password" (login uses a parameterised query, so `' OR '1'='1` fails).
2. Student orders biryani ×2 + coffee ×1 with wallet → bill ₹283.50, wallet 500 → 216.50.
3. Order 20 × Cold Coffee (stock 8) → rejected, nothing changes (rollback).
4. Student orders 28 × Biryani on wallet → "Not eligible… short by ₹…".
5. Cancel the order → stock restored, wallet refunded.
6. Admin: add item, edit price/stock, disable item, try to delete an item already ordered → friendly FK error.
7. Admin: change order status; check the dashboard totals; verify in SQL*Plus:
   `SELECT * FROM V_ORDER_SUMMARY;`  `SELECT ITEM_NAME, STOCK FROM MENU_ITEM;`

## 5. Troubleshooting (from the lab slide)
| Symptom | Fix |
|---|---|
| `JDBC driver not found` | `ojdbc11.jar` missing from `lib/` / classpath |
| `ORA-12541 / listener` or connection refused | Start the Oracle service (`lsnrctl status`); check host, port, service name |
| `ORA-01017 invalid username/password` | Fix `db.user` / `db.password` |
| `ORA-00942 table or view does not exist` | Run `@sql/schema.sql` in the **same schema** as `db.user` |
| `ORA-00907 / ORA-02000` near `GENERATED ALWAYS AS IDENTITY` | Oracle older than 12c – replace identity columns with `CREATE SEQUENCE` |
| Port 8080 busy | Change `http.port` |
| Rupee sign garbled | Compile with `-encoding UTF-8` |

## 6. ODBC (if your faculty asks for it)
The app is written with JDBC (Java). The ODBC route from the slides: install the Oracle ODBC driver → create a DSN
(Windows: *ODBC Data Sources (64-bit)* → System DSN → Oracle driver, data source `CAFE_DSN`, service `XEPDB1`) → connect
from any ODBC-capable front end (Python: `pip install pyodbc`, `pyodbc.connect("DSN=CAFE_DSN;UID=system;PWD=...")`) and run the
same SQL from `sql/schema.sql`. The JDBC-ODBC bridge was removed in Java 8, so Java uses the Type-4 thin driver instead.

## 7. Project layout
```
cafeteria-app/
├── db.properties        connection settings
├── run.sh / run.bat     build + run helpers
├── sql/schema.sql       all DDL + DML (SQL*Plus ready)
├── lib/                 ojdbc11.jar goes here
└── src/
    ├── Db.java          connection utility, password hashing, SQL-error translation, SetupDb loader
    ├── Dao.java         every SQL statement (PreparedStatement, ResultSet, transactions)
    └── Main.java        web server, login/session, pages
```
## 8. Security notes
Passwords are stored as SHA-256 hashes; all SQL uses bind variables; all output is HTML-escaped; admin pages are role-checked
on the server; order prices come from the database. (A production system would add salted slow hashing such as bcrypt, HTTPS and CSRF tokens.)
