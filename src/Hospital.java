import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;

/**
 * Hospital Emergency Room Simulation - CS241 Operating Systems Assignment 1.
 * The shared-resource classes (Semaphore, BoundedBuffer, TreatmentRooms) are
 * implemented using the course's synchronized/wait/notify semaphore approach.
 * No java.util.concurrent.Semaphore is used.
 */
public class Hospital {
    private static final Object PRINT_LOCK = new Object();

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
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
                // Re-prompt for invalid input.
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
            if (i > 0) arrivals.append(' ');
            arrivals.append(patients[i]);
        }
        log(arrivals.toString());

        for (Doctor doctor : doctors) doctor.start();
        for (Patient patient : patients) {
            patient.start();
            Thread.sleep(150);
        }

        // A patient thread terminates only after its treatment is complete.
        for (Patient patient : patients) patient.join();

        // Doctors may be blocked waiting for a patient; interrupt them for shutdown.
        for (Doctor doctor : doctors) doctor.interrupt();
        for (Doctor doctor : doctors) doctor.join();

        log("All patients have been treated.");
        log("Simulation completed.");
    }
}

/** Counting semaphore implemented with intrinsic locking and a wait set. */
class Semaphore {
    private int value;

    public Semaphore(int initialValue) {
        if (initialValue < 0) throw new IllegalArgumentException("Semaphore value cannot be negative.");
        this.value = initialValue;
    }

    /** P()/wait(): block until a permit is available, then consume one. */
    public synchronized void acquire() throws InterruptedException {
        while (value == 0) wait();
        value--;
    }

    /** V()/signal(): return a permit and wake blocked threads. */
    public synchronized void release() {
        value++;
        notifyAll();
    }

    /**
     * Acquire within a timeout. A monotonic deadline prevents spurious wakeups
     * from extending the requested timeout.
     */
    public synchronized boolean tryAcquire(long timeoutMs) throws InterruptedException {
        if (timeoutMs < 0) throw new IllegalArgumentException("Timeout cannot be negative.");
        if (value > 0) {
            value--;
            return true;
        }
        if (timeoutMs == 0) return false;

        final long timeoutNanos = timeoutMs * 1_000_000L;
        final long start = System.nanoTime();
        final long deadline = start + timeoutNanos;

        while (value == 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            long millis = remaining / 1_000_000L;
            int nanos = (int) (remaining % 1_000_000L);
            wait(millis, nanos);
        }
        value--;
        return true;
    }

    public synchronized int availablePermits() {
        return value;
    }
}

/**
 * Additional class #1: bounded waiting-room buffer.
 * Producers: arriving patients and doctors returning patients.
 * Consumers: doctors taking the next patient.
 */
class BoundedBuffer {
    private final Queue<Patient> queue = new LinkedList<>();
    private final int capacity;
    private final Semaphore emptySlots;
    private final Semaphore fullSlots;
    private final Semaphore mutex;

    public BoundedBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Waiting-room capacity must be positive.");
        this.capacity = capacity;
        this.emptySlots = new Semaphore(capacity);
        this.fullSlots = new Semaphore(0);
        this.mutex = new Semaphore(1);
    }

    /** Snapshot-style status check for event reporting; put() remains authoritative. */
    public boolean isFull() {
        return emptySlots.availablePermits() == 0;
    }

    /** Producer operation: wait for a free seat, then insert exactly once. */
    public void put(Patient patient) throws InterruptedException {
        if (patient == null) throw new IllegalArgumentException("Cannot enqueue a null patient.");
        emptySlots.acquire();
        boolean mutexAcquired = false;
        try {
            mutex.acquire();
            mutexAcquired = true;
            if (queue.size() >= capacity) {
                throw new IllegalStateException("Buffer invariant violated: queue is full after acquiring a slot.");
            }
            queue.add(patient);
        } catch (InterruptedException | RuntimeException ex) {
            // Do not lose the reserved empty-slot permit if insertion did not complete.
            emptySlots.release();
            throw ex;
        } finally {
            if (mutexAcquired) mutex.release();
        }
        fullSlots.release();
    }

    /** Consumer operation: wait for an item, then remove the FIFO head. */
    public Patient take() throws InterruptedException {
        fullSlots.acquire();
        boolean mutexAcquired = false;
        Patient patient;
        try {
            mutex.acquire();
            mutexAcquired = true;
            patient = queue.poll();
            if (patient == null) {
                throw new IllegalStateException("Buffer invariant violated: full-slot permit without a patient.");
            }
        } catch (InterruptedException | RuntimeException ex) {
            fullSlots.release();
            throw ex;
        } finally {
            if (mutexAcquired) mutex.release();
        }
        emptySlots.release();
        return patient;
    }
}

