package com.unlp.petri_processor;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PetriTransition {

   private int transitionId;

   private String uuid;
}
