# Hospital Emergency Room Simulation

Operating Systems Assignment 1 — CS241/SCS241, Faculty of Computing and Artificial Intelligence, Cairo University.

## Overview

A Java multithreaded simulation of a hospital emergency room implementing the Producer–Consumer problem with a bounded waiting-room buffer and counting semaphores.

The simulation models:
- Patients arriving concurrently and waiting for a seat when the waiting room is full.
- Multiple doctors consuming patients from the waiting room.
- Exactly **two treatment rooms**, shared by all doctors.
- A maximum **3-second** treatment-room acquisition attempt.
- Patient re-queuing when a doctor cannot acquire a treatment room in time.
- Random treatment durations and patient departure after treatment.

## Project structure

```text
Hospital-Emergency-Room-Simulation/
├── .vscode/
│   └── tasks.json
├── src/
│   └── Hospital.java
└── README.md
```

The supplied starter project keeps the classes in one source file. Java permits one public top-level class (`Hospital`) and package-private supporting classes in the same file. `javac` compiles all of them and generates their corresponding `.class` files.

### Classes

| Class | Responsibility |
|---|---|
| `Hospital` | Reads input, constructs shared resources and threads, starts the simulation, and shuts doctors down after all patients finish. |
| `Semaphore` | Counting semaphore implemented using `synchronized`, `wait()`, and `notifyAll()`. Includes timed `tryAcquire`. |
| `BoundedBuffer` | FIFO waiting room, synchronized with `emptySlots`, `fullSlots`, and `mutex` semaphores. |
| `TreatmentRooms` | Independent semaphore-backed pool of exactly two treatment-room permits. |
| `Patient` | Producer thread; enters the waiting room and waits for treatment completion. |
| `Doctor` | Consumer thread; takes patients, attempts room acquisition, treats or re-queues them. |
| `Logger` | Simple synchronized logging helper. `Hospital.log` is used by the simulation to serialize output. |

## Requirements

- JDK 17 or later (JDK 17 is recommended).
- Visual Studio Code.
- VS Code **Extension Pack for Java** (Microsoft).

Check the Java installation:

```bash
java -version
javac -version
```

On Ubuntu, if Java is not installed:

```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

## Run from VS Code

1. Open the project root folder (the folder containing `README.md`) in VS Code.
2. Install **Extension Pack for Java** if it is not already installed.
3. Open `src/Hospital.java`.
4. Use the Run button above `main`, or open the integrated terminal and run:

```bash
javac -d out src/Hospital.java
java -cp out Hospital
```

Enter the waiting-room capacity, number of doctors, and number of patients when prompted.

### Run using the configured build task

Press `Ctrl+Shift+B` and select **Build Java simulation**. Then run the `java -cp out Hospital` command in the integrated terminal.

## Example input

```text
Waiting room capacity: 2
Number of doctors: 3
Number of patients: 5
```

The exact event order varies between executions because Java threads are scheduled concurrently. A valid run should show arrivals, waiting-room entry/outside waiting, doctor assignments, treatment-room acquisition attempts, treatment or re-queueing, completion, and patient departures.

## Synchronization design

### Waiting room — bounded-buffer pattern

Three semaphores protect the queue:

| Semaphore | Initial value | Meaning |
|---|---:|---|
| `emptySlots` | `capacity` | Number of free waiting-room seats. |
| `fullSlots` | `0` | Number of queued patients. |
| `mutex` | `1` | Mutual exclusion for queue operations. |

Producer sequence: acquire `emptySlots` → acquire `mutex` → enqueue → release `mutex` → release `fullSlots`.

Consumer sequence: acquire `fullSlots` → acquire `mutex` → dequeue → release `mutex` → release `emptySlots`.

Both arriving patients and doctors returning a patient use the producer operation. Doctors use the consumer operation.

### Treatment rooms

`TreatmentRooms` owns a separate counting semaphore initialized to `2`. A doctor calls `tryAcquireRoom(3000)`. On success, it holds one permit for the duration of treatment and releases it in a `finally` block. On timeout, it returns the patient to the waiting room.

### Important correctness points

- The queue is only accessed while holding `mutex`.
- `emptySlots` and `fullSlots` enforce the buffer capacity and item count.
- The timed semaphore uses a monotonic deadline and a loop, so spurious wakeups do not extend the timeout.
- A doctor does not hold a treatment-room permit while waiting to re-enter a full waiting room.
- The waiting room and treatment rooms use independent synchronization.
- The implementation does not use `java.util.concurrent.Semaphore`.

## Testing checklist

- [ ] Capacity `1`, doctors `1`, patients `1` — basic arrival and treatment.
- [ ] Capacity `2`, doctors `3`, patients `5` — assignment sample scenario.
- [ ] Capacity `1`, doctors `4`, patients `10` — contention and re-queueing.
- [ ] Capacity `5`, doctors `1`, patients `8` — producer/consumer blocking.
- [ ] Confirm no more than two doctors are treating patients simultaneously.
- [ ] Confirm every patient who enters the simulation eventually leaves after treatment.
- [ ] Repeat each scenario several times because thread interleavings vary.

## Notes for team integration

The supplied starter archive currently contains a single `src/Hospital.java` with all supporting classes and TODO implementations. This implementation preserves the existing class names and method signatures so the resource layer can be merged with the team's work without renaming its interfaces.

If the team later splits classes into separate `.java` files, keep only `Hospital` public in its own file and move each package-private class into a correspondingly named file. Do not keep duplicate class definitions in both the combined and split versions.
