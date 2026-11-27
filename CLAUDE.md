# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An interactive Java 17 console client for the Prime Lending merchant REST APIs, built as a plain Java app (not Spring) with Jackson as its only runtime dependency. It is a port of `merchant_cli.py` and can be compiled to a GraalVM native executable.

## Commands

Use the Maven wrapper (`./mvnw` on bash, `mvnw.cmd` on Windows). There is no linter configured.

```bash
./mvnw clean package                         # build shaded jar + run tests -> target/lending-cli-1.0.0-SNAPSHOT.jar
java -jar target/lending-cli-1.0.0-SNAPSHOT.jar
./mvnw test                                  # all tests (JUnit 5)
./mvnw test -Dtest=OperationsTest#requestIdHasPrefixAndFixedLength   # single test
./mvnw -Pnative -DskipTests package          # native image -> target/lending-cli(.exe); needs GraalVM as JAVA_HOME
build-windows.cmd [--package-only]           # Windows: native exe (as now-lending.exe) + VC++ DLLs + config + packaging/windows/* -> dist\PrimeLendingCLI.zip
```

The native build on Windows must run from the "x64 Native Tools Command Prompt for VS". GraalVM does not cross-compile.

Running the app needs `config/merchants.json` (copy `config/merchants.example.json` and fill in the `CHANGE_ME` values). It looks for the config in this order: `-Dlending.config=<file>`, then the `LENDING_CONFIG` env var, then `config/merchants.json` in the current directory, then `config/merchants.json` next to the executable or jar (`Config.appDir()`). Clients double-click the exe, so `Main` also runs `chcp 65001` on Windows and waits for Enter before closing on a startup error.

## Architecture

Everything is in `com.primebank.lending`, with one class per concern:

- `Main`: merchant selection, then loan type (`retail` / `sme`), then the menu loop. Each time a merchant is selected it creates a new `ApiClient` and `Operations`, so carried-over state is reset when the user switches merchants. The SME menu has no operations yet.
- `Operations`: the 8 Retail operations. Each one prompts for fields (showing defaults), builds an `ObjectNode` payload, and POSTs it. It stores values from responses and inputs (`lastAccountNumber`, `lastAnswerResponseId`, `lastInvoiceNumber`, `lastLoanId`, amounts) so later steps can offer them as defaults. Endpoint paths are constants at the top of the class.
- `ApiClient`: fetches the token (form POST to `/user/get-token`), builds the merchant headers (`orgCode`, `clientAuth` = base64 `clientId:clientSecret` when not set explicitly, `token`, optional `Authorization: Bearer`), and POSTs JSON. On a 401 it refreshes the token and retries once. On a network failure it returns `null`, and callers check for that.
- `Config`: parses `merchants.json` into a `baseUrl`, a `showRequestPayload` flag and a list of `Merchant` objects (`auth`, `defaults`, `answerCategories`, `supportedLoanTypes`). `Merchant.auth(...)` and `Merchant.def(...)` return `null` for missing or blank values. The shared `Config.JSON` mapper keeps BigDecimals in plain notation, so amounts serialize exactly.
- `Prompt`: console input. On EOF it throws `Prompt.EndOfInput`, and `Main` catches that to exit cleanly.

All merchant-specific behavior (credentials, request defaults, the `requestIdPrefix` for the 10-character `requestId`, and credit-assessment answers) comes from config, not code. To add a merchant, add a config entry. No Java changes are needed.

## Rules

- **`merchant_cli.py` is the behavioral reference.** Endpoint paths, payload field names, headers, the token retry, the request ID format and the defaults all mirror it. Check against it when you change an operation. It is untracked on purpose and must stay that way.
- **Secrets:** `merchant_cli.py` contains plaintext UAT credentials, and `config/merchants.json` (git-ignored) holds the real config. Never `git add` either file, and never bundle the config into the jar or native binary.
- `.gitattributes` keeps `mvnw` on LF and `*.cmd` on CRLF line endings. Don't change their line endings.
- For native-image compatibility, avoid adding reflection-heavy libraries or dynamic class loading. The build uses `--no-fallback`.
