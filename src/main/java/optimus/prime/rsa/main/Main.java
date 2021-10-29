package optimus.prime.rsa.main;

import optimus.prime.rsa.argumentparser.ArgumentParser;
import optimus.prime.rsa.argumentparser.ArgumentBlueprint;
import optimus.prime.rsa.communication.Master;
import optimus.prime.rsa.communication.Slave;
import optimus.prime.rsa.config.MasterConfiguration;
import optimus.prime.rsa.config.NetworkConfiguration;
import optimus.prime.rsa.config.StaticConfiguration;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Main {

    private static boolean DO_RESTART;

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
                        "master-slice-size",
                        false,
                        "master-only: defines the size of the task that is being delegated to a slave",
                        "100"
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

        ap.load(args);

        // load all ip addresses the host listens to
        try {
            NetworkConfiguration.ownAddresses = Utils.getOwnIPs();
            System.out.println("Main   - own ips: " + NetworkConfiguration.ownAddresses);
        } catch (SocketException e) {
            System.err.println("Main   - unable to load own ips - " + e);
            System.exit(1);
        }

        // master-address key
        InetAddress masterAddress = null;
        try {
            masterAddress = InetAddress.getByName(ap.get("master-address"));
        } catch (UnknownHostException ignored) {
            System.err.println("Main   - " + ap.get("master-address") + " is not a valid hostname / ip-address");
            System.exit(0);
        }
        NetworkConfiguration.masterAddress = masterAddress;

        // port key
        StaticConfiguration.PORT = Integer.parseInt(ap.get("port"));
        // workers key
        StaticConfiguration.SLAVE_WORKERS = Integer.parseInt(ap.get("workers"));
        // pub-rsa-key key
        MasterConfiguration.PUB_RSA_KEY = new BigInteger(ap.get("pub-rsa-key"));
        // cipher key
        MasterConfiguration.CIPHER = ap.get("cipher");
        // master-slice-size key
        MasterConfiguration.MASTER_SLICE_SIZE = Integer.parseInt(ap.get("master-slice-size"));
        // max-slaves key
        MasterConfiguration.MAX_INCOMING_SLAVES = Integer.parseInt(ap.get("max-slaves"));

        loop();

        System.out.println("Main   - Bye :)");
    }

    private static void loop() {
        do {
            DO_RESTART = false;

            MasterConfiguration.isMaster = NetworkConfiguration.ownAddresses.contains(NetworkConfiguration.masterAddress);

            // Start master if not slave
            Thread masterThread = null;
            if (MasterConfiguration.isMaster) {
                Master master = new Master();
                masterThread = new Thread(master);
                masterThread.start();

                System.out.println("Main   - To join a slave use arguments " + ConsoleColors.RED_UNDERLINED + "--master-address " + NetworkConfiguration.masterAddress + ConsoleColors.RESET);
            }

            Thread slaveThread = null;
            if (StaticConfiguration.SLAVE_WORKERS != 0) {
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
                System.err.println("Main   - failed to join threads - " + e);
                return;
            }
        } while (DO_RESTART);
    }

    public synchronized static void restart() {
        DO_RESTART = true;
    }
}
