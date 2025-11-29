package io.figchain.client;

import io.figchain.avro.model.Fig;
import io.figchain.avro.model.FigFamily;

import java.util.Optional;

/**
 * Selects the correct fig from a fig family based on the evaluation context.
 */
public interface RolloutEvaluator {

    /**
     * Selects the correct fig from a fig family.
     *
     * @param figFamily the fig family
     * @param context the evaluation context
     * @return an optional containing the selected fig, or an empty optional if no fig is selected
     */
    Optional<Fig> evaluate(FigFamily figFamily, EvaluationContext context);
}
