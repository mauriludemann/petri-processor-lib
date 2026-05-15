package com.unlp.petri_processor;

public class InMemoryPetriNetState implements IPetriNetState {

    private PetriNetSnapshot snapshot;

    @Override
    public void save(PetriNetSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public PetriNetSnapshot load() {
        return snapshot;
    }
}
