package com.primebank.lending;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

/** Console input helpers. End of input (Ctrl+Z / Ctrl+D) raises {@link EndOfInput}. */
final class Prompt {

    static final class EndOfInput extends RuntimeException {
        EndOfInput() {
            super(null, null, false, false);
        }
    }

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

    String line(String label) {
        System.out.print(label);
        System.out.flush();
        try {
            String value = in.readLine();
            if (value == null) {
                throw new EndOfInput();
            }
            return value.trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String withDefault(String label, String defaultValue) {
        String value = line(label + " [" + defaultValue + "]: ");
        return value.isEmpty() ? defaultValue : value;
    }
}
