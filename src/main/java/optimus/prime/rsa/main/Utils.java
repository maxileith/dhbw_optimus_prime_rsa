package optimus.prime.rsa.main;

import optimus.prime.rsa.communication.payloads.SlicePayload;

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

    // TODO: maybe make an algorithm that makes slices that are equally hard to calculate

    public static Queue<SlicePayload> getSlices(int start, int end, int stepSize) {
        Queue<SlicePayload> slices = new LinkedList<>();
        for (int i = start; i <= end; i += stepSize) {
            int sliceEnd = Math.min(i + stepSize - 1, end);
            SlicePayload slice = new SlicePayload(i, sliceEnd);
            slices.add(slice);
        }
        return slices;
    }

    public static Queue<SlicePayload> getNSlices(int start, int end, int n) {
        int stepSize = (int) Math.ceil((float) (end - start + 1) / n);
        return getSlices(start, end, stepSize);
    }

    public static List<BigInteger> getPrimes() {
        List<BigInteger> primes = new ArrayList<>();

        // TODO: don't hardcode the primes file
        final String fileName = "primes7000.txt";
        final InputStream stream = Main.class.getClassLoader().getResourceAsStream(fileName);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                BigInteger value = new BigInteger(line);
                primes.add(value);
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Couldn't load primes - " + e);
            System.exit(1);
        }

        return primes;
    }
}