/** Additional class #2: independent pool of exactly two treatment rooms. */
class TreatmentRooms {
    public static final int NUM_ROOMS = 2;
    private final Semaphore rooms;

    public TreatmentRooms() {
        rooms = new Semaphore(NUM_ROOMS);
    }

    public boolean tryAcquireRoom(long timeoutMs) throws InterruptedException {
        return rooms.tryAcquire(timeoutMs);
    }

    public void releaseRoom() {
        // The semaphore represents two identical room permits.
        // A release beyond capacity is a programming error, not extra capacity.
        synchronized (rooms) {
            if (rooms.availablePermits() >= NUM_ROOMS) {
                throw new IllegalStateException("Cannot release a treatment room that is not held.");
            }
            rooms.release();
        }
    }
}

/** Patient thread (producer). */
class Patient extends Thread {
    private final int id;
    private final BoundedBuffer waitingRoom;
    private final Semaphore treatmentDone;

    public Patient(int id, BoundedBuffer waitingRoom) {
        super("Patient-" + id);
        this.id = id;
        this.waitingRoom = waitingRoom;
        this.treatmentDone = new Semaphore(0);
    }

    public int getPatientId() { return id; }

    public void finishTreatment() {
        treatmentDone.release();
    }

    @Override
    public void run() {
        try {
            Hospital.log(this + " arrived.");
            if (waitingRoom.isFull()) {
                Hospital.log("The waiting room is full.");
                Hospital.log(this + " is waiting outside for an available seat.");
            }
            waitingRoom.put(this);
            Hospital.log(this + " entered the waiting room.");
            treatmentDone.acquire();
            Hospital.log(this + " left.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Hospital.log(this + " was interrupted before completing the visit.");
        }
    }

    @Override
    public String toString() { return "P" + id; }
}

/** Doctor thread (consumer). */
class Doctor extends Thread {
    private static final long ROOM_TIMEOUT_MS = 3000;
    private static final long MIN_TREATMENT_MS = 2000;
    private static final long MAX_TREATMENT_MS = 5000;

    private final int id;
    private final BoundedBuffer waitingRoom;
    private final TreatmentRooms treatmentRooms;
    private final Random random = new Random();

    public Doctor(int id, BoundedBuffer waitingRoom, TreatmentRooms treatmentRooms) {
        super("Doctor-" + id);
        this.id = id;
        this.waitingRoom = waitingRoom;
        this.treatmentRooms = treatmentRooms;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                Patient patient = waitingRoom.take();
                Hospital.log("Doctor " + id + " is assigned to " + patient + ".");
                Hospital.log("Doctor " + id + " is trying to acquire a treatment room.");

                boolean acquired = treatmentRooms.tryAcquireRoom(ROOM_TIMEOUT_MS);
                if (!acquired) {
                    Hospital.log("Doctor " + id + " could not acquire a treatment room.");
                    Hospital.log("Doctor " + id + " is sending " + patient + " back to the waiting room.");
                    if (waitingRoom.isFull()) {
                        Hospital.log("The waiting room is full.");
                        Hospital.log(patient + " is waiting outside for an available seat.");
                    }
                    // No treatment-room permit is held here. put() may wait for a seat.
                    waitingRoom.put(patient);
                    Hospital.log(patient + " entered the waiting room.");
                    Hospital.log("Doctor " + id + " is now available.");
                    continue;
                }

                try {
                    Hospital.log("Doctor " + id + " acquired a treatment room.");
                    Hospital.log("Doctor " + id + " begins treating " + patient + ".");
                    long treatmentTime = MIN_TREATMENT_MS
                            + random.nextInt((int) (MAX_TREATMENT_MS - MIN_TREATMENT_MS + 1));
                    Thread.sleep(treatmentTime);
                    Hospital.log("Doctor " + id + " finished treating " + patient + ".");
                } finally {
                    treatmentRooms.releaseRoom();
                    Hospital.log("Doctor " + id + " released the treatment room.");
                }
                patient.finishTreatment();
                Hospital.log("Doctor " + id + " is now available.");
            }
        } catch (InterruptedException e) {
            interrupt();
        }
    }
}

/** Serializes complete log lines across all simulation threads. */
class Logger {
    public static synchronized void log(String message) {
        System.out.println(message);
    }
}
