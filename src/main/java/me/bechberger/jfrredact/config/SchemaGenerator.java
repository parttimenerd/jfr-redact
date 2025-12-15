package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates JSON Schema for the RedactionConfig class.
 * This schema can be used for validation and IDE support.
 */
public class SchemaGenerator {

    /**
     * Generate JSON Schema for RedactionConfig
     */
    public static JsonNode generateSchema() {
        JacksonModule module = new JacksonModule();
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON)
            .with(module)
            .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
            .with(Option.VALUES_FROM_CONSTANT_FIELDS)
            .with(Option.FLATTENED_ENUMS_FROM_TOSTRING);

        SchemaGeneratorConfig config = configBuilder.build();
        com.github.victools.jsonschema.generator.SchemaGenerator generator =
            new com.github.victools.jsonschema.generator.SchemaGenerator(config);

        return generator.generateSchema(RedactionConfig.class);
    }
}