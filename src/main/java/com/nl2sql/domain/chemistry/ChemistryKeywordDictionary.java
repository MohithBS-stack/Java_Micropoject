package com.nl2sql.domain.chemistry;

import java.util.List;
import java.util.Map;

/**
 * Domain-specific Keyword Dictionary for Chemistry laboratory terms.
 * Isolates chemistry vocabulary definitions from the generic NLP parsing engine loop.
 */
public class ChemistryKeywordDictionary {

    public static final Map<String, Integer> ORDINAL_MAP = Map.of(
            "first", 1, "1st", 1,
            "second", 2, "2nd", 2,
            "third", 3, "3rd", 3,
            "fourth", 4, "4th", 4,
            "fifth", 5, "5th", 5
    );

    public static final List<String> REAGENT_KEYWORDS = List.of(
            "mix", "reagent", "reagents", "add", "addition", "combine", "reactants"
    );

    public static final List<String> OBSERVATION_KEYWORDS = List.of(
            "observation", "observe", "result", "color", "precipitate", "expect", "expected"
    );

    public static final List<String> PROCEDURE_KEYWORDS = List.of(
            "procedure", "process", "instruction", "steps", "how to perform", "how do i test"
    );

    public static final List<String> TEST_KEYWORDS = List.of(
            "test", "tests", "confirmatory", "preliminary", "check"
    );

    public static final List<String> KNOWN_ANALYTES = List.of(
            "chloride", "sulfate", "carbonate", "nitrate", "lead", "iron", "copper", "zinc", "barium"
    );
}
