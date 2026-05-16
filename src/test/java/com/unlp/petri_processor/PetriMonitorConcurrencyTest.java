package com.unlp.petri_processor;

import com.unlp.petri_processor.exceptions.PetriMonitorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de concurrencia para PetriMonitor.
 *
 * Los tests "concurrentSteps" lanzan CADA PASO de cada flujo como tarea independiente,
 * todas simultáneamente (shuffled). Un hilo que intenta T0 antes de que T24 haya
 * disparado para su UUID irá a goToSleep y deberá ser despertado por
 * signalNextOrReleaseMutex. Esto ejercita el mecanismo completo de wakeup.
 *
 * Usa virtual threads (Java 21) para evitar thread pool exhaustion: cuando un
 * virtual thread se bloquea en sem.acquire(), el platform thread subyacente
 * queda libre para ejecutar otras tareas.
 *
 * Los tests "phased" sincronizan cada paso con una barrera, creando máxima contención
 * por transición pero sin riesgo de pool exhaustion.
 *
 * Flujos derivados de las matrices de incidencia y el mapping de eventos:
 *
 *   CU1-OK (Pago exitoso):
 *     T24 RecibirEvento → T0 AuthExitosa_C1 → T31 VerificarSaldoExitoso
 *     → T15 FinalizarProcesoPago → T33 NotificarPagoExito
 *
 *   CU2-OK (Registro exitoso):
 *     T24 RecibirEvento → T1 AuthExitosa_C2 → T25 RegistroExitoso
 *     → T22 NotificarRegistroEnviado
 *
 *   CU3-OK (Modificar pago exitoso):
 *     T24 RecibirEvento → T2 AuthExitosa_C3 → T18 ModificarPagoExitoso
 *     → T21 NotificarModificarPagoEnviado
 *
 *   CU6-OK (Reporte exitoso):
 *     T24 RecibirEvento → T3 AuthExitosa_C6 → T17 GeneracionReporteExitoso
 *     → T16 FinalizarReporte → T34 NotificarReporteExito
 */
class PetriMonitorConcurrencyTest {

    private static final int[] CU1_OK = {24, 0, 31, 15, 33};
    private static final int[] CU2_OK = {24, 1, 25, 22};
    private static final int[] CU3_OK = {24, 2, 18, 21};
    private static final int[] CU6_OK = {24, 3, 17, 16, 34};

    // ── Sanity: un solo flujo sin concurrencia ──────────────────────────

    @Test
    @Timeout(10)
    void singleCU1OK_shouldComplete() throws PetriMonitorException {
        PetriMonitor monitor = new PetriMonitor();
        fireFlow(monitor, CU1_OK, UUID.randomUUID().toString());
    }

    @Test
    @Timeout(10)
    void singleCU2OK_shouldComplete() throws PetriMonitorException {
        PetriMonitor monitor = new PetriMonitor();
        fireFlow(monitor, CU2_OK, UUID.randomUUID().toString());
    }

    @Test
    @Timeout(10)
    void singleCU3OK_shouldComplete() throws PetriMonitorException {
        PetriMonitor monitor = new PetriMonitor();
        fireFlow(monitor, CU3_OK, UUID.randomUUID().toString());
    }

    @Test
    @Timeout(10)
    void singleCU6OK_shouldComplete() throws PetriMonitorException {
        PetriMonitor monitor = new PetriMonitor();
        fireFlow(monitor, CU6_OK, UUID.randomUUID().toString());
    }

    // ── Concurrent steps (virtual threads): cada paso como tarea ────────
    // Ejercita goToSleep/signalNextOrReleaseMutex a fondo.

    @Test
    @Timeout(30)
    void concurrentSteps_100xCU1OK() throws Exception {
        assertConcurrentSteps(CU1_OK, 100);
    }

    @Test
    @Timeout(30)
    void concurrentSteps_100xCU2OK() throws Exception {
        assertConcurrentSteps(CU2_OK, 100);
    }

    @Test
    @Timeout(30)
    void concurrentSteps_100xCU3OK() throws Exception {
        assertConcurrentSteps(CU3_OK, 100);
    }

    @Test
    @Timeout(30)
    void concurrentSteps_100xCU6OK() throws Exception {
        assertConcurrentSteps(CU6_OK, 100);
    }

