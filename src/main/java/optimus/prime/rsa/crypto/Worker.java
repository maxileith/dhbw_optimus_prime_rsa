package optimus.prime.rsa.crypto;

import java.util.List;

public class Worker implements Runnable {
    private final int startIndex;
    private final int amount;
    private final List<Integer> primes;

    public Worker(int startIndex, int amount, List<Integer> primes) {
        this.startIndex = startIndex;
        this.amount = amount;
        this.primes = primes;
    }

    @Override
    public void run() {
        for (int i = startIndex; i < amount; i++) {
            for (int a = 0; a < primes.size(); a++) {

            }
        }
    }


}
