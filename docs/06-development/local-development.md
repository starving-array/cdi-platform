# Local Development Guide

## Environment Requirements

- **Java Version:** Java 21 is **strictly required**. The project uses modern Java 21 features (records, switch expressions) and is baselined at this version. Do not attempt to compile or run with Java 17 or older.
- **Maven:** Use the included Maven Wrapper (`mvnw` / `mvnw.cmd`). You do not need to install Maven globally.

## Building the Project

The build is automated using Maven. To compile and run all quality gates (tests, Checkstyle, JaCoCo), run:

```bash
# On Linux/macOS
./mvnw clean test

# On Windows
.\mvnw.cmd clean test
```

## Quality Gates

The CI pipeline and local build strictly enforce the following quality gates:
1. **Unit Tests:** Must pass.
2. **Checkstyle:** Enforces `google_checks.xml` format.
3. **JaCoCo:** Tracks code coverage during the test phase.

Do not weaken these gates. Ensure your code complies before pushing.
