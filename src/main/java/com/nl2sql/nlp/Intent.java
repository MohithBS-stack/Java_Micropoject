package com.nl2sql.nlp;

/**
 * Represents the linguistic intent of a natural-language database question.
 */
public enum Intent {
    FIND_REAGENTS("Retrieve chemical reagents or mixing agents used in a test"),
    FIND_OBSERVATION("Retrieve expected observations, results, or precipitate outcomes"),
    FIND_PROCEDURE("Retrieve test procedures, instructions, or operational steps"),
    FIND_TEST("Retrieve tests or test definitions matching criteria"),
    COUNT_RECORDS("Retrieve counts or quantities of database records"),
    UNKNOWN("Unrecognized or unsupported question intent");

    private final String description;

    Intent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
