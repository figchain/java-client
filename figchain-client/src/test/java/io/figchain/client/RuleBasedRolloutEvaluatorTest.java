package io.figchain.client;

import io.figchain.avro.model.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedRolloutEvaluatorTest {

    private final RuleBasedRolloutEvaluator evaluator = new RuleBasedRolloutEvaluator();

    @Test
    void testEvaluate_MatchFirstRule() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        Fig fig1 = createFig(v1, "config-v1");
        Fig fig2 = createFig(v2, "config-v2");

        Condition condition = Condition.newBuilder()
                .setVariable("user_type")
                .setOperator(Operator.EQUALS)
                .setValues(Collections.singletonList("beta"))
                .build();

        Rule rule = Rule.newBuilder()
                .setConditions(Collections.singletonList(condition))
                .setTargetVersion(v2)
                .build();

        FigFamily family = FigFamily.newBuilder()
                .setDefinition(createDefinition())
                .setFigs(Arrays.asList(fig1, fig2))
                .setRules(Collections.singletonList(rule))
                .setDefaultVersion(v1)
                .build();

        EvaluationContext context = new EvaluationContext().put("user_type", "beta");

        Optional<Fig> result = evaluator.evaluate(family, context);

        assertTrue(result.isPresent());
        assertEquals(v2.toString(), result.get().getVersion().toString());
    }

    @Test
    void testEvaluate_NoMatch_ReturnsDefault() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        Fig fig1 = createFig(v1, "config-v1");
        Fig fig2 = createFig(v2, "config-v2");

        Condition condition = Condition.newBuilder()
                .setVariable("user_type")
                .setOperator(Operator.EQUALS)
                .setValues(Collections.singletonList("beta"))
                .build();

        Rule rule = Rule.newBuilder()
                .setConditions(Collections.singletonList(condition))
                .setTargetVersion(v2)
                .build();

        FigFamily family = FigFamily.newBuilder()
                .setDefinition(createDefinition())
                .setFigs(Arrays.asList(fig1, fig2))
                .setRules(Collections.singletonList(rule))
                .setDefaultVersion(v1)
                .build();

        EvaluationContext context = new EvaluationContext().put("user_type", "regular");

        Optional<Fig> result = evaluator.evaluate(family, context);

        assertTrue(result.isPresent());
        assertEquals(v1.toString(), result.get().getVersion().toString());
    }

    @Test
    void testEvaluate_MatchMultipleConditions() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        Fig fig1 = createFig(v1, "v1");
        Fig fig2 = createFig(v2, "v2");

        Condition cond1 = Condition.newBuilder()
                .setVariable("region")
                .setOperator(Operator.EQUALS)
                .setValues(Collections.singletonList("US"))
                .build();
        
        Condition cond2 = Condition.newBuilder()
                .setVariable("tier")
                .setOperator(Operator.IN)
                .setValues(Arrays.asList("gold", "platinum"))
                .build();

        Rule rule = Rule.newBuilder()
                .setConditions(Arrays.asList(cond1, cond2))
                .setTargetVersion(v2)
                .build();

        FigFamily family = FigFamily.newBuilder()
                .setDefinition(createDefinition())
                .setFigs(Arrays.asList(fig1, fig2))
                .setRules(Collections.singletonList(rule))
                .setDefaultVersion(v1)
                .build();

        // Match
        EvaluationContext ctxMatch = new EvaluationContext()
                .put("region", "US")
                .put("tier", "gold");
        assertEquals(v2.toString(), evaluator.evaluate(family, ctxMatch).get().getVersion().toString());

        // No Match (Region wrong)
        EvaluationContext ctxFail1 = new EvaluationContext()
                .put("region", "EU")
                .put("tier", "gold");
        assertEquals(v1.toString(), evaluator.evaluate(family, ctxFail1).get().getVersion().toString());

        // No Match (Tier wrong)
        EvaluationContext ctxFail2 = new EvaluationContext()
                .put("region", "US")
                .put("tier", "silver");
        assertEquals(v1.toString(), evaluator.evaluate(family, ctxFail2).get().getVersion().toString());
    }

    @Test
    void testEvaluate_Split() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();

        Fig fig1 = createFig(v1, "default");
        Fig fig2 = createFig(v2, "canary");

        // Rule: 100% split -> Everyone matches v2
        Condition condAll = Condition.newBuilder()
                .setVariable("user_id")
                .setOperator(Operator.SPLIT)
                .setValues(Collections.singletonList("100"))
                .build();

        Rule ruleAll = Rule.newBuilder()
                .setConditions(Collections.singletonList(condAll))
                .setTargetVersion(v2)
                .build();

        FigFamily familyAll = FigFamily.newBuilder()
                .setDefinition(createDefinition())
                .setFigs(Arrays.asList(fig1, fig2))
                .setRules(Collections.singletonList(ruleAll))
                .setDefaultVersion(v1)
                .build();

        assertEquals(v2.toString(), evaluator.evaluate(familyAll, new EvaluationContext().put("user_id", "any")).get().getVersion().toString());
        
        // Rule: 0% split -> Everyone matches v1 (Default)
        Condition condNone = Condition.newBuilder()
                .setVariable("user_id")
                .setOperator(Operator.SPLIT)
                .setValues(Collections.singletonList("0"))
                .build();

        Rule ruleNone = Rule.newBuilder()
                .setConditions(Collections.singletonList(condNone))
                .setTargetVersion(v2)
                .build();

         FigFamily familyNone = FigFamily.newBuilder()
                .setDefinition(createDefinition())
                .setFigs(Arrays.asList(fig1, fig2))
                .setRules(Collections.singletonList(ruleNone))
                .setDefaultVersion(v1)
                .build();
         
         assertEquals(v1.toString(), evaluator.evaluate(familyNone, new EvaluationContext().put("user_id", "any")).get().getVersion().toString());
    }
    
    @Test
    void testEvaluate_NumericComparison() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        
        Fig fig1 = createFig(v1, "v1");
        Fig fig2 = createFig(v2, "v2");
        
        Condition condGt = Condition.newBuilder()
            .setVariable("age")
            .setOperator(Operator.GREATER_THAN)
            .setValues(Collections.singletonList("18"))
            .build();
            
        Rule rule = Rule.newBuilder()
            .setConditions(Collections.singletonList(condGt))
            .setTargetVersion(v2)
            .build();
            
        FigFamily family = FigFamily.newBuilder()
            .setDefinition(createDefinition())
            .setFigs(Arrays.asList(fig1, fig2))
            .setRules(Collections.singletonList(rule))
            .setDefaultVersion(v1)
            .build();
            
        // "21" > "18" lexicographically -> Match
        assertEquals(v2.toString(), evaluator.evaluate(family, new EvaluationContext().put("age", "21")).get().getVersion().toString());
        
        // "10" < "18" lexicographically -> No Match
        assertEquals(v1.toString(), evaluator.evaluate(family, new EvaluationContext().put("age", "10")).get().getVersion().toString());
    }

    private Fig createFig(UUID version, String content) {
        return Fig.newBuilder()
                .setFigId(UUID.randomUUID())
                .setVersion(version)
                .setPayload(ByteBuffer.wrap(content.getBytes()))
                .build();
    }

    private FigDefinition createDefinition() {
        return FigDefinition.newBuilder()
                .setNamespace("ns")
                .setKey("key")
                .setFigId(UUID.randomUUID())
                .setSchemaUri("http://schema")
                .setSchemaVersion("1")
                .setCreatedAt(Instant.now())
                .setUpdatedAt(Instant.now())
                .build();
    }
}
