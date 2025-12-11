package io.figchain.client;

import io.figchain.avro.model.Condition;
import io.figchain.avro.model.Fig;
import io.figchain.avro.model.FigFamily;
import io.figchain.avro.model.Operator;
import io.figchain.avro.model.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates rollout rules defined in the FigFamily to select the appropriate Fig version.
 */
public class RuleBasedRolloutEvaluator implements RolloutEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedRolloutEvaluator.class);

    @Override
    public Optional<Fig> evaluate(FigFamily figFamily, EvaluationContext context) {
        // 1. Evaluate Rules
        if (figFamily.getRules() != null) {
            for (Rule rule : figFamily.getRules()) {
                if (matches(rule, context)) {
                    return findFigVersion(figFamily, rule.getTargetVersion());
                }
            }
        }

        // 2. Fallback to Default
        if (figFamily.getDefaultVersion() != null) {
            return findFigVersion(figFamily, figFamily.getDefaultVersion());
        }

        // 3. Absolute Fallback: Return the first available fig (usually the latest)
        if (figFamily.getFigs() != null && !figFamily.getFigs().isEmpty()) {
            return Optional.of(figFamily.getFigs().get(0));
        }

        return Optional.empty();
    }

    private boolean matches(Rule rule, EvaluationContext context) {
        if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
            return true;
        }
        for (Condition condition : rule.getConditions()) {
            if (!evaluateCondition(condition, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateCondition(Condition condition, EvaluationContext context) {
        String varName = condition.getVariable().toString(); // Ensure varName is String
        String contextValue = context.get(varName);

        if (contextValue == null) {
            return false;
        }

        Operator op = condition.getOperator();
        List<? extends CharSequence> rawValues = condition.getValues(); // Use CharSequence
        String ruleValue = (rawValues != null && !rawValues.isEmpty()) ? rawValues.get(0).toString() : null; // Convert to String

        switch (op) {
            case EQUALS:
                return contextValue.equals(ruleValue);
            case NOT_EQUALS:
                return !contextValue.equals(ruleValue);
            case IN:
                return rawValues != null && rawValues.stream().anyMatch(cv -> contextValue.equals(cv.toString())); // Stream and convert
            case NOT_IN:
                return rawValues != null && rawValues.stream().noneMatch(cv -> contextValue.equals(cv.toString())); // Stream and convert
            case CONTAINS:
                return ruleValue != null && contextValue.contains(ruleValue);
            case GREATER_THAN:
                return ruleValue != null && contextValue.compareTo(ruleValue) > 0;
            case LESS_THAN:
                return ruleValue != null && contextValue.compareTo(ruleValue) < 0;
            case SPLIT:
                if (ruleValue == null) return false;
                try {
                    int threshold = Integer.parseInt(ruleValue);
                    int bucket = getBucket(contextValue);
                    return bucket < threshold;
                } catch (NumberFormatException e) {
                    log.warn("Invalid split value: {}", ruleValue);
                    return false;
                }
            default:
                return false;
        }
    }

    /**
     * FNV-1a hash implementation for deterministic bucketing.
     */
    private int getBucket(String key) {
        int hash = 0x811c9dc5;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b;
            hash *= 0x01000193;
        }
        return Math.abs(hash % 100);
    }

    private Optional<Fig> findFigVersion(FigFamily family, Object version) {
        if (family.getFigs() == null) return Optional.empty();
        String versionStr = version.toString();
        return family.getFigs().stream()
            .filter(f -> f.getVersion().toString().equals(versionStr))
            .findFirst();
    }
}
