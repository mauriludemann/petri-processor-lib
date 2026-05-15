package com.unlp.petri_processor;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PetriNetSnapshot(
    int[] currentMarking,
    Map<Integer, Set<String>> uuidCurrentMarking,
    List<Boolean> enabledTransitions,
    Map<Integer, Long> timedTransitionEnablingTimes,
    Map<Integer, Map<String, Long>> timedTransitionUuidEnablingTimes
) {}
