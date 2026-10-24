# Contributing to CampusPulse

Thank you for your interest in contributing to **CampusPulse — Smart Campus Complaint Management System**. We welcome contributions from developers, security researchers, and students.

To maintain code quality, security, and reproducibility, please adhere to the following guidelines.

---

## Code of Conduct

All contributors are expected to treat others with respect and professionalism. Constructive feedback and respectful collaboration are paramount.

---

## Development Workflow

### 1. Fork and Clone
```bash
# Clone your fork
git clone https://github.com/<your-username>/CampusPulse.git
cd CampusPulse
```

### 2. Branching Strategy
Create a dedicated feature or bugfix branch branching off `main`:
```bash
git checkout -b feature/issue-description
# or
git checkout -b fix/issue-description
```

### 3. Prerequisites
- **Java 21** or later (JDK 21+)
- **Maven** (use the provided `./mvnw` wrapper)
- **Python 3.10+** (for the AI microservice)
- **MySQL 8.0+** (for local application execution)

### 4. Local Environment Setup
Refer to the [README.md](file:///README.md) for full setup instructions:
1. Configure your local database and credentials via environment variables (or copy `.env.example` to `.env` locally).
2. Set up the Python virtual environment and run the model training script:
   ```bash
   cd ai-service
   python3 -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   python -m training.train
   ```
3. Start the Python AI service:
   ```bash
   uvicorn app.main:app --host 127.0.0.1 --port 8000
   ```
4. Start Spring Boot via Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```

---

## Testing Guidelines

Before committing or submitting a pull request, all test suites must pass cleanly.

### Java Tests (Spring Boot)
The Java test suite uses an in-memory H2 database and does not require MySQL or the AI service to be running:
```bash
./mvnw test
```
*Current test suite: 529 automated tests.*

### Python Tests (AI Service)
Execute tests using `pytest` inside the Python virtual environment:
```bash
cd ai-service
source .venv/bin/activate
pytest tests -v
```
*Current test suite: 16 automated tests.*

---

## Security & Secrets Policy

**NEVER commit real credentials, secrets, or sensitive personal data:**
- Do not commit `.env` files containing real passwords, database secrets, or SMTP credentials.
- Ensure all passwords and API tokens are read through environment variables with safe defaults.
- Database passwords and admin credentials in `.env.example` must remain placeholder values.
- Verify that newly added files comply with [`.gitignore`](file:///.gitignore).

---

## Code Style & Formatting

- **Java**: Follow standard Java naming conventions and clean architecture principles. Maintain separation of concerns between Controllers, Services, Repositories, and Entities.
- **Thymeleaf & HTML**: Ensure all templates use semantic HTML5 and proper CSRF token inclusions on all state-altering forms.
- **CSS**: Follow the existing SaaS-style design system defined in `src/main/resources/static/css/style.css`.
- **Python**: Follow PEP 8 style conventions and type annotations with Pydantic schemas.

---

## Submitting Pull Requests

1. Commit changes with clear, descriptive commit messages:
   ```bash
   git commit -m "feat(complaints): add residential location privacy masking"
   ```
2. Verify that there are no trailing whitespace errors:
   ```bash
   git diff --check
   ```
3. Push to your feature branch and open a Pull Request against `main`.
4. Include a clear summary of what changed, why it changed, and how it was tested in your PR description.
