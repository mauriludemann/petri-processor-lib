package com.unlp.petri_processor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

public class PetriNet {

    private static int[][] INCIDENCE_MATRIX_PLUS;

    private static int[][] INCIDENCE_MATRIX_MINUS;

    private static int[] INITIAL_MARKING;

    private static List<TimedTransition> TIMED_TRANSITIONS;

    @Getter
    private final RandomDefaultPolicy randomDefaultPolicy;

    @Getter
    private final int[] currentMarking;

    private final Map<Integer, Set<String>> uuidCurrentMarking;

    @Getter
    private List<Boolean> enabledTransitions;

    public PetriNet() {
        this.randomDefaultPolicy = new RandomDefaultPolicy();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("petri-config.json")) {
            PetriNetConfig config = mapper.readValue(input, PetriNetConfig.class);
            INCIDENCE_MATRIX_PLUS = config.getMatrizIPlus().stream().map(a -> a.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new);
            INCIDENCE_MATRIX_MINUS = config.getMatrizIMinus().stream().map(a -> a.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new);
            INITIAL_MARKING = config.getInitialMarking().stream().mapToInt(Integer::intValue).toArray();
            TIMED_TRANSITIONS = config.getTimedTransitions().stream().map(t -> {
                if (Objects.nonNull(t.getAlpha()) && t.getAlpha() > 0) {
                    return new TimedTransition(t.getAlpha(), Long.MAX_VALUE);
                }
                return null;
            }).collect(Collectors.toList());

        } catch (IOException ex) {
            throw new RuntimeException("Failed to load Petri net configuration", ex);
        }
        currentMarking = INITIAL_MARKING;
        enabledTransitions = new ArrayList<>(Collections.nCopies(getTransitionsAmount(), Boolean.FALSE));
        saveEnabledTransitions(System.currentTimeMillis());
        uuidCurrentMarking = new HashMap<>();
        for (int i = 0; i < currentMarking.length; i++) {
            uuidCurrentMarking.put(i, new HashSet<>());
        }
    }

    public boolean fireTransition(PetriTransition petriTransition) {
        if (isTimedTransition(petriTransition.getTransitionId()) && !TIMED_TRANSITIONS.get(petriTransition.getTransitionId()).canFire(System.currentTimeMillis())) {
            return false;
        }
        Long enablingTime = System.currentTimeMillis();
        updateMarking(petriTransition);
        saveEnabledTransitions(petriTransition, enablingTime);
        return true;
    }

    public Integer getTransitionsAmount() {
        return INCIDENCE_MATRIX_PLUS[0].length;
    }

    public boolean isEnabled(PetriTransition petriTransition) {
        int t = petriTransition.getTransitionId();
        if (IntStream.range(0, currentMarking.length).anyMatch(i -> (currentMarking[i] - INCIDENCE_MATRIX_MINUS[i][t] + INCIDENCE_MATRIX_PLUS[i][t]) < 0)) {
            return false;
        }
        // Si el UUID del PetriTransition es null, se considera recurso, está habilitado.
        if (Objects.isNull(petriTransition.getUuid())) {
            return true;
        }
        int[] minusVector = new int[INCIDENCE_MATRIX_MINUS.length];
        for (int i = 0; i < INCIDENCE_MATRIX_MINUS.length; i++) {
            minusVector[i] = INCIDENCE_MATRIX_MINUS[i][t];
        }
        for (int i = 0; i < minusVector.length; i++) {
            if (minusVector[i] > 0 && !uuidCurrentMarking.get(i).contains(petriTransition.getUuid())) {
                return false;
            }
        }
        return true;
    }

    public TimedTransition getTimedTransition(Integer t) {
        return TIMED_TRANSITIONS.get(t);
    }

    private void updateMarking(PetriTransition petriTransition) {
        int t = petriTransition.getTransitionId();

        // Actualiza el marcado actual
        IntStream.range(0, currentMarking.length)
                 .forEach(p -> currentMarking[p] = currentMarking[p] + INCIDENCE_MATRIX_PLUS[p][t] - INCIDENCE_MATRIX_MINUS[p][t]);

        // Si la transición tiene UUID, actualiza el conjunto de UUIDs en el marcado actual
        if (Objects.nonNull(petriTransition.getUuid())) {
            int[] plusVector = new int[INCIDENCE_MATRIX_PLUS.length];
            for (int i = 0; i < INCIDENCE_MATRIX_PLUS.length; i++) {
                plusVector[i] = INCIDENCE_MATRIX_PLUS[i][t];
            }
            for (int i = 0; i < plusVector.length; i++) {
                if (plusVector[i] > 0 ) {
                    uuidCurrentMarking.get(i).add(petriTransition.getUuid());
                }
            }

            int[] minusVector = new int[INCIDENCE_MATRIX_MINUS.length];
            for (int i = 0; i < INCIDENCE_MATRIX_MINUS.length; i++) {
                minusVector[i] = INCIDENCE_MATRIX_MINUS[i][t];
            }
            for (int i = 0; i < minusVector.length; i++) {
                if (minusVector[i] > 0) {
                    if (!uuidCurrentMarking.get(i).remove(petriTransition.getUuid())) {
                        throw new IllegalStateException("UUID not found in current marking for transition: " + petriTransition.getUuid());
                    }
                }
            }
        }
    }

    private void saveEnabledTransitions(PetriTransition petriTransition, Long enablingTime) {
        // falta resolver aca, este vector de enabled transitions esta raro
        enabledTransitions = IntStream.range(0, getTransitionsAmount()).mapToObj((t) -> {
            if (isEnabled(new PetriTransition(t, petriTransition.getUuid()))) {
                if (isTimedTransition(t) && !enabledTransitions.get(t)) {
                    TIMED_TRANSITIONS.get(t).setEnablingTime(petriTransition.getUuid(), enablingTime);
                }
                return true;
            }
            return false;
        }).collect(Collectors.toList());
    }

    private void saveEnabledTransitions(Long enablingTime) {
        saveEnabledTransitions(new PetriTransition(0, null), enablingTime);
    }

    private boolean isTimedTransition(int t) {
        return TIMED_TRANSITIONS.get(t) != null;
    }
}