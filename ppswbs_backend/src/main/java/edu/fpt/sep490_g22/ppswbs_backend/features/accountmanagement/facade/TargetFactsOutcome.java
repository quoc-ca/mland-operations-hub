package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

public sealed interface TargetFactsOutcome {
    record Found(TargetAccountFacts facts) implements TargetFactsOutcome {}
    record NotFound() implements TargetFactsOutcome {}
    record Unavailable() implements TargetFactsOutcome {}

    static TargetFactsOutcome found(TargetAccountFacts facts) {
        return new Found(facts);
    }

    static TargetFactsOutcome notFound() {
        return new NotFound();
    }

    static TargetFactsOutcome unavailable() {
        return new Unavailable();
    }
}

