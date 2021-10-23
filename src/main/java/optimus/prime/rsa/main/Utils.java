package optimus.prime.rsa.main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Utils {
    public static Queue<Integer> getIndicesToDo(int numberOfPrimes, int sliceSize) {
        Queue<Integer> indices = new LinkedList<>();
        int lastIndex = numberOfPrimes - 1;
        for (int i = 0; i <= lastIndex; i += sliceSize) {
            indices.add(i);
        }
        return indices;
    }

    public static List<BigInteger> getPrimes() {
        List<BigInteger> primes = new ArrayList<>();

        final String fileName = "primes1000.txt";
        final InputStream stream = Main.class.getClassLoader().getResourceAsStream(fileName);

        if (stream == null) {
            return primes;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))){
            String line;
            while((line = br.readLine()) != null) {
                BigInteger value = new BigInteger(line);
                primes.add(value);
            }
        } catch (IOException e) {
            System.out.println("Couldn't load primes - " + e);
        }

        return primes;
    }
}
