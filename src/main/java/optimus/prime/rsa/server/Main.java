package optimus.prime.rsa.server;

import optimus.prime.rsa.argumentparser.ArgumentParser;
import optimus.prime.rsa.argumentparser.ArgumentBlueprint;
import optimus.prime.rsa.server.communication.ClientHandler;
import optimus.prime.rsa.server.communication.Master;
import optimus.prime.rsa.server.communication.Slave;
import optimus.prime.rsa.server.config.MasterConfiguration;
import optimus.prime.rsa.server.config.NetworkConfiguration;
import optimus.prime.rsa.server.config.SlaveConfiguration;
import optimus.prime.rsa.server.config.StaticConfiguration;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;

/**
 * class to start the server from
 */
public class Main {

    private static boolean LOST_MASTER = false;

    /**
     * Main loop for a server
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {

        // moves cursor to first row
        System.out.print("\033[H\033[2J");

        // specifying the expected command line arguments
        ArgumentParser ap = new ArgumentParser();
        ap.addArgument(
                new ArgumentBlueprint(
                        "master-address",
                        false,
                        "defines the ip-address of the current master",
                        "localhost"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "master-checks-per-slice-per-worker",
                        false,
                        "master-only: defines the number of checks per slice per worker",
                        "150000"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "intra-port",
                        false,
                        "defines the TCP port to use for communication",
                        "2504"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "client-port",
                        false,
                        "defines the TCP port to use for communication with the client",
                        "2505"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "workers",
                        false,
                        "defines the number of the threads that are used to crack the key",
                        Integer.toString(Math.max(Runtime.getRuntime().availableProcessors() - 1, 1))
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "max-slaves",
                        false,
                        "master-only: defines how many slaves can connect to the master",
                        "1000"
                )
        );

        // load the command line arguments
        ap.load(args);

        // load all ip addresses the host listens to
        try {
            NetworkConfiguration.ownAddresses = Utils.getOwnIPs();
            System.out.println("Main   - own ips: ");
            NetworkConfiguration.ownAddresses.forEach(e -> System.out.println(e.getHostAddress()));
        } catch (SocketException e) {
            Utils.err("Main          - unable to load own ips - " + e);
            System.exit(1);
        }

        // master-address key
        InetAddress masterAddress = null;
        try {
            masterAddress = InetAddress.getByName(ap.get("master-address"));
        } catch (UnknownHostException ignored) {
            Utils.err("Main          - " + ap.get("master-address") + " is not a valid hostname / ip-address");
            System.exit(0);
        }
        NetworkConfiguration.masterAddress = masterAddress;

        // intra-port key
        StaticConfiguration.PORT = Integer.parseInt(ap.get("intra-port"));
        // client-port key
        StaticConfiguration.CLIENT_PORT = Integer.parseInt(ap.get("client-port"));
        // workers key
        SlaveConfiguration.WORKERS = Integer.parseInt(ap.get("workers"));
        // master-slice-size key
        MasterConfiguration.MASTER_CHECKS_PER_SLICE_PER_WORKER = Long.parseLong(ap.get("master-checks-per-slice-per-worker"));
        // max-slaves key
        MasterConfiguration.MAX_INCOMING_SLAVES = Integer.parseInt(ap.get("max-slaves"));

        // the loop that makes sure that after one mission is solved,
        // the next one can be submitted, by restarting all threads.
        // noinspection InfiniteLoopStatement
        while (true) {
            // getting the client handler
            ClientHandler clientHandler = ClientHandler.newInstance();
            Thread clientHandlerThread = new Thread(clientHandler);
            System.out.println("Main          - starting client handler ...");
            clientHandlerThread.start();

            // starting the loop that solves one mission
            loop();
            // mission is solved by this point in time

            // stopping the client handler
            System.out.println("Main          - stopping client handler ...");
            clientHandler.stop();
            System.out.println("Main          - waiting for the client handler to terminate ...");
            try {
                clientHandlerThread.join();
            } catch (InterruptedException e) {
                System.out.println("Main          - error while waiting for the client handler to terminate - " + e);
            }

            // resetting everything to start all over again
            MasterConfiguration.solution = null;
            MasterConfiguration.startMillis = 0;
            MasterConfiguration.lostSlices = new LinkedList<>();
            MasterConfiguration.currentSliceStart = 0;

            StaticConfiguration.PUB_RSA_KEY = BigInteger.ZERO;
            StaticConfiguration.primes = null;
            StaticConfiguration.CIPHER = "";

            // if this host is a slave wait a little for the master
            try {
                for (int i = 0; i < 1000 && !MasterConfiguration.isMaster; i += 50) {
                    // noinspection BusyWait
                    Thread.sleep(50);
                    System.out.print(".");
                }
                System.out.println();
            } catch (InterruptedException e) {
                Utils.err("Main          - error while waiting for the restart timeout to expire - " + e);
            }
        }
    }

    /**
     * In this loop, an entire mission will be solved
     */
    private static void loop() {
        do {

            // update masterAddress if master is lost
            // just use the next address of the hosts list
            if (LOST_MASTER) {
                try {
                    NetworkConfiguration.masterAddress = NetworkConfiguration.hosts.remove(0);
                } catch (IndexOutOfBoundsException | NullPointerException e) {
                    Utils.err("Main   - There are no known hosts left - " + e);
                    return;
                }
            }

            // check if this host is the master
            MasterConfiguration.isMaster = NetworkConfiguration.ownAddresses.contains(NetworkConfiguration.masterAddress);
            // give the new master some time to start
            if (!MasterConfiguration.isMaster) {
                System.out.println("Main   - The new master is " + NetworkConfiguration.masterAddress.getHostAddress());
                System.out.println("Main   - Waiting " + StaticConfiguration.MASTER_RESTART_TIMEOUT + "ms for the new master to start ...");
                try {
                    for (int i = 0; i < StaticConfiguration.MASTER_RESTART_TIMEOUT; i += 50) {
                        // noinspection BusyWait
                        Thread.sleep(50);
                        System.out.print(".");
                    }
                    System.out.println();
                } catch (InterruptedException e) {
                    Utils.err("Main          - error while waiting for the MASTER_RESTART_TIMEOUT to expire - " + e);
                }
                System.out.println("Main          - Starting Slave again ...");
            } else if (LOST_MASTER) {
                System.out.println("Main          - This host is the new master.");
                System.out.println("Main          - Starting Master and Slave ...");
            }

            // start or restart done
            LOST_MASTER = false;

            // Start master if not slave
            Thread masterThread = null;
            if (MasterConfiguration.isMaster) {
                Master master = new Master();
                masterThread = new Thread(master);
                masterThread.start();
            }

            // Start slave as long as there should be workers (!= 0)
            Thread slaveThread = null;
            if (SlaveConfiguration.WORKERS != 0) {
                Slave slave = new Slave();
                slaveThread = new Thread(slave);
                slaveThread.start();
            } else {
                System.out.println("Main          - Creating no slave because workers are set to 0");
            }

            try {
                if (slaveThread != null) {
                    slaveThread.join();
                }
                if (masterThread != null) {
                    masterThread.join();
                }
            } catch (InterruptedException e) {
                Utils.err("Main          - failed to join threads - " + e);
                return;
            }
        } while (LOST_MASTER); // do it again, if the current master is lost
    }

    /**
     * Endpoint for the slave to use, if he notices that the master is lost.
     */
    public synchronized static void reportMasterLost() {
        LOST_MASTER = true;
    }
}
