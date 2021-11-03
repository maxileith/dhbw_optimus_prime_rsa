package optimus.prime.rsa.server;

import optimus.prime.rsa.argumentparser.ArgumentParser;
import optimus.prime.rsa.argumentparser.ArgumentBlueprint;
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
import java.util.concurrent.TimeUnit;

public class Main {

    private static boolean LOST_MASTER = false;

    public static void main(String[] args) {

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
                        "master-only: defines the number of checks per slice",
                        "150000"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "port",
                        false,
                        "defines the TCP port",
                        "2504"
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
                        "pub-rsa-key",
                        true,
                        "master-only: defines the public-key to crack",
                        ""
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "cipher",
                        true,
                        "master-only: defines encrypted payload to decrypt",
                        ""
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
        ap.addArgument(
                new ArgumentBlueprint(
                        "primes",
                        true,
                        "master-only: defines the prime list to use",
                        "100"
                )
        );

        ap.load(args);

        // load all ip addresses the host listens to
        try {
            NetworkConfiguration.ownAddresses = Utils.getOwnIPs();
            System.out.println("Main   - own ips: ");
            NetworkConfiguration.ownAddresses.forEach(e -> System.out.println(e.getHostAddress()));
        } catch (SocketException e) {
            Utils.err("Main   - unable to load own ips - " + e);
            System.exit(1);
        }

        // master-address key
        InetAddress masterAddress = null;
        try {
            masterAddress = InetAddress.getByName(ap.get("master-address"));
        } catch (UnknownHostException ignored) {
            Utils.err("Main   - " + ap.get("master-address") + " is not a valid hostname / ip-address");
            System.exit(0);
        }
        NetworkConfiguration.masterAddress = masterAddress;

        // port key
        StaticConfiguration.PORT = Integer.parseInt(ap.get("port"));
        // workers key
        SlaveConfiguration.WORKERS = Integer.parseInt(ap.get("workers"));
        // pub-rsa-key key
        try {
            StaticConfiguration.PUB_RSA_KEY = new BigInteger(ap.get("pub-rsa-key"));
        } catch (NumberFormatException ignored) {
            StaticConfiguration.PUB_RSA_KEY = BigInteger.ZERO;
        }
        // cipher key
        StaticConfiguration.CIPHER = ap.get("cipher");
        // master-slice-size key
        MasterConfiguration.MASTER_CHECKS_PER_SLICE_PER_WORKER = Long.parseLong(ap.get("master-checks-per-slice-per-worker"));
        // max-slaves key
        MasterConfiguration.MAX_INCOMING_SLAVES = Integer.parseInt(ap.get("max-slaves"));

        loop(ap.get("primes"));

        System.out.println("Main   - Bye :)");
    }

    private static void loop(String primeList) {
        do {

            // update masterAddress if master is lost
            if (LOST_MASTER) {
                try {
                    NetworkConfiguration.masterAddress = NetworkConfiguration.hosts.remove(0);
                } catch (IndexOutOfBoundsException e) {
                    Utils.err("Main   - There are no known hosts left - " + e);
                    return;
                }
            }

            MasterConfiguration.isMaster = NetworkConfiguration.ownAddresses.contains(NetworkConfiguration.masterAddress);
            // give the new master some time to start
            if (!MasterConfiguration.isMaster && LOST_MASTER) {
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
                    Utils.err("Main   - error while waiting for the MASTER_RESTART_TIMEOUT to expire - " + e);
                }
                System.out.println("Main   - Starting Slave again ...");
            } else if (LOST_MASTER) {
                System.out.println("Main   - This host is the new master.");
                System.out.println("Main   - Starting Master and Slave ...");
            }

            // start or restart done
            LOST_MASTER = false;

            // Start master if not slave
            Thread masterThread = null;
            if (MasterConfiguration.isMaster) {
                Master master = new Master(primeList);
                masterThread = new Thread(master);
                masterThread.start();
            }

            Thread slaveThread = null;
            if (SlaveConfiguration.WORKERS != 0) {
                Slave slave = new Slave();
                slaveThread = new Thread(slave);
                slaveThread.start();
            } else {
                System.out.println("Main   - Creating no slave because workers are set to 0");
            }

            try {
                if (slaveThread != null) {
                    slaveThread.join();
                }
                if (masterThread != null) {
                    masterThread.join();
                }
            } catch (InterruptedException e) {
                Utils.err("Main   - failed to join threads - " + e);
                return;
            }
        } while (LOST_MASTER);
    }

    public synchronized static void reportMasterLost() {
        LOST_MASTER = true;
    }
}
