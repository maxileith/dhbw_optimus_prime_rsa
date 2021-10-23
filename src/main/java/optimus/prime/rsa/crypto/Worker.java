package optimus.prime.rsa.crypto;

import optimus.prime.rsa.communication.payloads.SlicePayload;
import optimus.prime.rsa.communication.payloads.SolutionPayload;
import optimus.prime.rsa.main.Main;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Callable;

public class Worker implements Callable<SolutionPayload> {
    private final SlicePayload slice;
    private final List<BigInteger> primes;

    private final RSAHelper rsaHelper;

    private final static String LOG_MESSAGE_NO_SOLUTION = "Worker - Found no solution in slice %s";
    private final static String LOG_MESSAGE_SOLUTION_FOUND = "Worker - Found solution in slice %s - Solution is a:%d b:%d";
    private final static String LOG_START_INSPECTING = "Worker - Start inspecting slice %s";

    public Worker(SlicePayload slice, List<BigInteger> primes) {
        this.slice = slice;
        this.primes = primes;
        this.rsaHelper = new RSAHelper();
    }

    @Override
    public SolutionPayload call() {
        System.out.printf((LOG_START_INSPECTING) + "%n", this.slice);
        for (int a = slice.getStart(); a <= slice.getEnd(); a++) {
            // Sollte später auf die reine Big Integer Variante gewechselt werden
            for (BigInteger bInt : primes) {
                BigInteger aInt = primes.get(a);
                if (rsaHelper.isValid(aInt.toString(), bInt.toString(), Main.PUB_RSA_KEY)) { // TODO: Verify whether correct positioning
                    System.out.printf((LOG_MESSAGE_SOLUTION_FOUND) + "%n", this.slice, aInt, bInt);
                    return new SolutionPayload(aInt, bInt);
                }
            }
        }
        System.out.printf((LOG_MESSAGE_NO_SOLUTION) + "%n", this.slice);
        return SolutionPayload.NO_SOLUTION;
    }
}
