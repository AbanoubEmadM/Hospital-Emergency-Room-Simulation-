import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public class Hospital {
    private static final int TREATMENT_ROOMS = 2;
    private static final Object PRINT_LOCK = new Object();

    /** Thread-safe event printing so lines never interleave. */
    public static void log(String message) {
        synchronized (PRINT_LOCK) {
            System.out.println(message);
        }
    }

    private static int readPositive(Scanner in, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!in.hasNextLine()) {
                System.exit(1);
            }
            try {
                int v = Integer.parseInt(in.nextLine().trim());
                if (v > 0) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
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

        WaitingRoom waitingRoom = new WaitingRoom(capacity);
        TreatmentRooms treatmentRooms = new TreatmentRooms(TREATMENT_ROOMS);
        CountDownLatch remaining = new CountDownLatch(numPatients);

        List<Thread> doctors = new ArrayList<>();
        for (int i = 1; i <= numDoctors; i++) {
            Thread t = new Thread(new Doctor(i, waitingRoom, treatmentRooms, remaining));
            doctors.add(t);
            t.start();
        }

        StringBuilder sb = new StringBuilder("Patients arriving:\n");
        for (int i = 1; i <= numPatients; i++) {
            sb.append("P").append(i).append(i < numPatients ? " " : "");
        }
        log(sb.toString());

        for (int i = 1; i <= numPatients; i++) {
            new Thread(new Patient(i, waitingRoom)).start();
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
        }

        remaining.await(); // all patients treated and gone

        for (Thread d : doctors) {
            d.interrupt();
        }
        for (Thread d : doctors) {
            d.join();
        }
        log("All patients have been treated.");
        log("Simulation completed.");
    }
}
