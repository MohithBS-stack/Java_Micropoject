package com.nl2sql.domain.chemistry;

import com.nl2sql.generator.DefaultSQLQueryGenerator;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.generator.SQLQueryGenerator;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.nlp.ParsedQuestion;

/**
 * Domain-specific Query Mapper for Chemistry laboratory demonstration intents.
 * Implements SQLQueryGenerator interface to produce parameterized queries validated against SchemaMetadata.
 */
public class ChemistryQueryMapper implements SQLQueryGenerator {

    private final DefaultSQLQueryGenerator delegateGenerator;

    public ChemistryQueryMapper() {
        this.delegateGenerator = new DefaultSQLQueryGenerator();
    }

    @Override
    public GeneratedQuery generate(ParsedQuestion parsedQuestion, SchemaMetadata schemaMetadata) {
        return delegateGenerator.generate(parsedQuestion, schemaMetadata);
    }
}
