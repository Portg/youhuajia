package com.youhua.engine.scoring.pmml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PmmlStrategyRegistry Tests")
class PmmlStrategyRegistryTest {

    private PmmlStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        StrategyMetadataLoader loader = new StrategyMetadataLoader();
        registry = new PmmlStrategyRegistry(loader);
        registry.init();
    }

    @Test
    @DisplayName("should_load_default_strategy_from_classpath")
    void should_load_default_strategy_from_classpath() {
        assertThat(registry.isInitialized()).isTrue();
        assertThat(registry.getAllStrategies()).containsKey(UserSegment.DEFAULT);
    }

    @Test
    @DisplayName("should_return_default_when_segment_not_found")
    void should_return_default_when_segment_not_found() {
        PmmlStrategyRegistry.StrategyEntry entry = registry.getStrategy(UserSegment.HIGH_DEBT);
        // Falls back to DEFAULT since high-debt.pmml doesn't exist
        assertThat(entry).isNotNull();
        assertThat(entry.evaluator()).isNotNull();
    }

    @Test
    @DisplayName("should_have_metadata_for_default_strategy")
    void should_have_metadata_for_default_strategy() {
        PmmlStrategyRegistry.StrategyEntry entry = registry.getStrategy(UserSegment.DEFAULT);
        assertThat(entry.metadata()).isNotNull();
        assertThat(entry.metadata().getStrategyName()).isEqualTo("稳健策略");
        assertThat(entry.metadata().getVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("should_force_reload_without_error")
    void should_force_reload_without_error() {
        registry.forceReload();
        assertThat(registry.getAllStrategies()).containsKey(UserSegment.DEFAULT);
    }

    @Test
    @DisplayName("should_verify_evaluator_on_load")
    void should_verify_evaluator_on_load() {
        PmmlStrategyRegistry.StrategyEntry entry = registry.getStrategy(UserSegment.DEFAULT);
        // If we got here without exception, verify() passed during init
        assertThat(entry.evaluator()).isNotNull();
    }
}
