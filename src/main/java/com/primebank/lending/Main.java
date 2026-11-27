package com.primebank.lending;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Entry point: merchant / loan-type selection and the menus. */
public final class Main {

    private static final String[][] LOAN_TYPES = {{"Retail", "retail"}, {"SME", "sme"}};

    private final Config config;
    private final Prompt prompt = new Prompt();

    private Main(Config config) {
        this.config = config;
    }

    public static void main(String[] args) {
        // UTF-8 output so Bangla text in responses prints cleanly.
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        useUtf8Console();

        Path path = Config.resolvePath();
        Config config;
        try {
            config = Config.load(path);
        } catch (IOException e) {
            String reason = e instanceof java.nio.file.NoSuchFileException ? "file not found" : e.getMessage();
            System.err.println("[-] Could not load config " + path.toAbsolutePath() + ": " + reason);
            System.err.println("    Put merchants.json in a \"config\" folder next to the program,");
            System.err.println("    or set LENDING_CONFIG to the file's full path.");
            pauseBeforeClose();
            System.exit(1);
            return;
        }

        System.out.println("=== Merchant CLI Runner ===");
        try {
            new Main(config).run();
        } catch (Prompt.EndOfInput e) {
            System.out.println();
            System.out.println("Exiting...");
        } catch (RuntimeException e) {
            System.err.println("[-] Unexpected error: " + e);
            pauseBeforeClose();
            System.exit(1);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    /**
     * On Windows, switches the console to UTF-8 (chcp 65001) so Bangla text prints correctly
     * without the user running chcp first. Does nothing elsewhere or without a console.
     */
    private static void useUtf8Console() {
        if (!isWindows() || System.console() == null) {
            return;
        }
        try {
            new ProcessBuilder("cmd", "/c", "chcp 65001 >nul").inheritIO().start().waitFor();
        } catch (IOException e) {
            // Leave the code page as it is; only non-ASCII text is affected.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Keeps a double-clicked console window open so the user can read the error. */
    private static void pauseBeforeClose() {
        if (System.console() == null) {
            return;
        }
        System.err.print("Press Enter to close...");
        System.err.flush();
        try {
            System.in.read();
        } catch (IOException ignored) {
            // Closing anyway.
        }
    }

    private void run() {
        while (true) {
            Config.Merchant merchant = selectMerchant();
            String loanType = selectLoanType();
            if (!merchant.supportedLoanTypes.contains(loanType)) {
                System.out.println("[-] For " + merchant.name + " " + loanTypeName(loanType) + " is not available.");
                continue;
            }
            System.out.println("[+] Selected: " + merchant.name + " (" + loanTypeName(loanType) + ")");

            ApiClient api = new ApiClient(config, merchant);
            if (!api.login()) {
                continue;
            }
            Operations ops = new Operations(merchant, api, prompt);
            boolean exit = "retail".equals(loanType) ? retailMenu(merchant, ops) : smeMenu(merchant);
            if (exit) {
                return;
            }
        }
    }

    private Config.Merchant selectMerchant() {
        while (true) {
            System.out.println();
            System.out.println("=== Select Merchant ===");
            for (int i = 0; i < config.merchants.size(); i++) {
                System.out.println((i + 1) + ") " + config.merchants.get(i).name);
            }
            int choice = parseChoice(prompt.line("Enter choice [1]: "));
            if (choice >= 1 && choice <= config.merchants.size()) {
                return config.merchants.get(choice - 1);
            }
            System.out.println("[-] Invalid selection. Please enter 1-" + config.merchants.size() + ".");
        }
    }

    private String selectLoanType() {
        while (true) {
            System.out.println();
            System.out.println("=== Select Loan Type ===");
            for (int i = 0; i < LOAN_TYPES.length; i++) {
                System.out.println((i + 1) + ") " + LOAN_TYPES[i][0]);
            }
            int choice = parseChoice(prompt.line("Enter choice [1]: "));
            if (choice >= 1 && choice <= LOAN_TYPES.length) {
                return LOAN_TYPES[choice - 1][1];
            }
            System.out.println("[-] Invalid selection. Please enter 1 or 2.");
        }
    }

    /** Returns true to exit the program, false to switch merchant. */
    private boolean retailMenu(Config.Merchant merchant, Operations ops) {
        while (true) {
            System.out.println();
            System.out.println("--- " + merchant.name + " (Retail) Menu ---");
            System.out.println("1. Customer Binding");
            System.out.println("2. Questions List");
            System.out.println("3. Credit Assessment");
            System.out.println("4. Check Eligibility");
            System.out.println("5. Submit Invoice");
            System.out.println("6. Loan Information");
            System.out.println("7. Settlement Request");
            System.out.println("8. Settlement");
            System.out.println("9. Switch Merchant");
            System.out.println("0. Exit");

            switch (prompt.line("Select an option: ")) {
                case "1" -> ops.customerBinding();
                case "2" -> ops.questionsList();
                case "3" -> ops.creditAssessment();
                case "4" -> ops.checkEligibility();
                case "5" -> ops.submitInvoice();
                case "6" -> ops.loanInformation();
                case "7" -> ops.settlementRequest();
                case "8" -> ops.settlement();
                case "9" -> { return false; }
                case "0" -> { System.out.println("Exiting..."); return true; }
                default -> System.out.println("[-] Invalid option. Try again.");
            }
        }
    }

    /** SME has no operations yet: only Switch Merchant / Exit. */
    private boolean smeMenu(Config.Merchant merchant) {
        while (true) {
            System.out.println();
            System.out.println("--- " + merchant.name + " (SME) Menu ---");
            System.out.println("9. Switch Merchant");
            System.out.println("0. Exit");

            switch (prompt.line("Select an option: ")) {
                case "9" -> { return false; }
                case "0" -> { System.out.println("Exiting..."); return true; }
                default -> System.out.println("[-] Invalid option. Try again.");
            }
        }
    }

    private static int parseChoice(String raw) {
        if (raw.isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String loanTypeName(String key) {
        for (String[] type : LOAN_TYPES) {
            if (type[1].equals(key)) {
                return type[0];
            }
        }
        return key;
    }
}
