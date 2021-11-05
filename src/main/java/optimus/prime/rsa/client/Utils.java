package optimus.prime.rsa.client;

import java.io.*;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Utils {

    public static List<BigInteger> getPrimes(String primeList) {
        Set<BigInteger> primes = new HashSet<>();

        final String fileName = "primes" + primeList + ".txt";
        InputStream stream = Main.class.getClassLoader().getResourceAsStream(fileName);

        if (stream == null) {
            File f = new File(primeList);
            try {
                stream = new FileInputStream(f);
            } catch (FileNotFoundException e) {
                System.err.println("Couldn't load primes - " + e);
                System.exit(1);
            }
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                BigInteger value = new BigInteger(line);
                primes.add(value);
            }
        } catch (IOException e) {
            System.err.println("Couldn't load primes - " + e);
            System.exit(1);
        }

        return primes.stream().toList();
    }
}