    // ── Concurrencia por fases: barrera entre cada paso ──────────────────
    // Todos los flujos hacen T24 juntos, esperan, luego T0 juntos, etc.

    @Test
    @Timeout(30)
    void phasedConcurrency_100xCU1OK_10threads() throws Exception {
        assertPhasedConcurrentFlows(CU1_OK, 100, 10);
    }

    @Test
    @Timeout(30)
    void phasedConcurrency_100xCU1OK_50threads() throws Exception {
        assertPhasedConcurrentFlows(CU1_OK, 100, 50);
    }

    // ── Concurrent steps mixtos ─────────────────────────────────────────

    @Test
    @Timeout(60)
    void concurrentSteps_mixed_25each() throws Exception {
        assertConcurrentMixedSteps(25, 25, 25, 25);
    }

    @Test
    @Timeout(60)
    void concurrentSteps_mixed_50each() throws Exception {
        assertConcurrentMixedSteps(50, 50, 50, 50);
    }

    // ── Estrés ──────────────────────────────────────────────────────────

    @Test
    @Timeout(60)
    void stress_200xCU1OK() throws Exception {
        assertConcurrentSteps(CU1_OK, 200);
    }

    @Test
    @Timeout(120)
    void stress_500xCU1OK() throws Exception {
        assertConcurrentSteps(CU1_OK, 500);
    }

    @Test
    @Timeout(120)
    void stress_mixed_100each() throws Exception {
        assertConcurrentMixedSteps(100, 100, 100, 100);
    }

    // ── Secuencial (baseline) ───────────────────────────────────────────

    @Test
    @Timeout(10)
    void sequential_10xCU1OK_shouldComplete() throws PetriMonitorException {
        PetriMonitor monitor = new PetriMonitor();
        for (int i = 0; i < 10; i++) {
            fireFlow(monitor, CU1_OK, UUID.randomUUID().toString());
        }
    }

    @Test
    @Timeout(10)
    void sequential_mixedFlows_shouldComplete() throws PetriMonitorException {
        PetriMonitor monitor = new PetriMonitor();
        for (int i = 0; i < 5; i++) {
            fireFlow(monitor, CU1_OK, UUID.randomUUID().toString());
            fireFlow(monitor, CU2_OK, UUID.randomUUID().toString());
            fireFlow(monitor, CU3_OK, UUID.randomUUID().toString());
            fireFlow(monitor, CU6_OK, UUID.randomUUID().toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    private void fireFlow(PetriMonitor monitor, int[] transitions, String uuid) throws PetriMonitorException {
        for (int t : transitions) {
            monitor.fire(new PetriTransition(t, uuid));
        }
    }

    /**
     * Cada paso de cada flujo se lanza como tarea independiente con virtual threads.
     * Todas las tareas se mezclan (shuffle) para máximo entrelazado.
     *
     * Un hilo que intenta un paso cuyo predecesor no disparó aún, irá a goToSleep.
     * El mecanismo de wakeup (signalNextOrReleaseMutex) debe despertarlo cuando
     * el predecesor dispare.
     *
     * Virtual threads evitan pool exhaustion: cuando un thread se bloquea en
     * sem.acquire(), el platform thread subyacente queda libre.
     */
    private void assertConcurrentSteps(int[] flowTransitions, int numFlows) throws Exception {
        PetriMonitor monitor = new PetriMonitor();
        int totalTasks = numFlows * flowTransitions.length;
        CountDownLatch allDone = new CountDownLatch(totalTasks);
        AtomicInteger completedSteps = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        String[] uuids = new String[numFlows];
        for (int i = 0; i < numFlows; i++) {
            uuids[i] = UUID.randomUUID().toString();
        }

        // Crear todas las tareas
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < numFlows; i++) {
            String uuid = uuids[i];
            for (int transition : flowTransitions) {
                tasks.add(() -> {
                    try {
                        monitor.fire(new PetriTransition(transition, uuid));
                        completedSteps.incrementAndGet();
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        allDone.countDown();
                    }
                });
            }
        }

        // Mezclar para máximo entrelazado
        Collections.shuffle(tasks);

        // Lanzar con virtual threads (Java 21) — no hay pool exhaustion
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Runnable task : tasks) {
                executor.submit(task);
            }

            boolean finished = allDone.await(Math.max(25, totalTasks / 10), TimeUnit.SECONDS);

            if (!errors.isEmpty()) {
                System.err.println("=== ERRORS (" + errors.size() + ") ===");
                errors.stream().limit(5).forEach(Throwable::printStackTrace);
            }

            assertTrue(finished,
                    String.format("Deadlock: solo %d/%d pasos completaron (de %d flujos)",
                            completedSteps.get(), totalTasks, numFlows));
            assertEquals(0, errors.size(),
                    String.format("%d pasos fallaron con excepciones", errors.size()));
            assertEquals(totalTasks, completedSteps.get());
        }
    }

