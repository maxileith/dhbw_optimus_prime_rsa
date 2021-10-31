package optimus.prime.rsa.server;

import optimus.prime.rsa.server.communication.payloads.SlicePayload;

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
        int currentEnd;

        do {
            // Don't worry if you don't understand the following line of code.
            // You need to reed the documentation to understand the derivation
            // of this mathematical formula.
            currentEnd = numberOfPrimes - (int) Math.round(Math.sqrt(Math.pow(numberOfPrimes - currentStart, 2) - 2 * checksPerSlice));
            // current end is at least at current start
            currentEnd = Math.max(currentEnd, currentStart);
            // current end must be smaller or equal to end
            currentEnd = Math.min(currentEnd, end);
            SlicePayload slice = new SlicePayload(currentStart, currentEnd);
            slices.add(slice);
            currentStart = currentEnd + 1;
        } while (currentEnd != end);

        return slices;
    }

    public static Queue<SlicePayload> getNSlices(int start, int end, int n) {
        Queue<SlicePayload> slices = new LinkedList<>();
        double stepSize = (end - start + 1) / (double) n;

        double desiredPosition = start;
        int currentStart = start;
        int currentEnd;

        do {
            desiredPosition += stepSize;

            if (--n == 0) {
                currentEnd = end;
            } else {
                currentEnd = (int) Math.round(desiredPosition);
                // current end is at least at current start
                currentEnd = Math.max(currentEnd, currentStart);
                // current end must be smaller or equal to end
                currentEnd = Math.min(currentEnd, end);
            }

            SlicePayload slice = new SlicePayload(currentStart, currentEnd);
            slices.add(slice);

            currentStart = currentEnd + 1;
        } while (currentEnd != end);

        return slices;
    }

    public static List<BigInteger> getPrimes(String primeList) {
        Set<BigInteger> primes = new HashSet<>();

        final String fileName = "primes" + primeList + ".txt";
        final InputStream stream = Main.class.getClassLoader().getResourceAsStream(fileName);

        // stream could be null: catching NullPointerException
        // noinspection ConstantConditions
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

        return primes.stream().toList();
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