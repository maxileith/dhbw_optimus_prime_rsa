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

    public Worker(int startIndex, int amount, List<BigInteger> primes) {
        this.startIndex = startIndex;
        this.amount = amount;
        this.primes = primes;
        this.rsaHelper = new RSAHelper();
    }

    @Override
    public SolutionPayload call() {
        for (int a = startIndex; a < startIndex + amount ; a++) {
            // Sollte später auf die reine Big Integer Variante gewechselt werden

            for (int b = 0; b < primes.size(); b++){
                BigInteger aInt = primes.get(a);
                BigInteger bInt = primes.get(b);
                if(rsaHelper.isValid(aInt.toString(), bInt.toString(), Main.PUB_RSA_KEY)) { // TODO: Verify whether correct positioning
                    return new SolutionPayload(aInt, bInt);
                }
            }
        }
        return SolutionPayload.NO_SOLUTION;
    }


}
