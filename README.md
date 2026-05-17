# PetriProcessor

Librería Java reutilizable para la ejecución de Redes de Petri No Autónomas (RPNA) con soporte para transiciones temporales y acceso concurrente.

Desarrollada como parte de la tesis de Maestría en Ingeniería de Software (UNLP):
**"Modelado de Sistemas Reactivos mediante Redes de Petri no Autónomas y Microservicios"**.

La librería se publica como artefacto Maven (`com.unlp:petri-processor:1.0.0`) y es consumida por el microservicio de Pagos (`Payments`) del caso de estudio, pero no depende de ningún dominio en particular: puede integrarse en cualquier aplicación Java que requiera ejecutar y verificar el comportamiento de una RPNA en tiempo de ejecución.

## Capacidades

- Definición de una Red de Petri a partir de su matriz de incidencia (`I+`, `I−`) y un marcado inicial, declarados en `petri-config.json`.
- Evaluación de la sensibilización de transiciones (`isEnabled`) y disparo controlado (`fireTransition`), respetando la semántica formal de las RdP.
- Soporte para **transiciones temporales** (parámetro `alpha`): una transición temporal sólo dispara cuando el tiempo de sensibilización acumulado supera el umbral configurado.
- **Identificación de instancias por UUID**: además del marcado clásico (`int[]`), la librería lleva un mapa `place → Set<UUID>` que permite distinguir entre múltiples instancias del mismo flujo ejecutándose concurrentemente sobre la misma red.
- **Acceso concurrente seguro** mediante la clase `PetriMonitor`: implementa el patrón monitor con un semáforo de exclusión mutua y variables de condición indexadas por `(transición, UUID)`. Los hilos que intentan disparar una transición no sensibilizada se duermen y son despertados selectivamente cuando la condición se cumple.
- **Persistencia del estado** a través de la interfaz `IPetriNetState`. La implementación por defecto (`InMemoryPetriNetState`) mantiene el estado en memoria; el consumidor de la librería puede proveer su propia implementación (por ejemplo, persistencia JPA) para mantener el estado entre reinicios.
- **Política de ejecución desacoplada** (`IPetriPolicy`): permite definir cómo se elige la próxima transición a disparar cuando hay varias sensibilizadas. La implementación incluida (`RandomDefaultPolicy`) selecciona aleatoriamente.

## Estructura del proyecto

```
src/main/java/com/unlp/petri_processor/
├── PetriNet.java               # Núcleo: marcado, sensibilización, disparo
├── PetriMonitor.java           # Monitor para acceso concurrente
├── PetriTransition.java        # Identificador de una transición (id + UUID)
├── TimedTransition.java        # Transición temporal con parámetros alpha/beta
├── TimedTransitionConfig.java  # Configuración de una transición temporal
├── PetriNetConfig.java         # Configuración general de la red (DTO)
├── PetriNetSnapshot.java       # Snapshot inmutable del estado
├── IPetriNetState.java         # Interfaz de persistencia del estado
├── InMemoryPetriNetState.java  # Persistencia en memoria (default)
├── IPetriPolicy.java           # Interfaz de política de disparo
├── RandomDefaultPolicy.java    # Política aleatoria
└── exceptions/
    └── PetriMonitorException.java
```

El consumidor de la librería debe proveer un archivo `petri-config.json` en el classpath (`src/main/resources/petri-config.json`) con la siguiente estructura:

```json
{
  "matrizIPlus":  [[ ... ]],
  "matrizIMinus": [[ ... ]],
  "initialMarking": [0, 0, ...],
  "timedTransitions": [
    { "transitionId": 5,  "alpha": 999999 },
    { "transitionId": 7,  "alpha": 999999 }
  ],
  "mapPlacesToIndex":      { "P0": 0, "P1": 1 },
  "mapTransitionsToIndex": { "T0": 0, "T1": 1 }
}
```

## Requisitos

- **Java 21** (la librería utiliza `record`s y features modernos del lenguaje).
- **Maven 3.6+** (el repositorio incluye el Maven wrapper `mvnw`, por lo que no es estrictamente necesario tener Maven instalado).

## Compilación

Desde la raíz del repositorio:

```bash
./mvnw clean install
```

Esto compila el código, ejecuta los tests y publica el artefacto `com.unlp:petri-processor:1.0.0` en el repositorio local de Maven (`~/.m2/repository`), de modo que pueda ser consumido por otros proyectos como dependencia.

Para compilar sin correr los tests:

```bash
./mvnw clean install -DskipTests
```

## Tests

Los tests utilizan JUnit 5 y **virtual threads de Java 21** para verificar el comportamiento concurrente del monitor bajo distintos escenarios (flujos individuales, flujos concurrentes y disparo por fases). Cubren los casos de uso del caso de estudio (CU1-OK, CU2-OK, CU3-OK, CU6-OK) y sus variantes de error.

Para ejecutarlos:

```bash
./mvnw test
```

## Uso como dependencia

Una vez instalada en el repositorio Maven local (o publicada en un repositorio remoto), la librería se incluye en otro proyecto agregando al `pom.xml`:

```xml
<dependency>
    <groupId>com.unlp</groupId>
    <artifactId>petri-processor</artifactId>
    <version>1.0.0</version>
</dependency>
```

Y se utiliza así:

```java
// Creación del monitor con persistencia en memoria (default)
PetriMonitor monitor = new PetriMonitor();

// O con una implementación de persistencia propia (por ejemplo, JPA)
PetriMonitor monitor = new PetriMonitor(new MyJpaPetriNetState());

// Disparo de una transición identificada por su id y un UUID de instancia
PetriTransition transition = new PetriTransition(24, "uuid-de-la-instancia");
monitor.fire(transition);
```

El método `fire` es bloqueante: si la transición no está sensibilizada, el hilo se duerme hasta que la transición se habilita; si la transición es temporal y aún no venció su tiempo de espera, el hilo libera el mutex, espera y reintenta.

## Licencia

Distribuido bajo licencia MIT. Ver `LICENSE` para más detalles.
