# BusinessBuddy: AI-Powered Business Cost and Inventory Management System

BusinessBuddy is a Java 21 / Spring Boot 3.5 / PostgreSQL web application for small businesses that make or sell physical products (bakeries, caterers, soap and candle makers). It records purchases, keeps a traceable inventory ledger, costs versioned recipes, suggests selling prices, records production, sales and expenses, reports profitability, and forecasts demand with an evaluated machine-learning model.

## Contents

| Path | What it is |
|---|---|
| `src/main/java/com/businessbuddy` | Application code, organised by layer (see below) |
| `src/main/resources/db/migration` | Flyway migrations that build `businessbuddy_db` |
| `src/main/resources/templates` | Thymeleaf pages |
| `src/test/java/com/businessbuddy` | Unit, integration, security and system tests |
| `Dockerfile`, `docker-compose.yml` | Container deployment |
| `.env.example` | Names of the environment variables (no secrets) |
| `.github/workflows/ci.yml` | Continuous integration workflow |

Packages: `config`, `controller`, `dto`, `entity`, `repository`, `service` (with `costing`, `inventory`, `pricing`, `reporting`, `insights`), `ml`, `security`, `validation`, `exception`, `audit`, `util`.

## Requirements

* Java 21 or later (tested on Temurin JDK 25 compiling for release 21)
* No Maven installation is needed: use the Maven wrapper (`mvnw` / `mvnw.cmd`). It downloads Maven 3.9.11 on first use.
* No PostgreSQL installation is needed for the **local** profile, which starts an embedded PostgreSQL server.
* Internet access on the first build to download dependencies from Maven Central.

## Option 1: run on one computer (local profile, embedded PostgreSQL)

```bash
./mvnw -B package -DskipTests
java -jar target/businessbuddy.jar
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

Open <http://localhost:8080>. On first start the application:

1. creates the database `businessbuddy_db` in `./data/postgres` and applies the Flyway migrations;
2. creates a local Administrator account;
3. generates **Sunrise Bakery (sample data)**, 120 days of clearly labelled demonstration data, and runs the forecasts. This takes about a minute and happens once.

Sign-in details for the local demonstration copy (change them for any real use):

| Account | Email | Password |
|---|---|---|
| Owner (sample business) | `owner@sunrise-bakery.test` | `SampleBakery#2026` |
| Manager (sample business) | `manager@sunrise-bakery.test` | `SampleBakery#2026` |
| Employee (sample business) | `employee@sunrise-bakery.test` | `SampleBakery#2026` |
| Administrator | `admin@businessbuddy.test` | `LocalAdmin#2026` |

The values come from `application-local.yml` and can be overridden with the environment variables `BUSINESSBUDDY_DEMO_PASSWORD`, `BUSINESSBUDDY_ADMIN_EMAIL` and `BUSINESSBUDDY_ADMIN_PASSWORD`. Set `BUSINESSBUDDY_DEMO_DATA=false` to start with an empty system and register your own business at `/register`.

To start again from an empty database, stop the application and delete the `data` folder.

## Option 2: Docker Compose (application + PostgreSQL 16)

```bash
cp .env.example .env      # then fill in DB_USERNAME, DB_PASSWORD and the Administrator account
docker compose up -d --build
```

The application listens on `127.0.0.1:8080` for a reverse proxy (Nginx) that terminates HTTPS. This route is provided as designed in Chapter 4 (section 4.4.3); it was not run on the development machine, which has no Docker.

## Option 3: your own PostgreSQL server (dev profile)

Create an empty database `businessbuddy_db` and a user, then:

```bash
export DB_USERNAME=businessbuddy DB_PASSWORD=...
java -jar target/businessbuddy.jar --spring.profiles.active=dev
```

## Running the tests

```bash
./mvnw -B test
```

Unit tests (`*Test`) check the calculations. Integration tests (`*IT`) start an embedded PostgreSQL database and test the services, security rules and pages through the full stack. Reports are written to `target/surefire-reports`.

## Configuration

| Setting | Default | Meaning |
|---|---|---|
| `businessbuddy.security.max-failed-logins` | 5 | Failed logins before an account is locked |
| `businessbuddy.security.lockout-minutes` | 15 | Lockout duration |
| `businessbuddy.inventory.cost-increase-threshold` | 0.10 | Purchase cost rise that raises an alert |
| `businessbuddy.forecast.min-history-days` | 56 | History needed before a demand forecast is made |
| `businessbuddy.forecast.test-days` | 14 | Held-out test period for model evaluation |
| `businessbuddy.forecast.horizon-days` | 7 | Days forecast ahead |
| `businessbuddy.jobs.nightly-cron` | `0 30 1 * * *` | Nightly stock checks and forecasts |

## Known limitations

* Forecasts in the sample business are trained on generated data and say nothing about real-world accuracy.
* Stock is not tracked by delivery lot, so expiry alerts refer to deliveries rather than individual units.
* Supplier lead times are not stored; stockout warnings use a fixed 7-day window.
* Profit and expense forecasting are future extensions (decision D9).
* BusinessBuddy is not accounting software and does not give tax advice.
