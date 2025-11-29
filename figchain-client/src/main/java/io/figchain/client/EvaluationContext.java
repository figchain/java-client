package io.figchain.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains information for making rollout decisions.
 * <p>
 * This context holds key-value pairs that represent the environment, user, or request
 * attributes (e.g., "region", "userId", "betaUser"). These attributes are used by the
 * {@link RolloutEvaluator} to determine which version of a configuration to return.
 * </p>
 */
public class EvaluationContext {
    private final Map<String, String> attributes;

    public EvaluationContext() {
        this.attributes = new HashMap<>();
    }

    public EvaluationContext(Map<String, String> attributes) {
        this.attributes = new HashMap<>(attributes);
    }

    public EvaluationContext put(String key, String value) {
        this.attributes.put(key, value);
        return this;
    }

    public String get(String key) {
        return this.attributes.get(key);
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Merges this context with another context.
     * <p>
     * The attributes from the {@code other} context will take precedence over attributes
     * in this context if keys collide.
     * </p>
     *
     * @param other the context to merge with
     * @return a new EvaluationContext containing the merged attributes
     */
    public EvaluationContext merge(EvaluationContext other) {
        if (other == null) {
            return new EvaluationContext(this.attributes);
        }
        Map<String, String> merged = new HashMap<>(this.attributes);
        merged.putAll(other.attributes);
        return new EvaluationContext(merged);
    }

    @Override
    public String toString() {
        return "EvaluationContext{" +
                "attributes=" + attributes +
                '}';
    }
}
