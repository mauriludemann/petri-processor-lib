package com.unlp.petri_processor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import com.unlp.petri_processor.exceptions.PetriMonitorException;

public class PetriMonitor {

    private final PetriNet petriNet;
    private final Semaphore mutex;
    private final ConcurrentHashMap<String, Semaphore> uuidConditionVariables;

    /**
     * Set de claves (transición:uuid) que indica intención de dormir.
     * Se marca ANTES de soltar el mutex en goToSleep, y se consulta en
     * signalNextOrReleaseMutex (que también tiene el mutex).
     *
     * Resuelve el race condition entre mutex.release() y sem.acquire():
     * sin este set, un hilo que soltó el mutex pero no llegó a sem.acquire()
     * no es visible para hasQueuedThreads(), y el wakeup se pierde.
     */
    private final Set<String> waitingThreads = ConcurrentHashMap.newKeySet();

    public PetriMonitor() {
        this(new InMemoryPetriNetState());
    }

    public PetriMonitor(IPetriNetState state) {
        this.petriNet = new PetriNet(state);
        this.mutex = new Semaphore(1, true);
        this.uuidConditionVariables = new ConcurrentHashMap<>();
    }

    public void fire(PetriTransition petriTransition) throws PetriMonitorException {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            System.out.println("InterruptedException when acquiring mutex");
            throw new PetriMonitorException("Unexpected error while acquiring mutex");
        }
        boolean fired = false;
        while (!fired) {
            if (petriNet.isEnabled(petriTransition)) {
                if (petriNet.fireTransition(petriTransition)) {
                    signalNextOrReleaseMutex();
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
                goToSleep(petriTransition.getTransitionId(), petriTransition.getUuid());
            }
        }
        // Limpia la variable de condición y el flag de este (transición, uuid)
        String key = condKey(petriTransition.getTransitionId(), petriTransition.getUuid());
        waitingThreads.remove(key);
        uuidConditionVariables.remove(key);
    }

    private String condKey(int transition, String uuid) {
        return transition + ":" + (uuid != null ? uuid : "null");
    }

    /**
     * Duerme al hilo en una variable de condición específica para su (transición, UUID).
     *
     * Registra la intención de dormir en waitingThreads ANTES de soltar el mutex.
     * Así, signalNextOrReleaseMutex (que tiene el mutex) siempre ve al hilo como
     * "esperando" incluso si aún no llegó a sem.acquire().
     *
     * Si el permit se libera antes de que el hilo llame sem.acquire(),
     * el permit se acumula en el semáforo y acquire() retorna inmediatamente.
     */
    private void goToSleep(int transition, String uuid) {
        String key = condKey(transition, uuid);
        Semaphore sem = uuidConditionVariables.computeIfAbsent(key, k -> new Semaphore(0));
        waitingThreads.add(key);  // Marcar intención ANTES de soltar mutex
        try {
            mutex.release();
            sem.acquire();
            // Al despertar: el hilo tiene el mutex (pasado por el señalizador).
            // Limpia el flag de espera para que signalNext no lo considere de nuevo.
            waitingThreads.remove(key);
        } catch (InterruptedException e) {
            waitingThreads.remove(key);
            System.out.printf("I was interrupted when sleeping in transition %s, uuid %s%n", transition, uuid);
        }
    }

    /**
     * Busca un hilo esperando cuya transición esté habilitada para su UUID.
     * Usa waitingThreads (intención de dormir) en lugar de hasQueuedThreads()
     * para evitar el race condition entre mutex.release() y sem.acquire().
     *
     * Si encuentra uno, libera su semáforo (pasándole la sección crítica).
     * Si no encuentra ninguno, libera el mutex.
     */
    private void signalNextOrReleaseMutex() {
        for (String key : waitingThreads) {
            Semaphore sem = uuidConditionVariables.get(key);
            if (sem != null) {
                String[] parts = key.split(":", 2);
                int transitionId = Integer.parseInt(parts[0]);
                String uuid = "null".equals(parts[1]) ? null : parts[1];
                PetriTransition pt = new PetriTransition(transitionId, uuid);
                if (petriNet.isEnabled(pt)) {
                    waitingThreads.remove(key);  // Limpiar antes de despertar
                    sem.release();
                    return;  // Mutex pasado al hilo despertado
                }
            }
        }
        mutex.release();
    }
}
