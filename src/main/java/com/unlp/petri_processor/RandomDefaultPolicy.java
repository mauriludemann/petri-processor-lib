package com.unlp.petri_processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomDefaultPolicy implements IPetriPolicy {

    public RandomDefaultPolicy() {
    }

    @Override
    public Integer whoIsNext(List<Boolean> enabledTransitions) {
        List<Integer> enabledIndex = new ArrayList<>();
        for (int i=0; i<enabledTransitions.size(); i++) {
            if (enabledTransitions.get(i)) {
                enabledIndex.add(i);
            }
        }
        Random random = new Random();
        return enabledIndex.get(random.nextInt(enabledIndex.size()));
    }
}