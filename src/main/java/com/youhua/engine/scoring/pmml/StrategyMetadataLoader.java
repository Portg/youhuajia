package com.youhua.engine.scoring.pmml;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and parses .meta.yml strategy metadata files.
 */
@Slf4j
@Component
public class StrategyMetadataLoader {

    private final ObjectMapper yamlMapper;

    public StrategyMetadataLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Load metadata from a .meta.yml file path.
     */
    public StrategyMetadata load(Path metaPath) {
        try (InputStream is = Files.newInputStream(metaPath)) {
            return yamlMapper.readValue(is, StrategyMetadata.class);
        } catch (Exception e) {
            log.error("Failed to load strategy metadata from {}", metaPath, e);
            return null;
        }
    }

    /**
     * Load metadata from classpath resource.
     */
    public StrategyMetadata loadFromClasspath(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Metadata resource not found: {}", resourcePath);
                return null;
            }
            return yamlMapper.readValue(is, StrategyMetadata.class);
        } catch (Exception e) {
            log.error("Failed to load strategy metadata from classpath: {}", resourcePath, e);
            return null;
        }
    }
}
