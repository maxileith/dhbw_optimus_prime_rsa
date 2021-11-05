package optimus.prime.rsa.client;

import optimus.prime.rsa.Message;
import optimus.prime.rsa.MessageType;
import optimus.prime.rsa.argumentparser.ArgumentParser;
import optimus.prime.rsa.argumentparser.ArgumentBlueprint;
import optimus.prime.rsa.payloads.HostsPayload;
import optimus.prime.rsa.payloads.MasterAddressPayload;
import optimus.prime.rsa.payloads.MissionPayload;
import optimus.prime.rsa.payloads.SolutionPayload;
import optimus.prime.rsa.server.config.StaticConfiguration;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        // moves cursor to first row
        System.out.print("\033[H\033[2J");

        ArgumentParser ap = new ArgumentParser();
        ap.addArgument(
                new ArgumentBlueprint(
                        "ip-address",
                        true,
                        "defines the ip-address of a node"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "port",
                        false,
                        "defines the TCP port to use for communication with the master",
                        "2505"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "pub-rsa-key",
                        true,
                        "defines the public-key to crack"
                )
        );
        ap.addArgument(
                new ArgumentBlueprint(
                        "cipher",
                        true,
                        "defines encrypted payload to decrypt"
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

        InetAddress masterAddress = null;
        try {
            masterAddress = InetAddress.getByName(ap.get("ip-address"));
        } catch (UnknownHostException ignored) {
            System.err.println("Client - " + ap.get("ip-address") + " is not a valid hostname / ip-address");
            System.exit(0);
        }

        int port = Integer.parseInt(ap.get("port"));
        String cipher = ap.get("cipher");
        List<BigInteger> primes = Utils.getPrimes(ap.get("primes"));

        BigInteger pubKeyRsa;
        try {
            pubKeyRsa = new BigInteger(ap.get("pub-rsa-key"));
        } catch (NumberFormatException ignored) {
            System.err.println("Client - the public key is not valid");
            return;
        }

        SolutionPayload solution = null;
        List<InetAddress> hosts = null;
        boolean lostMaster = false;

        // loop that makes sure that we connect to the current
        // master
        do {
            try (
                Socket socket = new Socket(masterAddress, port);
                InputStream inputStream = socket.getInputStream();
                ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                OutputStream outputStream = socket.getOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            ) {
                // receive
                do {
                    Message message = (Message) objectInputStream.readObject();
                    switch (message.getType()) {
                        case MASTER_CONFIRM -> {
                            // we are connected to the master,
                            // send the mission
                            System.out.println("Client - connected to the master " + masterAddress.getHostAddress());
                            if (!lostMaster) {
                                System.out.println("Client - send new Mission");
                                Message missionMessage = new Message(MessageType.CLIENT_NEW_MISSION, new MissionPayload(
                                        pubKeyRsa,
                                        cipher,
                                        primes
                                ));
                                objectOutputStream.writeObject(missionMessage);
                                objectOutputStream.flush();
                            } else {
                                lostMaster = false;
                            }
                        }
                        case SLAVE_NOT_MASTER -> {
                            InetAddress slaveIp = masterAddress;
                            MasterAddressPayload masterAddressPayload = (MasterAddressPayload) message.getPayload();
                            masterAddress = masterAddressPayload.getMasterAddress();
                            System.out.println("Client - connected to the slave " + slaveIp.getHostAddress() + " - told us that the master is " + masterAddress.getHostAddress());
                        }
                        case MASTER_SOLUTION_FOUND -> {
                            solution = (SolutionPayload) message.getPayload();
                        }
                        case MASTER_HOSTS_LIST -> {
                            HostsPayload hostsPayload = (HostsPayload) message.getPayload();
                            hosts = hostsPayload.getHosts();
                        }
                        default -> {}
                    }
                } while (solution == null);

            } catch (ClassNotFoundException | IOException e) {
                System.out.println("Client - Lost connection to master " + masterAddress.getHostAddress());
                lostMaster = true;
                try {
                    // noinspection ConstantConditions
                    masterAddress = hosts.remove(0);
                    for (int i = 0; i < StaticConfiguration.MASTER_RESTART_TIMEOUT; i += 50) {
                        // noinspection BusyWait
                        Thread.sleep(50);
                        System.out.print(".");
                    }
                    System.out.println();
                } catch (NullPointerException | IndexOutOfBoundsException f) {
                    System.err.println("Client - There are no known hosts left - " + f);
                    return;
                } catch (InterruptedException f) {
                    System.err.println("Client - error while waiting for the MASTER_RESTART_TIMEOUT to expire - " + f);
                }
            }

        } while (solution == null);

        System.out.println("Client - The solution is " + solution);
        System.out.println("Client - Bye :)");
    }
}
