package optimus.prime.rsa.main;

import optimus.prime.rsa.communication.payloads.SlicePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;

public class Utils {

    public static Queue<SlicePayload> getSlices(int numberOfPrimes, int start, int end, long checksPerSlice) {
        final Queue<SlicePayload> slices = new LinkedList<>();

        int currentStart = start;
        int currentEnd = 0;

        while (currentEnd != end) {
            // Don't worry if you don't understand the following line of code.
            // You need to reed the documentation to understand the derivation
            // of this mathematical formula.
            currentEnd = numberOfPrimes - (int) Math.sqrt(Math.pow(numberOfPrimes - currentStart, 2) - 2 * checksPerSlice);
            currentEnd = Math.min(currentEnd, end);
            SlicePayload slice = new SlicePayload(currentStart, currentEnd);
            slices.add(slice);
            currentStart = currentEnd + 1;
        }

        return slices;
    }

    public static Queue<SlicePayload> getNSlices(int start, int end, int n) {
        int stepSize = (int) Math.ceil((float) (end - start + 1) / n);
        Queue<SlicePayload> slices = new LinkedList<>();
        for (int i = start; i <= end; i += stepSize) {
            int sliceEnd = Math.min(i + stepSize - 1, end);
            SlicePayload slice = new SlicePayload(i, sliceEnd);
            slices.add(slice);
        }
        return slices;
    }

    public static List<BigInteger> getPrimes(String primeList) {
        List<BigInteger> primes = new ArrayList<>();

        final String fileName = "primes" + primeList + ".txt";
        final InputStream stream = Main.class.getClassLoader().getResourceAsStream(fileName);

        // stream could be null: catching NullPointerException
        // noinspection ConstantConditions
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                BigInteger value = new BigInteger(line);
                // don't load duplicates
                if (!primes.contains(value)) {
                    primes.add(value);
                }
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Couldn't load primes - " + e);
            System.exit(1);
        }

        return primes;
    }

    public static List<InetAddress> getOwnIPs() throws SocketException {
        final ArrayList<InetAddress> ips = new ArrayList<>();

        Enumeration<NetworkInterface> interfaces;
        interfaces = NetworkInterface.getNetworkInterfaces();

        for (NetworkInterface networkInterface : Collections.list(interfaces)) {
            Enumeration<InetAddress> networkInterfaceIPs = networkInterface.getInetAddresses();
            ips.addAll(Collections.list(networkInterfaceIPs));
        }

        return ips;
    }
}
