package optimus.prime.rsa.main;

import optimus.prime.rsa.communication.Master;
import optimus.prime.rsa.communication.Slave;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public final static String PUB_RSA_KEY = "268342277565109549360836262560222031507";
    public final static String CHIFFRE = "2d80afa14a65a7bf26636f97c89b43d5";
    public final static int MASTER_SLICE_SIZE = 100;

    public static void main(String[] args) {
        /*
        --master=192.169.2.40
        --file_with_primes=<file-path>
         */

        /*
        Slave Communication thread
            - Eingehende und Ausgehende Kommunikation zuständig
        Master Communication Thread
            - Server Sockets öffnen
        Master thread
            - Einteilung der Arbeitspakete -> sendet an Communication Thread -> welcher dann die Arbeitspakete an Slaves sendet
        Slave thread
            - Bearbeitet die Arbeitspakete
            - Ergebnis wird an Communicatin Thread gegeben, der die Nachricht an den Master sendet
        */

        /*try {
            String adr = Inet4Address.getLocalHost().getHostAddress();
            InetAddress ownAddress = InetAddress.getByName(adr);
        } catch (UnknownHostException e) {
            System.out.println("An error occured. " + e);
            return;
        }*/

        InetAddress masterAddress = null;
        boolean isSlave = false;
        // FIXME: Add path to prime list
        // -m 10.10.10.10 -s

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-m" -> {
                    i++;
                    if (i >= args.length) {
                        System.err.println("Not enough arguments supplied! Please supply a master-address");
                        return;
                    }
                    try {
                        masterAddress = InetAddress.getByName(args[i]);
                    } catch (UnknownHostException e) {
                        System.err.println("An error occurred. " + e);
                        return;
                    }
                }
                case "-s" -> isSlave = true;
                default -> {
                    System.err.println("Unknown argument " + args[i]);
                    return;
                }
            }
        }

        // Make sure that masterAddress is set
        if (masterAddress == null) {
            System.err.println("Master-Address is required.");
            return;
        }

        // load primes
        final List<BigInteger> primes = Utils.getPrimes();

        NetworkConfiguration networkConfig = new NetworkConfiguration(masterAddress);

        // Start master if not slave
        Thread masterThread = null;
        if (!isSlave) {
            Master master = new Master(networkConfig, primes);
            masterThread = new Thread(master);
            masterThread.start();

            System.out.println("To join a slave use -m " + networkConfig.getMasterAddress());
        }

        Slave slave = new Slave(networkConfig, primes);
        Thread slaveThread = new Thread(slave);
        slaveThread.start();

        try {
            slaveThread.join();
            if (masterThread != null) {
                masterThread.join();
            }
        } catch (InterruptedException e) {
            System.err.println("Main   - failed to join threads - " + e);
        }

        System.out.println("Bye :)");
    }
}
