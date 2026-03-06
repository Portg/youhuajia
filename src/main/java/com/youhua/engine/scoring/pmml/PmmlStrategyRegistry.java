package com.youhua.engine.scoring.pmml;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages all loaded PMML strategy evaluators.
 * Supports hot-reload by polling file MD5 changes every 60 seconds.
 * Thread-safe via ReentrantReadWriteLock.
 */
@Slf4j
@Component
public class PmmlStrategyRegistry {

    @Value("${youhua.engine.scoring.strategy-dir:}")
    private String strategyDir;

    private final StrategyMetadataLoader metadataLoader;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Segment → loaded strategy entry */
    private final Map<UserSegment, StrategyEntry> strategies = new ConcurrentHashMap<>();

    /** File path → MD5 hash for change detection */
    private final Map<String, String> fileHashes = new ConcurrentHashMap<>();

    @Getter
    private volatile boolean initialized = false;

    public PmmlStrategyRegistry(StrategyMetadataLoader metadataLoader) {
        this.metadataLoader = metadataLoader;
    }

    /**
     * A loaded strategy: evaluator + metadata.
     */
    public record StrategyEntry(
            Evaluator evaluator,
            StrategyMetadata metadata,
            UserSegment segment
    ) {}

    @PostConstruct
    public void init() {
        loadAllStrategies();
        initialized = true;
    }

    /**
     * Get the strategy entry for a segment, falling back to DEFAULT.
     */
    public StrategyEntry getStrategy(UserSegment segment) {
        lock.readLock().lock();
        try {
            StrategyEntry entry = strategies.get(segment);
            if (entry != null) {
                return entry;
            }
            // Fallback to DEFAULT
            entry = strategies.get(UserSegment.DEFAULT);
            if (entry != null) {
                log.debug("Segment {} not found, falling back to DEFAULT", segment);
                return entry;
            }
            throw new BizException(ErrorCode.STRATEGY_NOT_FOUND,
                    "No strategy found for segment " + segment + " and no DEFAULT fallback");
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all loaded strategies.
     */
    public Map<UserSegment, StrategyEntry> getAllStrategies() {
        lock.readLock().lock();
        try {
            return new LinkedHashMap<>(strategies);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Force reload all strategies.
     */
    public void forceReload() {
        log.info("Force reloading all PMML strategies");
        fileHashes.clear();
        loadAllStrategies();
    }

    /**
     * Scheduled task: check for PMML file changes every 60 seconds.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void checkForChanges() {
        if (strategyDir == null || strategyDir.isBlank()) {
            // Using classpath resources — check not supported for hot-reload
            return;
        }

        Path dir = Path.of(strategyDir);
        if (!Files.isDirectory(dir)) {
            return;
        }

        try {
            boolean changed = false;
            for (UserSegment segment : UserSegment.values()) {
                Path pmmlPath = dir.resolve(segment.getPmmlFileName() + ".pmml");
                if (Files.exists(pmmlPath)) {
                    String currentHash = computeMd5(Files.readAllBytes(pmmlPath));
                    String previousHash = fileHashes.get(pmmlPath.toString());
                    if (!currentHash.equals(previousHash)) {
                        log.info("PMML file changed: {}", pmmlPath);
                        changed = true;
                    }
                }
            }

            if (changed) {
                loadAllStrategies();
            }
        } catch (Exception e) {
            log.error("Error checking for PMML strategy changes", e);
        }
    }

    // ===================== Private helpers =====================

    private void loadAllStrategies() {
        lock.writeLock().lock();
        try {
            if (strategyDir != null && !strategyDir.isBlank()) {
                loadFromFileSystem(Path.of(strategyDir));
            } else {
                loadFromClasspath();
            }
            log.info("PMML strategies loaded: {}", strategies.keySet());
        } catch (Exception e) {
            log.error("Failed to load PMML strategies", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void loadFromClasspath() {
        for (UserSegment segment : UserSegment.values()) {
            String pmmlResource = "strategies/" + segment.getPmmlFileName() + ".pmml";
            String metaResource = "strategies/" + segment.getPmmlFileName() + ".meta.yml";

            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource resource = resolver.getResource("classpath:" + pmmlResource);
                if (!resource.exists()) {
                    log.debug("PMML resource not found for segment {}: {}", segment, pmmlResource);
                    continue;
                }

                Evaluator evaluator;
                try (InputStream is = resource.getInputStream()) {
                    evaluator = new LoadingModelEvaluatorBuilder().load(is).build();
                    evaluator.verify();
                }

                StrategyMetadata metadata = metadataLoader.loadFromClasspath(metaResource);

                strategies.put(segment, new StrategyEntry(evaluator, metadata, segment));
                log.info("Loaded PMML strategy from classpath: segment={}, version={}",
                        segment, metadata != null ? metadata.getVersion() : "unknown");
            } catch (Exception e) {
                log.warn("Failed to load PMML strategy for segment {} from classpath", segment, e);
            }
        }
    }

    private void loadFromFileSystem(Path dir) {
        if (!Files.isDirectory(dir)) {
            log.warn("Strategy directory does not exist: {}", dir);
            loadFromClasspath();
            return;
        }

        for (UserSegment segment : UserSegment.values()) {
            Path pmmlPath = dir.resolve(segment.getPmmlFileName() + ".pmml");
            Path metaPath = dir.resolve(segment.getPmmlFileName() + ".meta.yml");

            if (!Files.exists(pmmlPath)) {
                log.debug("PMML file not found for segment {}: {}", segment, pmmlPath);
                continue;
            }

            try {
                Evaluator evaluator;
                try (InputStream is = Files.newInputStream(pmmlPath)) {
                    evaluator = new LoadingModelEvaluatorBuilder().load(is).build();
                    evaluator.verify();
                }

                byte[] pmmlBytes = Files.readAllBytes(pmmlPath);
                fileHashes.put(pmmlPath.toString(), computeMd5(pmmlBytes));

                StrategyMetadata metadata = Files.exists(metaPath)
                        ? metadataLoader.load(metaPath)
                        : null;

                strategies.put(segment, new StrategyEntry(evaluator, metadata, segment));
                log.info("Loaded PMML strategy from filesystem: segment={}, path={}, version={}",
                        segment, pmmlPath, metadata != null ? metadata.getVersion() : "unknown");
            } catch (Exception e) {
                log.warn("Failed to load PMML strategy for segment {} from {}", segment, pmmlPath, e);
            }
        }
    }

    private String computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }
}
