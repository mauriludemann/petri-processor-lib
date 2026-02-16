package com.unlp.petri_processor;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class PetriNetConfig {

   private List<List<Integer>> matrizIPlus;

   private List<List<Integer>> matrizIMinus;

   private List<Integer> initialMarking;

   private List<TimedTransitionConfig> timedTransitions;

   private Map<String, Integer> mapPlacesToIndex;

   private Map<String, Integer> mapTransitionsToIndex;

}
