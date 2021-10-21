package optimus.prime.rsa.main;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public final static String PUB_RSA_KEY = "";
    public final static int MASTER_SLICE_SIZE = 100;
    public final static int SLAVE_SLICE_SIZE = 10;

    public static void main(String[] args) throws IOException {
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

        NetworkConfiguration networkConfig;
        try {
            InetAddress masterAddress = InetAddress.getByName("10.10.10.10");
            networkConfig = new NetworkConfiguration(masterAddress);
        } catch (UnknownHostException e) {
            System.out.println("An error occured. " + e);
            return;
        }

        final List<Integer> primes = this.getPrimes();


        new Master(networkConfig);
    }

    public static List<BigInteger> getPrimes() throws IOException {
        List<BigInteger> primes = new ArrayList<>();

        final String fileName = "primes100.txt";
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
            System.out.println("An error occured. " + e);
        }

        return primes;
    }
}
