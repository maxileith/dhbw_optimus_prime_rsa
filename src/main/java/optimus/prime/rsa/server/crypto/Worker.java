package optimus.prime.rsa.server.crypto;

import optimus.prime.rsa.payloads.SlicePayload;
import optimus.prime.rsa.payloads.SolutionPayload;
import optimus.prime.rsa.ConsoleColors;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Callable;

public class Worker implements Callable<SolutionPayload> {
    private final SlicePayload slice;
    private final List<BigInteger> primes;
    private final BigInteger pubRsaKey;

    private final RSAHelper rsaHelper;

    private final static String LOG_MESSAGE_NO_SOLUTION = ConsoleColors.BOLD + "Slave         - Worker - Found no solution in slice %s" + ConsoleColors.RESET;
    private final static String LOG_MESSAGE_SOLUTION_FOUND = ConsoleColors.BOLD + "Slave         - Worker - Found solution in slice %s - Solution is a:%d b:%d" + ConsoleColors.RESET;
    private final static String LOG_START_INSPECTING = ConsoleColors.BOLD + "Slave         - Worker - Start inspecting slice %s" + ConsoleColors.RESET;
    private final static String LOG_INTERRUPTED = ConsoleColors.BOLD + "Slave         - Worker - Interrupted" + ConsoleColors.RESET;

    /**
     * Sets up the worker for finding a solution
     *
     * @param slice the slice where the worker is trying to find the solution
     * @param primes the {@link List} of prime numbers
     * @param pubRsaKey the public key to find the private key for
     */
    public Worker(SlicePayload slice, List<BigInteger> primes, BigInteger pubRsaKey) {
        this.slice = slice;
        this.primes = primes;
        this.pubRsaKey = pubRsaKey;
        this.rsaHelper = new RSAHelper();
    }

    /**
     * tries to find a solution within the slice of the worker
     *
     * @return the {@link SolutionPayload} if found, otherwise null
     */
    @Override
    public SolutionPayload call() {
        System.out.printf((LOG_START_INSPECTING) + "%n", this.slice);
        // Check for interrupt here; 7000 primes in list; solution at ~5600; time -> 1m44s
        for (int a = this.slice.getStart(); a <= this.slice.getEnd(); a++) {
            BigInteger aInt = this.primes.get(a);
            // Check for interrupt here; 7000 primes in list; solution at ~5600; time -> 1m42s
            // Faster interrupts, no performance difference when checking for interrupts here
            for (int b = a + 1; b < this.primes.size(); b++) {
                // if thread is interrupted exit immediately
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println(LOG_INTERRUPTED);
                    return null;
                }
                BigInteger bInt = this.primes.get(b);
                if (this.rsaHelper.isValid(aInt, bInt, this.pubRsaKey)) {
                    System.out.printf((LOG_MESSAGE_SOLUTION_FOUND) + "%n", this.slice, aInt, bInt);
                    return new SolutionPayload(aInt, bInt);
                }
            }
        }

        System.out.printf((LOG_MESSAGE_NO_SOLUTION) + "%n", this.slice);
        return null;
    }
}
