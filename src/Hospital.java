import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.ThreadLocalRandom;

// ============================================================
// Hospital.java  -  single source file, like ServiceStation.java
// Only Hospital is public; the other classes are package-private
// and javac will produce a separate .class file for each one.
// ============================================================

// ===================== HOSPITAL (main) =====================
/**
 * Hospital.java  (Required class - 1 mark)
 * Owner: Member ____
 *
 * Entry point. Reads input, creates shared resources, doctors and patients,
 * starts everything and shuts down cleanly.
 */

public class Hospital {

    private static final Object PRINT_LOCK = new Object();

    /** Print one complete event at a time, even when several threads log. */
    public static void log(String message) {
        synchronized (PRINT_LOCK) {
            System.out.println(message);
        }
    }

    private static int readPositive(Scanner in, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!in.hasNextLine()) {
                throw new IllegalStateException("Input ended before the simulation could start.");
            }
            try {
                int value = Integer.parseInt(in.nextLine().trim());
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Ask again below.
            }
            System.out.println("Please enter a positive integer.");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Scanner in = new Scanner(System.in);
        int capacity = readPositive(in, "Waiting room capacity: ");
        int numDoctors = readPositive(in, "Number of doctors: ");
        int numPatients = readPositive(in, "Number of patients: ");
        System.out.println();

        BoundedBuffer waitingRoom = new BoundedBuffer(capacity);
        TreatmentRooms rooms = new TreatmentRooms();

        Doctor[] doctors = new Doctor[numDoctors];
        for (int i = 0; i < numDoctors; i++) {
            doctors[i] = new Doctor(i + 1, waitingRoom, rooms);
        }

        Patient[] patients = new Patient[numPatients];
        for (int i = 0; i < numPatients; i++) {
            patients[i] = new Patient(i + 1, waitingRoom);
        }

        StringBuilder arrivals = new StringBuilder("Patients arriving:\n");
        for (int i = 0; i < numPatients; i++) {
            if (i > 0) {
                arrivals.append(' ');
            }
            arrivals.append(patients[i]);
        }
        log(arrivals.toString());

        for (Doctor doctor : doctors) {
            doctor.start();
        }
        for (Patient patient : patients) {
            patient.start();
            Thread.sleep(150);
        }

        // Each patient thread exits only after its treatment has finished.
        for (Patient patient : patients) {
            patient.join();
        }

        for (Doctor doctor : doctors) {
            doctor.interrupt();
        }
        for (Doctor doctor : doctors) {
            doctor.join();
        }

        log("All patients have been treated.");
        log("Simulation completed.");
    }
}

// ===================== SEMAPHORE =====================
/**
 * Semaphore.java  (Required class - 1 mark)
 * Owner: Member ____
 *
 * Counting semaphore implemented from scratch (as in the labs).
 * Do NOT use java.util.concurrent.Semaphore.
 * Hint: use synchronized + wait()/notify() (or notifyAll()).
 */
class Semaphore {

    private int value;   // current number of available permits

    // TODO: constructor Semaphore(int initialValue)
    public Semaphore(int initialValue) {
    }

    /**
     * Classic wait()/P(): block while value == 0, then value--.
     * TODO: loop on wait() while value <= 0, then decrement.
     */
    public synchronized void acquire() throws InterruptedException {
    }

    /**
     * Classic signal()/V(): value++ then wake up a waiting thread.
     * TODO
     */
    public synchronized void release() {
    }

    /**
     * CUSTOM METHOD REQUIRED BY THE ASSIGNMENT.
     * Try to acquire within timeoutMs milliseconds.
     * Returns true if acquired, false if the time ran out.
     * TODO:
     *   1. compute deadline = now + timeoutMs
     *   2. while value == 0: compute remaining time; if <= 0 return false; wait(remaining)
     *   3. value--; return true
     * Careful with spurious wakeups (that's why we use a loop + deadline).
     */
    public synchronized boolean tryAcquire(long timeoutMs) throws InterruptedException {
        return false;
    }

    /** Non-blocking check, useful to know if the waiting room is full. TODO */
    public synchronized int availablePermits() {
        return 0;
    }
}

// ===================== BOUNDEDBUFFER =====================
/**
 * BoundedBuffer.java  (Additional class - shared resource #1: the WAITING ROOM)
 * Owner: Member ____
 *
 * Classic Producer-Consumer bounded buffer:
 *   - Producers = Patients (and doctors returning a patient)
 *   - Consumers = Doctors
 *
 * Uses THREE semaphores:
 *   emptySlots (init = capacity)  -> counts free seats
 *   fullSlots  (init = 0)         -> counts waiting patients
 *   mutex      (init = 1)         -> protects the queue itself
 */

class BoundedBuffer {

    private final Queue<Patient> queue = new LinkedList<>();
    private final int capacity;

    private final Semaphore emptySlots;
    private final Semaphore fullSlots;
    private final Semaphore mutex;

    // TODO: constructor(int capacity) -> initialize the 3 semaphores
    public BoundedBuffer(int capacity) {
        this.capacity = capacity;
        this.emptySlots = null; // TODO
        this.fullSlots  = null; // TODO
        this.mutex      = null; // TODO
    }

    /** True if no seat is free right now (used to print "waiting room is full"). TODO */
    public boolean isFull() {
        return false;
    }

    /**
     * Producer side: put a patient in the waiting room. Blocks if full.
     * TODO:
     *   emptySlots.acquire();
     *   mutex.acquire();   queue.add(p);   mutex.release();
     *   fullSlots.release();
     * NOTE: the "P entered the waiting room." log goes here (or in Patient) - decide as a team,
     *       but print it ONCE only.
     */
    public void put(Patient p) throws InterruptedException {
    }

