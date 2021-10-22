package optimus.prime.rsa.crypto;

import optimus.prime.rsa.communication.payloads.SolutionPayload;
import optimus.prime.rsa.main.Main;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Callable;

public class Worker implements Callable<SolutionPayload> {
    private final int  amount;
    private final int startIndex;
    private final List<BigInteger> primes;

    private final RSAHelper rsaHelper;

    private final static String LOG_MESSAGE_SOLUTION_FOUND = "Worker - Found no solution in Block [%d:%d]";
    private final static String LOG_MESSAGE_NO_SOLUTION = "Worker - Found solution in Block [%d:%d] \n Solution is a:%d :d";
    private final static String LOG_START_INSPECTING = "Worker - Start inspecting block [%d:%d]";

    public Worker(int startIndex, int amount, List<BigInteger> primes) {
        this.startIndex = startIndex;
        this.amount = amount;
        this.primes = primes;
        this.rsaHelper = new RSAHelper();
    }

    @Override
    public SolutionPayload call() {
        int endIndex = startIndex + amount;
        System.out.println(String.format(LOG_START_INSPECTING, startIndex, endIndex));
        for (int a = startIndex; a < endIndex ; a++) {
            // Sollte später auf die reine Big Integer Variante gewechselt werden

            for (int b = 0; b < primes.size(); b++){
                BigInteger aInt = primes.get(a);
                BigInteger bInt = primes.get(b);
                if(rsaHelper.isValid(aInt.toString(), bInt.toString(), Main.PUB_RSA_KEY)) { // TODO: Verify whether correct positioning
                    System.out.println(String.format(LOG_MESSAGE_SOLUTION_FOUND, startIndex, endIndex, aInt, bInt));
                    return new SolutionPayload(aInt, bInt);
                }
            }
        }
        System.out.println(String.format(LOG_MESSAGE_NO_SOLUTION, startIndex, endIndex));
        return SolutionPayload.NO_SOLUTION;
    }


}
