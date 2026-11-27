# Prime Lending CLI

A small Java command-line client for the Prime Lending merchant REST APIs. It is a port of
`merchant_cli.py` and can be built as a single native executable with GraalVM, so users don't need
Java or Python installed.

It is plain Java (not Spring Boot). It has one runtime dependency (Jackson) and five classes.

## Project layout

```
pom.xml                              Maven build (jar + "native" profile)
mvnw, mvnw.cmd, .mvn/                Maven wrapper, so Maven doesn't need to be installed
config/merchants.example.json        Config template, safe to commit
config/merchants.json                Your real config with credentials (git-ignored)
src/main/java/com/primebank/lending/
  Main.java        merchant / loan-type selection and menus
  Operations.java  the 8 Retail operations (payloads, defaults, values carried between steps)
  ApiClient.java   token fetch, headers, POST with one retry on 401, response printing
  Config.java      loads merchants.json
  Prompt.java      console input
build-windows.cmd                    builds the client zip (dist\PrimeLendingCLI.zip)
packaging/windows/                   install.cmd / uninstall.cmd / setup.ps1 / client README.txt shipped in the zip
.gitattributes                       keeps mvnw on LF line endings so it runs on Linux/macOS
```

`merchant_cli.py`, the original Python CLI, is kept locally as the behavioral reference. It is not
committed because it contains credentials (see [Secrets](#secrets)).

## Requirements

| Purpose            | Needs                                                              |
|--------------------|--------------------------------------------------------------------|
| Run from source    | JDK 17+                                                            |
| Build native image | [GraalVM](https://www.graalvm.org/downloads/) for JDK 17+ (includes `native-image`) |
| Windows native     | Also Visual Studio Build Tools ("Desktop development with C++")    |

## Configuration

All merchant settings live in one JSON file: base URL, credentials, request defaults and the
credit-assessment answers.

```bash
cp config/merchants.example.json config/merchants.json      # Windows: copy config\merchants.example.json config\merchants.json
```

Then replace every `CHANGE_ME` in the file.

The CLI looks for the config file in this order:

1. `-Dlending.config=/path/to/merchants.json` (JVM only)
2. The `LENDING_CONFIG` environment variable (works for the native binary too)
3. `config/merchants.json`, relative to the current directory
4. `config/merchants.json` next to the executable (or jar), so a double-clicked exe or a shortcut
   finds its config wherever it starts

Useful settings:

- `showRequestPayload: true` prints every request URL and JSON body before it is sent.
- To add a merchant, add an entry to `merchants`. It appears in the menu automatically.
- `supportedLoanTypes` (`retail` / `sme`) controls which loan types a merchant offers.
- `requestIdPrefix` sets the start of the 10-character `requestId` (`mrcn` for Hishabee, `wgr` for Wegro).

## Run from source

```bash
./mvnw clean package                                   # Windows: mvnw.cmd clean package
java -jar target/lending-cli-1.0.0-SNAPSHOT.jar
```

## Build the native executable

Use a terminal where `JAVA_HOME` points to GraalVM. On Windows, use the
**x64 Native Tools Command Prompt for VS**.

```bash
./mvnw -Pnative -DskipTests package                    # Windows: mvnw.cmd -Pnative -DskipTests package
```

The output is `target/lending-cli` (Linux/macOS) or `target\lending-cli.exe` (Windows).
GraalVM doesn't cross-compile, so build on each OS/architecture you need to support.

## Package for a client (Windows)

The client needs nothing installed: no Java, no Python, no runtime. From the
**x64 Native Tools Command Prompt for VS** (with `JAVA_HOME` set to GraalVM), run:

```bat
build-windows.cmd
```

This builds the native exe and produces `dist\PrimeLendingCLI.zip`:

```
PrimeLendingCLI\
  lending.exe               the CLI (target\lending-cli.exe, renamed so the command is "lending")
  install.cmd               adds the CLI to the user's PATH (no admin needed)
  uninstall.cmd             removes it again
  setup.ps1                 used by install.cmd / uninstall.cmd
  README.txt                client user guide (packaging/windows/README.txt)
  vcruntime140.dll          Visual C++ runtime, shipped next to the exe because a clean Windows may lack it
  vcruntime140_1.dll
  config\merchants.json     copied from your config\merchants.json (contains credentials)
```

`build-windows.cmd --package-only` repackages the existing `target\lending-cli.exe` without
rebuilding it.

The client unzips it and either:

- double-clicks `now-lending.exe` to run it once, or
- double-clicks `install.cmd` once, then types `now-lending` in any new Command Prompt or PowerShell
  window. `install.cmd` copies the folder to `%LOCALAPPDATA%\Programs\PrimeLendingCLI` and adds
  that folder to the user `PATH`. To update, run `install.cmd` from the new zip. To remove, run
  `uninstall.cmd` from that folder.

The exe switches the console to UTF-8
for Bangla text by itself. If something goes wrong at startup (for example, a missing config), the
window stays open until they press Enter. The exe is built with `-march=compatibility`, so it runs
on older x86-64 CPUs too.

The zip contains credentials, so send it through a secure channel. For other OSes, build the native
image on that OS (`./mvnw -Pnative package`) and ship the binary with `config/merchants.json` next
to it in the same way.

## Menu

```
Select Merchant -> Select Loan Type -> menu

Retail:  1 Customer Binding     5 Submit Invoice
         2 Questions List       6 Loan Information
         3 Credit Assessment    7 Settlement Request
         4 Check Eligibility    8 Settlement
         9 Switch Merchant      0 Exit
SME:     9 Switch Merchant      0 Exit
```

Each prompt shows a default value. Press Enter to keep it. Values returned or entered in one step
are offered as defaults in later steps: account number, answer response ID, invoice number,
amount and loan ID.

## Secrets

- `config/merchants.json` is git-ignored. Never commit it or build it into the binary.
- `merchant_cli.py` contains the UAT credentials in plain text, so it is not committed. Strip its
  secrets before you ever `git add` it. The credentials have already been shared in plain text, so
  treat them as exposed and arrange rotation.

## Migration rule

`merchant_cli.py` (local only, not in the repository) is the behavioral reference. Endpoint paths,
payload field names, headers, the token retry, request ID format and defaults in the Java code
mirror it. Check against it when changing an operation.