    /**
     * Consumer side: doctor takes a patient. Blocks if empty (doctor is idle).
     * TODO:
     *   fullSlots.acquire();
     *   mutex.acquire();   Patient p = queue.poll();   mutex.release();
     *   emptySlots.release();
     *   return p;
     */
    public Patient take() throws InterruptedException {
        return null;
    }
}

// ===================== TREATMENTROOMS =====================
/**
 * TreatmentRooms.java  (Additional class - shared resource #2: the TREATMENT ROOMS)
 * Owner: Member ____
 *
 * Independent from the waiting room. Only 2 rooms in the whole ER.
 * Just a counting semaphore initialized to 2.
 */
class TreatmentRooms {

    public static final int NUM_ROOMS = 2;
    private final Semaphore rooms;   // TODO: new Semaphore(NUM_ROOMS)

    public TreatmentRooms() {
        this.rooms = null; // TODO
    }

    /**
     * Doctor tries to get a room, waiting at most timeoutMs (3000 ms per the assignment).
     * TODO: return rooms.tryAcquire(timeoutMs)
     */
    public boolean tryAcquireRoom(long timeoutMs) throws InterruptedException {
        return false;
    }

    /** Doctor releases the room after treatment. TODO: rooms.release() */
    public void releaseRoom() {
    }
}

// ===================== PATIENT =====================
/**
 * Patient.java  (Required class - 0.5 mark)
 * Owner: Member ____
 *
 * Each patient is a thread (producer).
 */
class Patient extends Thread {

    private final int id;
    private final BoundedBuffer waitingRoom;

    // Doctor releases this when treatment is done, so the patient can "leave".
    // TODO: init to Semaphore(0)
    private final Semaphore treatmentDone;

    public Patient(int id, BoundedBuffer waitingRoom) {
        this.id = id;
        this.waitingRoom = waitingRoom;
        this.treatmentDone = null; // TODO
    }

    public int getPatientId() { return id; }

    /** Called by the doctor when treatment finishes. TODO: treatmentDone.release() */
    public void finishTreatment() {
    }

    @Override
    public void run() {
        // TODO:
        // 1. log "P<id> arrived."
        // 2. if waitingRoom.isFull():
        //        log "The waiting room is full."
        //        log "P<id> is waiting outside for an available seat."
        // 3. waitingRoom.put(this)          (blocks until a seat is free)
        //        log "P<id> entered the waiting room."
        // 4. treatmentDone.acquire()        (wait until a doctor finishes treating me)
        // 5. log "P<id> left."
        // Handle InterruptedException.
    }

    @Override
    public String toString() { return "P" + id; }
}

// ===================== DOCTOR =====================
/**
 * Doctor.java  (Required class - 1.5 marks)
 * Owner: Member ____
 *
 * Each doctor is a thread (consumer) running an endless loop
 * until the Hospital interrupts it at the end of the simulation.
 */

class Doctor extends Thread {

    private static final long ROOM_TIMEOUT_MS = 3000;   // 3 seconds

    private final int id;
    private final BoundedBuffer waitingRoom;
    private final TreatmentRooms treatmentRooms;
    private final Random random = new Random();

    public Doctor(int id, BoundedBuffer waitingRoom, TreatmentRooms treatmentRooms) {
        this.id = id;
        this.waitingRoom = waitingRoom;
        this.treatmentRooms = treatmentRooms;
    }

    @Override
    public void run() {
        // TODO: while (!isInterrupted()) {
        //
        //   1. Patient p = waitingRoom.take();             // idle until a patient exists
        //      log "Doctor <id> is assigned to P<p>."
        //
        //   2. log "Doctor <id> is trying to acquire a treatment room."
        //      boolean got = treatmentRooms.tryAcquireRoom(ROOM_TIMEOUT_MS);
        //
        //   3. if (!got) {
        //          log "Doctor <id> could not acquire a treatment room."
        //          log "Doctor <id> is sending P<p> back to the waiting room."
        //          if waitingRoom.isFull(): log "The waiting room is full."
        //                                   log "P<p> is waiting outside for an available seat."
        //          waitingRoom.put(p);                      // may block if full
        //          log "P<p> entered the waiting room."
        //          log "Doctor <id> is now available."
        //          continue;
        //      }
        //
        //   4. log "Doctor <id> acquired a treatment room."
        //      log "Doctor <id> begins treating P<p>."
        //      Thread.sleep(random time, e.g. 2000-5000 ms);
        //
        //   5. log "Doctor <id> finished treating P<p>."
        //      treatmentRooms.releaseRoom();
        //      log "Doctor <id> released the treatment room."
        //      log "Doctor <id> is now available."
        //      p.finishTreatment();                          // lets the patient leave
        // }
        // Catch InterruptedException -> exit loop quietly.
        //
        // DEADLOCK WARNING: think about the doctor putting the patient back while the
        // waiting room is full (all doctors holding patients, room full). Discuss as a team
        // how your design avoids this (the sample output shows the patient waiting outside).
    }
}

// ===================== LOGGER =====================
/**
 * Logger.java  (Additional class)
 * Owner: Member ____
 *
 * All threads print through here so output lines never get mixed up.
 */
class Logger {

    // TODO: public static synchronized void log(String message)
    //       -> System.out.println(message)
    public static synchronized void log(String message) {
    }
}
