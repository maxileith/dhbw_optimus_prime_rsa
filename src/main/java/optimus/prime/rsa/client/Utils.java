package optimus.prime.rsa.client;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A collection of static utility methods
 */
public class Utils {

    /**
     * Loads prime numbers
     *
     * @param primeList the name of the list of primes. either 100|1000|10000|100000 or a path to a .txt file.
     * @return a {@link List} of primes without duplicates
     */
    public static List<BigInteger> getPrimes(String primeList) {
        // use a HashSet to prevent duplicates
        Set<BigInteger> primes = new HashSet<>();

        // try to use primes file from resources first
        final String fileName = "primes" + primeList + ".txt";
        InputStream stream = Main.class.getClassLoader().getResourceAsStream(fileName);

        // if there is no resource, use primeList as a path to a file to load primes
        if (stream == null) {
            File f = new File(primeList);
            try {
                stream = new FileInputStream(f);
            } catch (FileNotFoundException e) {
                System.err.println("Couldn't load primes - " + e);
                System.exit(1);
            }
        }

        // read the file line by line and insert the prime into the HashSet
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

        // return primes as a list
        return new ArrayList<>(primes);
    }
}
