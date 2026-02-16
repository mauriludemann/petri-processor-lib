package com.unlp.petri_processor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.unlp.petri_processor.exceptions.PetriMonitorException;

public class PetriMonitor {

    private final PetriNet petriNet;
    private final Semaphore mutex;
    private final List<Semaphore> conditionVariables;

    public PetriMonitor() {
        this.petriNet = new PetriNet();
        this.mutex = new Semaphore(1, true);
        conditionVariables = new ArrayList<>();
        for (int i = 0; i < petriNet.getTransitionsAmount(); i++) conditionVariables.add(new Semaphore(0));
    }

    public void fire(PetriTransition petriTransition) throws PetriMonitorException {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            System.out.println("InterruptedException when acquiring mutex");
            throw new PetriMonitorException("Unexpected error while acquiring mutex");
        }
        boolean fired = false;
        while(!fired) {
            if (petriNet.isEnabled(petriTransition)) {
                if (petriNet.fireTransition(petriTransition)) {
                    //log.info("Fired T {} with UUID {}. Current marking: {}", petriTransition.getTransitionId(), petriTransition.getUuid(), Arrays.toString(petriNet.getCurrentMarking()));
                    //Obtiene las transiciones sensibilizadas
                    List<Boolean> enabledTransitions = petriNet.getEnabledTransitions();
                    //Obtiene las transiciones que tienen hilos durmiendo
                    List<Boolean> queuedTransitions = getConditionVariablesStatus();
                    //And lógico entre las dos listas anteriores
                    List<Boolean> transitionsToRelease = logicAnd(enabledTransitions, queuedTransitions);
                    if (transitionsToRelease.stream().anyMatch(Boolean::booleanValue)) {
                        //Obtiene de la política, la próxima transición a disparar
                        Integer nextTransitionToFire = petriNet.getRandomDefaultPolicy().whoIsNext(transitionsToRelease);
                        //Despierta de las colas de condición, al hilo de la transición que eligió la política
                        conditionVariables.get(nextTransitionToFire).release();
                    } else {
                        mutex.release();
                    }
                    fired = true;
                } else {
                    try {
                        long timeToSleep = petriNet
                              .getTimedTransition(petriTransition.getTransitionId()).getEnablingTime(petriTransition.getUuid()) +
                              petriNet.getTimedTransition(petriTransition.getTransitionId()).getAlpha() - System.currentTimeMillis();
                        mutex.release();
                        Thread.sleep(timeToSleep);
                        mutex.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                goToSleep(petriTransition.getTransitionId());
            }
        }
    }

    private void goToSleep(Integer transition) {
        try {
            //Si la transición a disparar no estaba sensibilizada, libera el mutex y se duerme en la cola de condición
            mutex.release();
            conditionVariables.get(transition).acquire();
        } catch (InterruptedException e) {
            System.out.printf("I was interrupted when sleeping in transition %s%n", transition);
        }
    }

    private List<Boolean> getConditionVariablesStatus() {
        return conditionVariables.stream().map(Semaphore::hasQueuedThreads).collect(Collectors.toList());
    }

    private List<Boolean> logicAnd(List<Boolean> list1, List<Boolean> list2) {
        return IntStream.range(0, list1.size()).mapToObj(i -> list1.get(i) && list2.get(i)).collect(Collectors.toList());
    }
}