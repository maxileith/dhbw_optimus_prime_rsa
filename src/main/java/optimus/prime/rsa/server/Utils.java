package optimus.prime.rsa.server;

import optimus.prime.rsa.ConsoleColors;
import optimus.prime.rsa.payloads.SlicePayload;

import java.awt.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.List;

/**
 * A collection of static utility methods
 */
public class Utils {

    /**
     * This method splits up a major slice into multiple minor slices
     * 
     * @param majorSlice the slice that has to be split up
     * @param n number minor of slices
     * @return returns a {@link java.util.Queue} of minor slices
     */
    public static Queue<SlicePayload> getNSlices(SlicePayload majorSlice, int n) {

        int start = majorSlice.getStart();
        int end = majorSlice.getEnd();

        Queue<SlicePayload> slices = new LinkedList<>();
        // calculate how big each minor slice is
        double stepSize = (end - start + 1) / (double) n;

        double desiredPosition = start;
        int currentStart = start;
        int currentEnd;

        do {
            desiredPosition += stepSize;

            if (--n == 0) {
                currentEnd = end;
            } else {
                // round to an index that actually exists (non-floating point number)
                currentEnd = (int) Math.round(desiredPosition);
                // current end is at least at current start
                currentEnd = Math.max(currentEnd, currentStart);
                // current end must be smaller or equal to end
                currentEnd = Math.min(currentEnd, end);
            }

            // create the new slice and add it to the queue
            SlicePayload slice = new SlicePayload(currentStart, currentEnd);
            slices.add(slice);

            // the next start should be one behind the current end
            currentStart = currentEnd + 1;
        } while (currentEnd != end); // do it as long as the end is not reached

        // return the queue of slices
        return slices;
    }

    /**
     * This method returns all ip-addresses of this host
     *
     * @return a {@link List} of ip-addresses that belong to this host
     * @throws SocketException if the host has no configured network interfaces
     */
    public static List<InetAddress> getOwnIPs() throws SocketException {
        // list where all ip-addresses are saved
        final ArrayList<InetAddress> ips = new ArrayList<>();

        Enumeration<NetworkInterface> interfaces;
        interfaces = NetworkInterface.getNetworkInterfaces();

        // iterate through all interfaces
        for (NetworkInterface networkInterface : Collections.list(interfaces)) {
            Enumeration<InetAddress> networkInterfaceIPs = networkInterface.getInetAddresses();
            // add all ip-addresses of this network interface to the list
            ips.addAll(Collections.list(networkInterfaceIPs));
        }

        // return the list of ip-addresses
        return ips;
    }

    /**
     * wrapper method that prints errors red. without that wrapper function
     * errors are printed in the default color when exporting as jar, e.g.
     * white in Windows Terminal
     *
     * @param s {@link String} to log as an error
     */
    public static void err(String s) {
        Toolkit.getDefaultToolkit().beep();
        System.err.println(ConsoleColors.RED + s + ConsoleColors.RESET);
    }
}
