package optimus.prime.rsa.main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

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

        /*

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
        */

        System.out.println(getPrimes());
    }

    private static List<Integer> getPrimes() throws IOException {
        List<Integer> primes = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("ressources/primes100000.txt"))){
            while(true) {
                Integer value = Integer.parseInt(br.readLine());
                primes.add(value);
            }
        } catch (IOException e) {
            System.out.println("An error occured. " + e);
        } catch (NumberFormatException e) {
            System.out.println("Finished reading primes.");
        }

        return primes;
    }
}