    /**
     * Ejecución por fases: todos los flujos disparan el mismo paso simultáneamente,
     * esperan en un latch, y avanzan al siguiente paso juntos.
     * Usa un pool fijo — no necesita virtual threads porque nunca hay goToSleep
     * (cada paso está habilitado porque el anterior ya completó en la fase previa).
     */
    private void assertPhasedConcurrentFlows(int[] flowTransitions, int numFlows, int concurrency) throws Exception {
        PetriMonitor monitor = new PetriMonitor();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        String[] uuids = new String[numFlows];
        for (int i = 0; i < numFlows; i++) {
            uuids[i] = UUID.randomUUID().toString();
        }

        for (int step = 0; step < flowTransitions.length; step++) {
            int transition = flowTransitions[step];
            CountDownLatch phaseDone = new CountDownLatch(numFlows);

            for (int i = 0; i < numFlows; i++) {
                String uuid = uuids[i];
                executor.submit(() -> {
                    try {
                        monitor.fire(new PetriTransition(transition, uuid));
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        phaseDone.countDown();
                    }
                });
            }

            boolean phaseFinished = phaseDone.await(15, TimeUnit.SECONDS);
            assertTrue(phaseFinished,
                    String.format("Deadlock en fase %d (T%d): %d/%d pendientes",
                            step, transition, phaseDone.getCount(), numFlows));
        }

        executor.shutdownNow();
        assertTrue(errors.isEmpty(),
                String.format("%d errores durante ejecución por fases", errors.size()));
    }

    /**
     * Flujos mixtos con todos los pasos como tareas independientes.
     * Virtual threads para evitar pool exhaustion.
     */
    private void assertConcurrentMixedSteps(int cu1Count, int cu2Count, int cu3Count, int cu6Count) throws Exception {
        PetriMonitor monitor = new PetriMonitor();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        AtomicInteger completedSteps = new AtomicInteger(0);

        List<Runnable> tasks = new ArrayList<>();
        int totalTasks = 0;

        totalTasks += buildFlowTasks(tasks, CU1_OK, cu1Count, monitor, completedSteps, errors);
        totalTasks += buildFlowTasks(tasks, CU2_OK, cu2Count, monitor, completedSteps, errors);
        totalTasks += buildFlowTasks(tasks, CU3_OK, cu3Count, monitor, completedSteps, errors);
        totalTasks += buildFlowTasks(tasks, CU6_OK, cu6Count, monitor, completedSteps, errors);

        CountDownLatch allDone = new CountDownLatch(totalTasks);
        Collections.shuffle(tasks);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Runnable task : tasks) {
                executor.submit(() -> {
                    try {
                        task.run();
                    } finally {
                        allDone.countDown();
                    }
                });
            }

            boolean finished = allDone.await(Math.max(50, totalTasks / 10), TimeUnit.SECONDS);

            assertTrue(finished,
                    String.format("Deadlock: solo %d/%d pasos completaron",
                            completedSteps.get(), totalTasks));
            assertTrue(errors.isEmpty(),
                    String.format("%d pasos fallaron con excepciones", errors.size()));
            assertEquals(totalTasks, completedSteps.get());
        }
    }

    private int buildFlowTasks(List<Runnable> tasks, int[] flowTransitions, int numFlows,
                               PetriMonitor monitor, AtomicInteger completedSteps, List<Throwable> errors) {
        int count = 0;
        for (int i = 0; i < numFlows; i++) {
            String uuid = UUID.randomUUID().toString();
            for (int transition : flowTransitions) {
                tasks.add(() -> {
                    try {
                        monitor.fire(new PetriTransition(transition, uuid));
                        completedSteps.incrementAndGet();
                    } catch (Exception e) {
                        errors.add(e);
                    }
                });
                count++;
            }
        }
        return count;
    }
}
