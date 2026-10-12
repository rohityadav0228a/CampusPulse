# CampusPulse

> **Institutional Grievance Redressal and Operations Intelligence Platform**
> Built with **Java 21**, **Spring Boot 4**, **Spring Security**, **Thymeleaf**, **MySQL**, and a **Python FastAPI** microservice for local Machine Learning classification.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Key Features](#key-features)
- [Technology Stack and Architecture](#technology-stack-and-architecture)
- [Prerequisites](#prerequisites)
- [MySQL Database Setup](#mysql-database-setup)
- [Environment Configuration](#environment-configuration)
  - [Critical Note on `.env` Files in Spring Boot](#critical-note-on-env-files-in-spring-boot)
  - [How to Set Variables on macOS and Linux](#how-to-set-variables-on-macos-and-linux)
  - [Configuring Variables in Deployment Environments](#configuring-variables-in-deployment-environments)
  - [Complete Reference of Configuration Variables](#complete-reference-of-configuration-variables)
- [Running the Application](#running-the-application)
  - [1. Setting Up and Running the Python AI Microservice](#1-setting-up-and-running-the-python-ai-microservice)
  - [2. Model Training and Artifact Recreation](#2-model-training-and-artifact-recreation)
  - [3. Running the Spring Boot Application](#3-running-the-spring-boot-application)
- [Running Test Suites](#running-test-suites)
  - [Java Test Suite](#java-test-suite)
  - [Python Test Suite](#python-test-suite)
- [API Endpoints](#api-endpoints)
  - [Spring Boot Web Endpoints](#spring-boot-web-endpoints)
  - [Python FastAPI Endpoints](#python-fastapi-endpoints)
- [Security Notes and Production Readiness](#security-notes-and-production-readiness)
- [Known Limitations and Operational Roadmap](#known-limitations-and-operational-roadmap)

---

## Project Overview

CampusPulse is an institutional grievance redressal and workflow tracking platform designed for universities and higher education campuses. It provides end-to-end management for student complaints, administrative case workflows, service level agreement (SLA) monitoring, automated notifications with retry resilience, and executive operations analytics.

The application pairs a server-side Spring Boot core with a lightweight, 100% local Python NLP microservice to provide instant advisory category suggestions during complaint submission while maintaining total data privacy and incurring zero external cloud API expenses.

---

## Key Features

1. **Student Complaint Tracking & Submission**
   - Public submission form with client and server-side validation.
   - Unique reference ID generation (`CP-YYYY-XXXXXXXX`) and self-service tracking interface.
   - Optional student notification email for real-time lifecycle updates.

2. **Advisory AI Category Classification**
   - Non-intrusive category recommendations powered by a local TF-IDF + Logistic Regression model.
   - 100% privacy-preserving and offline; no data is transmitted to third-party cloud APIs.
   - Strictly advisory: students retain complete autonomy to apply, modify, or ignore the suggested category.

3. **Administrative Case Management**
   - Role-based administration portal (`/admin/complaints`) protected by Spring Security.
   - Governed status transitions (`SUBMITTED` &rarr; `UNDER_REVIEW` &rarr; `IN_PROGRESS` &rarr; `RESOLVED` / `REJECTED`).
   - Immutable audit trail recording state transitions, acting administrators, and internal remarks.

4. **Resilient Notification Outbox System**
   - Transactional Outbox pattern: email notification records are persisted within the same database transaction as complaint mutations.
   - Background retry scheduler with exponential backoff and pessimistic/claim locking.
   - Transient vs. permanent failure classification (prevents indefinite retries for invalid addresses).

5. **SLA Deadlines & Automated Escalation**
   - Configurable target response deadlines (default: 24h) and resolution deadlines (default: 72h).
   - Real-time SLA indicators (`ON_SCHEDULE`, `APPROACHING_DEADLINE`, `RESPONSE_OVERDUE`, `RESOLUTION_OVERDUE`).
   - Scheduled escalation worker with automated administrative alert generation and audit logging.

6. **Operations Analytics & AI Insights**
   - Real-time aggregation of resolution rates, category distributions, and department backlogs.
   - Historical trend analysis comparing intake volumes against previous calendar periods.
   - Heuristic AI Insights engine identifying department bottlenecks and generating prioritized recommendations.

---

## Technology Stack and Architecture

### Technology Stack

| Layer | Technologies |
|---|---|
| **Backend Core** | Java 21, Spring Boot 4.0.8, Spring Data JPA, Spring Security, Spring Mail |
| **Frontend UI** | Thymeleaf Template Engine, Modern Semantic HTML5, Custom Vanilla CSS |
| **Relational Database** | MySQL 8.0+ (Production/Local), H2 In-Memory (Automated Testing) |
| **AI / ML Microservice** | Python 3.10+, FastAPI, Uvicorn, scikit-learn, pandas, joblib, Pydantic |
| **Build & Test Tools** | Apache Maven (via `mvnw`), pytest, httpx, Starlette TestClient |

### High-Level Architecture

```
                       +-----------------------------------+
                       |         Student / Admin           |
                       |       Web Browser Clients         |
                       +-----------------+-----------------+
                                         |
                                         | HTTP / HTTPS (8080)
                                         v
       +-----------------------------------------------------------------+
       |                  Spring Boot Core Application                   |
       |                                                                 |
       |  [Spring Security]  [Thymeleaf UI]     [Complaint Controllers]  |
       |  [Audit Service]    [SLA Engine]       [Notification Service]   |
       |                                                                 |
       |  [Schedulers: Notification Retry & SLA Escalation Background]   |
       +--------------+----------------------------------+---------------+
                      |                                  |
    JDBC / SQL (3306) |                REST / JSON (8000)| (Advisory NLP)
                      v                                  v
        +----------------------------+     +----------------------------+
        |       MySQL Database       |     |   Python FastAPI Service   |
        |                            |     |                            |
        | - complaints               |     | - TF-IDF + Logistic Reg    |
        | - complaint_status_history |     | - 6 Supported Categories   |
        | - notification_outbox      |     | - Completely Local & Free  |
        | - escalation_audit         |     +----------------------------+
        +----------------------------+
```

---

## Prerequisites

Ensure the following tools are installed on your host system:

- **Java Development Kit (JDK)**: Version 21 or higher (`java -version`)
- **Python**: Version 3.10 or higher (`python3 --version`)
- **MySQL Server**: Version 8.0 or higher running locally or in a container
- **Git**: Installed and configured

---

## MySQL Database Setup

1. Open your MySQL client (CLI, MySQL Workbench, or DBeaver) as an administrative user:
   ```bash
   mysql -u root -p
   ```

2. Create the application database and dedicated service user:
   ```sql
   CREATE DATABASE campuspulse_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

   -- Generate a strong password for the application database user
   CREATE USER 'campuspulse_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_STRONG_DB_PASSWORD';

   GRANT ALL PRIVILEGES ON campuspulse_db.* TO 'campuspulse_app'@'localhost';

   FLUSH PRIVILEGES;
   ```

3. **Schema Generation**:
   CampusPulse utilizes Spring Data JPA with `spring.jpa.hibernate.ddl-auto=update`. When the Spring Boot application starts, Hibernate will automatically create or update all tables, indexes, and foreign keys. No manual SQL migration files are required for initial setup.

---

## Environment Configuration

### Critical Note on `.env` Files in Spring Boot

> **IMPORTANT**: Standard Spring Boot applications **do not automatically load `.env` files** into the Java process environment. Copying [`.env.example`](file:///.env.example) to `.env` alone will **not** pass variables to Spring Boot.

Configuration properties in Spring Boot resolve values from:
1. OS environment variables (`export KEY=VALUE`)
2. JVM system properties (`-DKEY=VALUE`)
3. Spring command-line arguments (`--KEY=VALUE`)
4. Property files (`application.properties`)

### How to Set Variables on macOS and Linux

#### Option 1: Direct Shell Export (Recommended for Development)
Generate strong credentials (e.g. using `openssl rand -base64 24`) and export them in your terminal session before launching Maven:
```bash
# Generate strong passwords
# openssl rand -base64 24

export DB_USERNAME="campuspulse_app"
export DB_PASSWORD="REPLACE_WITH_STRONG_DB_PASSWORD"
export ADMIN_PASSWORD="REPLACE_WITH_STRONG_ADMIN_PASSWORD"

./mvnw spring-boot:run
```

#### Option 2: Sourcing a Local `.env` File (macOS zsh / bash)
If you prefer maintaining a local `.env` file (which is git-ignored):
```bash
cp .env.example .env
# Edit .env and insert your strong passwords

# In macOS zsh (or bash), source and export variables into the current session:
set -a
source .env
set +a

# Run the application
./mvnw spring-boot:run
```

#### Option 3: Command-Line Arguments via Maven
You can pass overrides directly to the Spring Boot runner:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--DB_PASSWORD=REPLACE_WITH_STRONG_DB_PASSWORD --ADMIN_PASSWORD=REPLACE_WITH_STRONG_ADMIN_PASSWORD"
```

### Configuring Variables in Deployment Environments

- **Docker**:
  ```bash
  docker run --env-file .env -p 8080:8080 campuspulse:latest
  ```
- **Docker Compose**:
  ```yaml
  services:
    app:
      image: campuspulse:latest
      env_file: .env
      ports:
        - "8080:8080"
  ```
- **systemd Service Unit** (`/etc/systemd/system/campuspulse.service`):
  ```ini
  [Service]
  EnvironmentFile=/etc/campuspulse/campuspulse.env
  ExecStart=/usr/bin/java -jar /opt/campuspulse/campuspulse.jar
  ```
- **Kubernetes**: Inject via a Secret using `envFrom`:
  ```yaml
  envFrom:
    - secretRef:
        name: campuspulse-secrets
  ```

---

### Complete Reference of Configuration Variables

Every variable listed below is bound in [application.properties](file:///Users/rohityadav/Projects/CampusPulse/src/main/resources/application.properties) and mapped to active Java configuration classes:

| Environment Variable | Real Property in `application.properties` | Default Value | Description |
|---|---|---|---|
| `DB_URL` | `spring.datasource.url` | `jdbc:mysql://localhost:3306/campuspulse_db` | MySQL JDBC connection string |
| `DB_USERNAME` | `spring.datasource.username` | `campuspulse_app` | MySQL username |
| `DB_PASSWORD` | `spring.datasource.password` | `""` (empty) | MySQL user password |
| `ADMIN_USERNAME` | `campuspulse.admin.username` | `admin` | Admin portal username |
| `ADMIN_PASSWORD` | `campuspulse.admin.password` | `""` (empty) | Admin plaintext password (auto-hashed with BCrypt on startup). If neither password nor hash is supplied, an ephemeral UUID password is auto-generated and logged to console. |
| `ADMIN_PASSWORD_HASH` | `campuspulse.admin.password-hash` | `""` (empty) | Pre-computed BCrypt hash (takes priority over `ADMIN_PASSWORD`) |
| `SMTP_HOST` | `spring.mail.host` | `localhost` | Outgoing SMTP server hostname |
| `SMTP_PORT` | `spring.mail.port` | `587` | SMTP port |
| `SMTP_USERNAME` | `spring.mail.username` | `""` (empty) | SMTP authentication username |
| `SMTP_PASSWORD` | `spring.mail.password` | `""` (empty) | SMTP authentication password or API key |
| `SMTP_AUTH` | `spring.mail.properties.mail.smtp.auth` | `false` | Enable SMTP authentication |
| `SMTP_STARTTLS` | `spring.mail.properties.mail.smtp.starttls.enable` | `true` | Enable STARTTLS encryption |
| `SMTP_STARTTLS_REQUIRED` | `spring.mail.properties.mail.smtp.starttls.required` | `false` | Require STARTTLS encryption |
| `MAIL_FROM` | `campuspulse.mail.from` | `noreply@campuspulse.edu` | Sender email address displayed to recipients |
| `APP_BASE_URL` | `campuspulse.app.base-url` | `http://localhost:8080` | Public base URL included in tracking email links |
| `NOTIFICATION_RETRY_ENABLED` | `campuspulse.notifications.retry.enabled` | `true` | Master switch for background email retry worker |
| `NOTIFICATION_RETRY_INTERVAL_MS` | `campuspulse.notifications.retry.interval-ms` | `60000` | Retry scheduler polling interval (ms) |
| `NOTIFICATION_RETRY_INITIAL_DELAY_MS` | `campuspulse.notifications.retry.initial-delay-ms` | `10000` | Delay before first retry check on startup (ms) |
| `NOTIFICATION_RETRY_MAX_ATTEMPTS` | `campuspulse.notifications.retry.max-attempts` | `3` | Max attempts before marking `PERMANENTLY_FAILED` |
| `NOTIFICATION_RETRY_INITIAL_BACKOFF_MS` | `campuspulse.notifications.retry.initial-backoff-ms` | `30000` | Initial retry backoff interval (ms) |
| `NOTIFICATION_RETRY_BACKOFF_MULTIPLIER` | `campuspulse.notifications.retry.backoff-multiplier` | `2.0` | Exponential backoff multiplier |
| `NOTIFICATION_RETRY_MAX_BACKOFF_MS` | `campuspulse.notifications.retry.max-backoff-ms` | `300000` | Maximum backoff delay ceiling (ms) |
| `NOTIFICATION_RETRY_STALE_THRESHOLD_MS` | `campuspulse.notifications.retry.stale-threshold-ms` | `300000` | Threshold to reclaim stuck in-flight locks (ms) |
| `AI_SERVICE_URL` | `campuspulse.ai.service-url` | `http://127.0.0.1:8000` | HTTP URL of the Python FastAPI microservice |
| `AI_CONNECT_TIMEOUT_MS` | `campuspulse.ai.connect-timeout-ms` | `2000` | HTTP connection timeout to AI service (ms) |
| `AI_READ_TIMEOUT_MS` | `campuspulse.ai.read-timeout-ms` | `3000` | HTTP read/response timeout to AI service (ms) |
| `SLA_RESPONSE_HOURS` | `campuspulse.sla.default-response-hours` | `24` | Target response deadline window (hours) |
| `SLA_RESOLUTION_HOURS` | `campuspulse.sla.default-resolution-hours` | `72` | Target resolution deadline window (hours) |
| `SLA_APPROACHING_HOURS` | `campuspulse.sla.approaching-deadline-hours` | `6` | Warning window before deadline expiration (hours) |
| `SLA_TIMEZONE` | `campuspulse.sla.timezone` | `Asia/Kolkata` | Timezone ID for SLA deadline calculations |
| `ADMIN_ESCALATION_EMAIL` | `campuspulse.sla.admin-escalation-email` | `admin@campuspulse.edu` | Recipient email address for SLA escalation alerts |
| `SLA_ESCALATION_ENABLED` | `campuspulse.sla.escalation.enabled` | `true` | Master switch for automated SLA escalation scheduler |
| `SLA_ESCALATION_INTERVAL_MS` | `campuspulse.sla.escalation.interval-ms` | `60000` | Escalation evaluation polling interval (ms) |
| `SLA_ESCALATION_INITIAL_DELAY_MS` | `campuspulse.sla.escalation.initial-delay-ms` | `15000` | Delay before first SLA check on startup (ms) |

---

## Running the Application

### 1. Setting Up and Running the Python AI Microservice

The AI microservice classifies complaint text into one of 6 institutional categories.

1. Navigate to the `ai-service` directory:
   ```bash
   cd ai-service
   ```

2. Create and activate a Python virtual environment:
   ```bash
   python3 -m venv .venv
   source .venv/bin/activate   # On Windows: .venv\Scripts\activate
   ```

3. Install dependencies:
   ```bash
   pip install --upgrade pip
   pip install -r requirements.txt
   ```

### 2. Model Training and Artifact Recreation

> **Note on Model Artifacts**: Binary model artifacts (`*.joblib`) are excluded from Git version control via [`.gitignore`](file:///.gitignore) to maintain a lightweight repository and adhere to reproducibility best practices.

To generate or recreate the classification model from the verified demonstration dataset:
```bash
python -m training.train
```

This offline script will:
- Validate the dataset in `data/demo_complaints.csv`.
- Train a `TfidfVectorizer` and `LogisticRegression` pipeline.
- Export the fitted artifact to `models/complaint_classifier.joblib`.
- Record validation metrics and metadata in `models/model_metadata.json`.

Now start the FastAPI microservice with Uvicorn:
```bash
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Verify the service is operational by checking the health endpoint:
```bash
curl http://127.0.0.1:8000/health
```
Expected response:
```json
{
  "status": "ok",
  "service": "CampusPulse AI Classifier",
  "model_loaded": true,
  "can_predict": true,
  "model_version": "1.0.0",
  "supported_categories": [
    "Academic Affairs",
    "Hostel & Residence",
    "Infrastructure & Labs",
    "Library & Study Spaces",
    "Canteen & Dining",
    "Transit & Campus Grounds"
  ]
}
```

Interactive Swagger API documentation is available at `http://127.0.0.1:8000/docs`.

---

### 3. Running the Spring Boot Application

In a separate terminal window at the repository root:

1. Export your database credentials and admin password:
   ```bash
   export DB_USERNAME="campuspulse_app"
   export DB_PASSWORD="REPLACE_WITH_STRONG_DB_PASSWORD"
   export ADMIN_PASSWORD="REPLACE_WITH_STRONG_ADMIN_PASSWORD"
   ```

2. Launch the application using the Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```

3. Open your browser and navigate to:
   - **Student Portal**: [http://localhost:8080](http://localhost:8080)
   - **Submit Complaint**: [http://localhost:8080/complaints/new](http://localhost:8080/complaints/new)
   - **Track Complaint**: [http://localhost:8080/complaints/track](http://localhost:8080/complaints/track)
   - **Admin Login**: [http://localhost:8080/login](http://localhost:8080/login) (Default user: `admin` / Password: as configured in `ADMIN_PASSWORD`)
   - **Admin Management**: [http://localhost:8080/admin/complaints](http://localhost:8080/admin/complaints)
   - **Admin Analytics**: [http://localhost:8080/admin/analytics](http://localhost:8080/admin/analytics)
   - **Admin AI Insights**: [http://localhost:8080/admin/insights](http://localhost:8080/admin/insights)

---

## Running Test Suites

CampusPulse includes comprehensive automated test coverage for both Java and Python components.

### Java Test Suite

The Java test suite uses an embedded in-memory H2 database with mock notification and AI clients; **it does not require a running MySQL or Python service**.

Execute tests using Maven:
```bash
./mvnw test
```

**Test Coverage Summary (150 tests, 0 failures, 0 errors)**:
- `ComplaintRepositoryTest`: Database persistence, JPA queries, and optimistic locking.
- `ComplaintServiceTest`: Status transition validation, audit generation, and reference formatting.
- `AdminComplaintControllerTest`: Administrative security and status mutations.
- `AdminSecurityTest`: Role-based route protection, CSRF verification, and authentication.
- `AiClassifierServiceTest`: Microservice communication, fallback handling, timeouts, and category whitelisting.
- `AnalyticsServiceTest`: Aggregation math, period comparisons, and metric calculations.
- `InsightsServiceTest`: Heuristic health calculations and operational recommendation engine.
- `NotificationServiceTest`: Transactional Outbox mechanics, exponential backoff, and error taxonomy.
- `NotificationRetrySchedulerTest`: Background polling and concurrency contention handling.
- `SlaDeadlinesTest` & `SlaEscalationServiceTest`: SLA calculation, timezone compliance, and automated escalation logs.

### Python Test Suite

Execute Python tests inside `ai-service`:
```bash
cd ai-service
source .venv/bin/activate
pytest tests -v
```

**Test Coverage Summary (16 tests, 0 failures)**:
- `test_health.py`: Healthcheck response with loaded and unloaded models.
- `test_predict.py`: Category prediction correctness, confidence boundaries, 503 fallback when unloaded, schema validation.
- `test_training.py`: CSV dataset schema verification, category distribution constraints, and end-to-end model generation.

---

## API Endpoints

### Spring Boot Web Endpoints

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/` | Public | Landing page with platform overview |
| `GET` | `/complaints` | Public | Recent public complaint registry |
| `GET` | `/complaints/new` | Public | Student complaint submission form |
| `POST` | `/complaints` | Public | Submit new complaint |
| `POST` | `/complaints/suggest-category` | Public | Advisory AI classification request (`{"title": "...", "description": "..."}`) |
| `GET` | `/complaints/track` | Public | Track complaint by reference ID |
| `GET` | `/complaints/success/{referenceId}` | Public | Post-submission confirmation page |
| `GET` | `/login` | Public | Administrator login view |
| `POST` | `/logout` | Authenticated | Terminate session and invalidate cookies |
| `GET` | `/admin/complaints` | Role `ADMIN` | Administrative complaint management queue |
| `GET` | `/admin/complaints/{id}` | Role `ADMIN` | Detailed complaint view with audit history & SLA timeline |
| `POST` | `/admin/complaints/{id}/status` | Role `ADMIN` | Update complaint status and record audit remark |
| `GET` | `/admin/analytics` | Role `ADMIN` | Operational metrics, charts, and departmental breakdowns |
| `GET` | `/admin/insights` | Role `ADMIN` | AI Insights, bottleneck analysis, and SLA performance |

### Python FastAPI Endpoints

| Method | Endpoint | Purpose | Request Body / Parameters |
|---|---|---|---|
| `GET` | `/` | Service root and metadata | None |
| `GET` | `/health` | Health and readiness check | None |
| `POST` | `/predict` | Classify complaint text | `{"text": "Grievance text string..."}` |
| `GET` | `/docs` | OpenAPI Swagger Documentation | None |
| `GET` | `/redoc` | OpenAPI ReDoc Documentation | None |

---

## Security Notes and Production Readiness

A comprehensive production-readiness audit was performed on the codebase. Below is an overview of verified security implementations and operational considerations:

### Verified Security Measures
1. **Password Hashing & Random Fallback**:
   - Administrative credentials are encrypted using `BCryptPasswordEncoder`.
   - If no admin password is provided via environment variables, Spring Boot generates a cryptographically random UUID on startup, preventing default-credential attacks.
2. **Role-Based Access Control**:
   - All `/admin/**` endpoints strictly require `ROLE_ADMIN`. Unauthorized requests are redirected to `/login`.
3. **Cross-Site Request Forgery (CSRF)**:
   - CSRF protection is active across all mutating routes (`POST /complaints`, `POST /complaints/suggest-category`, `POST /admin/complaints/{id}/status`).
   - Thymeleaf templates embed CSRF meta tags and include tokens in AJAX requests.
4. **Data Privacy & Redaction**:
   - Sensitive student identifiers (student email, detailed grievance text) are omitted from public audit feeds and escalation logs.
   - Email logging masks recipient addresses (`s***@college.edu`).
5. **CORS Restrictions**:
   - The FastAPI AI microservice restricts CORS strictly to local origins (`http://127.0.0.1:8080`, `http://localhost:8080`).

---

## Known Limitations and Operational Roadmap

To clearly distinguish verified automated test behavior from unverified external deployment environments:

1. **Email Delivery (SMTP)**:
   - **Verified**: Outbox staging, exponential backoff computation, failure detection, and mock SMTP dispatch.
   - **Unverified**: Real email inbox delivery over an external network. For production use, configure an authentic SMTP provider (e.g., AWS SES, SendGrid, Mailgun) and ensure SPF/DKIM/DMARC records are configured.
2. **Machine Learning Model Calibration**:
   - **Verified**: Deterministic offline training, TF-IDF feature extraction, and fast local inference with mock fallback.
   - **Limitation**: The model is trained on a small synthetic demo dataset (60 samples across 6 classes). Prediction probabilities represent heuristic estimates rather than fully calibrated real-world probabilities. For production deployment, retrain on at least 1,000+ real institutional grievances.
3. **Single-Node Schedulers vs. Distributed Locks**:
   - The notification retry and SLA escalation workers currently utilize in-memory and database row-level locking. In a clustered multi-node deployment, adopt a distributed locking library (e.g., [ShedLock](https://github.com/lukas-krecan/ShedLock)) to prevent concurrent execution across horizontal instances.
4. **SSL/TLS Termination**:
   - The application binds to HTTP. Production deployments must place CampusPulse behind an HTTPS reverse proxy (such as Nginx, Caddy, or AWS ALB) with valid TLS certificates and security headers (HSTS, CSP).
