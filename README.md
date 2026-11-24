# Hospital Appointment Management

A Java web application for booking and managing medical appointments, with separate patient and administrator workflows. Originally developed as a university project, it was subsequently improved for Josias Cisneros's software engineering portfolio.

The project demonstrates server-side authorization, transactional business rules, relational persistence and a server-rendered interface. The application interface is in Spanish.

## Features

- Patient registration, login, profile management and password recovery through a local test mailbox.
- Doctor and availability management, with account and doctor deactivation.
- Appointment booking in 30-minute slots, a review step before booking, and cancellation confirmations.
- An administrator appointment dashboard with confirmation, cancellation, filters and CSV export.
- Appointment history preserved after cancellation; future cancelled slots can be booked again.
- Role-aware navigation and responsive Thymeleaf pages.

## Technology

| Area | Implementation |
| --- | --- |
| Runtime | Java 26, Spring Boot 4.1.0 |
| Web | Spring MVC, Thymeleaf, CSS and JavaScript; locally bundled Bootstrap 5.3.3 on the booking page |
| Security | Spring Security, session authentication, BCrypt, CSRF protection |
| Persistence | Spring Data JPA, H2, Flyway |
| Build and tests | Maven Wrapper, JUnit, Spring integration tests |
| Local email | Mailpit, optional for the password recovery demo |

## Run locally

The supported demo workflow uses **Windows, PowerShell 7 and JDK 26**. Set `JAVA_HOME` to your JDK installation. Maven is downloaded by the included wrapper; the first build requires Internet access.

```powershell
git clone https://github.com/JosiasCisneros/citas-hospitalarias.git
cd citas-hospitalarias
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.1' # Adjust to your installation.

# Build the application. This command deliberately skips tests.
Push-Location Citas
.\mvnw.cmd -B -ntp -DskipTests package
Pop-Location

# Start the persistent local demo.
.\scripts\ejecutar-demo-local.ps1 -Puerto 18086
```

Open **http://127.0.0.1:18086/login** after Spring Boot finishes starting. Keep the terminal open while using the application. Stop it with **Ctrl+C**, or run this from another terminal in the repository root:

```powershell
.\scripts\detener-demo-local.ps1 -Puerto 18086
```

The launcher asks you to choose the initial administrator password: at least 12 characters and at most 72 UTF-8 bytes. Sign in as **`admin-demo@example.test`** with the password you chose. No password is shipped in the repository. On later starts, the prompt does **not** reset an existing account's password.

The demo starts with an administrator, not a populated hospital dataset. Use fictitious data only:

1. As administrator, create a doctor and future availability.
2. Sign out and register a patient with an address ending in `@example.test` and a fictional nine-digit national ID.
3. Select a future slot, review the appointment and submit the booking.
4. As administrator, confirm the pending appointment.
5. As patient, inspect its status and cancel before its start time. Check that the appointment remains in history and the future slot becomes available again.

### Data and startup troubleshooting

The launcher stores H2 data in `datos-locales/demo/`, outside Maven's build directory. Records survive restarts. This directory, mailbox contents and build outputs are excluded from Git.

- Reuse the same port and data directory when restarting. The browser cannot open the application while its process is stopped.
- Stop the application before rebuilding its JAR. Rebuild after changing source files to see the new version.
- Run only one application instance per data directory.
- Starting without the `persistencia-local` profile uses an **in-memory** database instead. Use the launcher above for a persistent demo.
- If an older database has an incompatible schema, preserve it. Do not bypass Flyway or delete it to force startup. For a separate fresh demo, pass `-DirectorioDatos 'datos-locales/demo-nueva'`.

More startup and backup details: [Local configuration](docs/CONFIGURACION_LOCAL.md) (Spanish).

### Password recovery demo

After building, start the local mailbox and launch the app with recovery enabled:

```powershell
.\scripts\buzon-local.ps1 -Smtp 1025 -Web 8025
.\scripts\ejecutar-demo-local.ps1 -Puerto 18086 -RecuperacionLocal -Smtp 1025
```

Stop any existing app instance before launching this one. Open **http://127.0.0.1:8025** to read the captured messages. This profile accepts only recipient addresses ending in `@example.test`; it does not deliver mail to the Internet. The mailbox script downloads a pinned Mailpit binary and verifies its checksum.

Stop the application as described above, then stop the mailbox with:

```powershell
.\scripts\buzon-local.ps1 -Accion detener
```

See [Local password recovery](docs/RECUPERACION_LOCAL.md) for the walkthrough.

## Architecture and decisions

The application is a single Spring MVC service. Controllers handle HTTP requests, form objects limit writable input, services implement business rules and transaction boundaries, and repositories access JPA entities. Thymeleaf renders the interface; a separate REST API or SPA is not required for the current workflows.

```text
Citas/
  src/main/java/       Controllers, forms, services, security, repositories and entities
  src/main/resources/  Templates, static assets, configuration and Flyway migration
  src/test/            Business-rule, authorization, persistence and concurrency tests
scripts/               Local launchers and verification helpers
docs/                  Configuration and technical notes
```

The relational model connects users, doctors, availability, appointments and password-reset tokens. The persistent profile applies Flyway migrations and uses Hibernate schema validation rather than automatically modifying tables.

Booking transactions use an ordered database-locking protocol and recheck availability under those locks. Both doctor conflicts and overlapping appointments for the same patient are considered. A cancelled appointment is retained as history, not reactivated. Availability with associated appointments cannot be edited or deleted.

Role and ownership checks run on the server. Password reset tokens are stored as hashes, expire and are consumed atomically. Effective email/password changes and account deactivation invalidate outstanding tokens and sessions; other sessions are rejected on their next authenticated request. CSV exports neutralize formula prefixes.

H2 was retained to make the local demonstration easy to run while preserving relational constraints and transactions. Concurrency evidence applies to the tested H2 setup, not automatically to other databases or deployments.

Technical notes (Spanish): [Appointment integrity](docs/INTEGRIDAD_AGENDA.md) · [Sessions and identity](docs/SESIONES_E_IDENTIDAD.md).

## Verification

Run the Java suite and package the application from the repository root:

```powershell
.\scripts\verificar.ps1
```

Tests cover registration and permissions, recovery, session invalidation, appointment rules, concurrent transactions, CSV handling and persistence across JVM restarts. They use isolated databases, not the local demo's records.

Optional JavaScript checks require Node.js and no npm packages:

```powershell
node scripts/comprobar-dialogos-agenda.mjs
node scripts/comprobar-fragmento-recuperacion.mjs
```

These JavaScript checks simulate the DOM; they do not replace real browser, keyboard or accessibility testing. Python HTTP helpers are also included for targeted development checks. Automated checks were used throughout development; no hosted CI workflow is configured yet.

## Scope and limitations

This is an academic portfolio application, not a production hospital system. It models outpatient scheduling with fixed 30-minute appointments and the `America/Costa_Rica` time zone; it does not model surgery duration, clinical records or billing.

The demonstrated deployment is a single local instance using H2. Production hosting, external email delivery, large-scale load testing and comprehensive accessibility/mobile verification are outside the completed scope.

## Project origin

Developed from a university Java project and improved by **Josias Cisneros**. AI tools assisted with analysis, implementation and verification. Scope decisions and manual walkthrough feedback were provided by the author.
