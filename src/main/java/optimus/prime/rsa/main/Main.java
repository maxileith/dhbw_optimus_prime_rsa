package optimus.prime.rsa.main;

import optimus.prime.rsa.argumentparser.ArgumentParser;
import optimus.prime.rsa.argumentparser.ArgumentBlueprint;
import optimus.prime.rsa.communication.Master;
import optimus.prime.rsa.communication.Slave;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        ArgumentParser ap = new ArgumentParser();
        ap.addArgument(
                new ArgumentBlueprint(
                        "master",
                        false,
                        "defines if this host is the master (true / false)",
                        "false"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "master-address",
                        false,
                        "defines the ip-address of the current master",
                        "127.0.0.1"
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
                        "7"
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
                        "chiffre",
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

        // master key
        boolean isMaster = ap.get("master").equals("true");

        // master-address key
        InetAddress masterAddress = null;
        try {
            masterAddress = InetAddress.getByName(ap.get("master-address"));
        } catch (UnknownHostException ignored) {
            System.err.println(ap.get("master-address") + " is not a valid hostname / ip-address");
            System.exit(0);
        }
        NetworkConfiguration networkConfig = new NetworkConfiguration(masterAddress);

        // port key
        StaticConfiguration.PORT = Integer.parseInt(ap.get("port"));

        // workers key
        StaticConfiguration.SLAVE_WORKERS = Integer.parseInt(ap.get("workers"));

        if (isMaster) {
            // pub-rsa-key key
            MasterConfiguration.PUB_RSA_KEY = ap.get("pub-rsa-key");
            // chiffre key
            MasterConfiguration.CHIFFRE = ap.get("chiffre");
            // master-slice-size key
            MasterConfiguration.MASTER_SLICE_SIZE = Integer.parseInt(ap.get("master-slice-size"));
            // max-slaves key
            MasterConfiguration.MAX_INCOMING_SLAVES = Integer.parseInt(ap.get("max-slaves"));
        }

        // Start master if not slave
        Thread masterThread = null;
        if (isMaster) {
            Master master = new Master(networkConfig);
            masterThread = new Thread(master);
            masterThread.start();

            System.out.println("To join a slave use -m " + networkConfig.getMasterAddress());
        }

        Slave slave = new Slave(networkConfig);
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
