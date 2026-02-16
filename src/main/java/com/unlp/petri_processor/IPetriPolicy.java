package com.unlp.petri_processor;

import java.util.List;

public interface IPetriPolicy {

   Integer whoIsNext(List<Boolean> enabledTransitions);
}
