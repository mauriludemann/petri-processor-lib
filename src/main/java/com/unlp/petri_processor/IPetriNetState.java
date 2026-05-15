package com.unlp.petri_processor;

public interface IPetriNetState {

    void save(PetriNetSnapshot snapshot);

    PetriNetSnapshot load();
}
