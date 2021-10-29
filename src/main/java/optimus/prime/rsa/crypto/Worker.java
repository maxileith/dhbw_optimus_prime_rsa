package optimus.prime.rsa.crypto;

import optimus.prime.rsa.communication.payloads.SlicePayload;
import optimus.prime.rsa.communication.payloads.SolutionPayload;
import optimus.prime.rsa.main.Colors;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Callable;

public class Worker implements Callable<SolutionPayload> {
    private final SlicePayload slice;
    private final List<BigInteger> primes;
    private final String pubRsaKey;

    private final RSAHelper rsaHelper;

    private final static String LOG_MESSAGE_NO_SOLUTION = "Worker - Found no solution in slice %s";
    private final static String LOG_MESSAGE_SOLUTION_FOUND = "Worker - Found solution in slice %s - Solution is a:%d b:%d";
    private final static String LOG_START_INSPECTING = "Worker - Start inspecting slice %s";

    public Worker(SlicePayload slice, List<BigInteger> primes, String pubRsaKey) {
        this.slice = slice;
        this.primes = primes;
        this.pubRsaKey = pubRsaKey;
        this.rsaHelper = new RSAHelper();
    }

    @Override
    public SolutionPayload call() {
        System.out.printf((LOG_START_INSPECTING) + "%n", this.slice);
        for (int a = slice.getStart(); a <= slice.getEnd(); a++) {
            BigInteger aInt = primes.get(a);
            // Sollte später auf die reine Big Integer Variante gewechselt werden
            // TODO: Optimierung in der Dokumentation berücksichtigen
            for (int b = a + 1; b < primes.size(); b++) {
                BigInteger bInt = primes.get(b);
                if (rsaHelper.isValid(aInt.toString(), bInt.toString(), this.pubRsaKey)) { // TODO: Verify whether correct positioning
                    System.out.printf((LOG_MESSAGE_SOLUTION_FOUND) + "%n", this.slice, aInt, bInt);
                    return new SolutionPayload(aInt, bInt);
                }
            }
        }
        System.out.printf((LOG_MESSAGE_NO_SOLUTION) + "%n", this.slice);
        return SolutionPayload.NO_SOLUTION;
    }
}
