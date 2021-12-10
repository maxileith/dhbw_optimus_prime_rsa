package optimus.prime.rsa.client;

import optimus.prime.rsa.Message;
import optimus.prime.rsa.MessageType;
import optimus.prime.rsa.argumentparser.ArgumentParser;
import optimus.prime.rsa.argumentparser.ArgumentBlueprint;
import optimus.prime.rsa.payloads.*;
import optimus.prime.rsa.server.config.StaticConfiguration;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

/**
 * class to start the client from
 */
public class Main {

    /**
     * Main loop for a client
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {

        // moves cursor to first row
        System.out.print("\033[H\033[2J");

        // specify the expected command line arguments
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

        // load the command line arguments
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

        MissionResponsePayload missionResponsePayload = null;
        List<InetAddress> hosts = null;
        boolean lostMaster = false;

        // loop that makes sure that we connect to the current master
        outerLoop:
        do {
            try (
                Socket socket = new Socket(masterAddress, port);
                InputStream inputStream = socket.getInputStream();
                ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                OutputStream outputStream = socket.getOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)
            ) {
                // receive
                do {
                    // wait for message
                    Message message = (Message) objectInputStream.readObject();
                    switch (message.getType()) {
                        case MASTER_CONFIRM -> {
                            // we are connected to the master,
                            // send the mission
                            System.out.println("Client - connected to the master " + masterAddress.getHostAddress());
                            // send the mission only if we are not reconnecting because the previous master was lost
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
                            // the server that we are connected to is not the master
                            InetAddress slaveIp = masterAddress;
                            MasterAddressPayload masterAddressPayload = (MasterAddressPayload) message.getPayload();
                            // update the masterAddress to the real ip-address of the master
                            masterAddress = masterAddressPayload.getMasterAddress();
                            System.out.println("Client - connected to the slave " + slaveIp.getHostAddress() + " - told us that the master is " + masterAddress.getHostAddress());
                            // jump back to the outerLoop (reconnect to the right server)
                            continue outerLoop;
                        }
                        case MASTER_SOLUTION_FOUND -> {
                            // save the solution
                            missionResponsePayload = (MissionResponsePayload) message.getPayload();
                            // tell the server that we are existing
                            Message missionMessage = new Message(MessageType.CLIENT_EXIT_ACKNOWLEDGE);
                            objectOutputStream.writeObject(missionMessage);
                            objectOutputStream.flush();
                        }
                        case MASTER_HOSTS_LIST -> {
                            // update the list of server in the distributed system
                            System.out.println("Client - update of hosts list received");
                            HostsPayload hostsPayload = (HostsPayload) message.getPayload();
                            hosts = hostsPayload.getHosts();
                        }
                        case MASTER_BUSY -> {
                            // master has already a mission --> tell the user
                            System.out.println("Client - Master is busy. The new mission was refused. Stay to receive the result of the current mission.");
                        }
                        default -> {}
                    }
                } while (missionResponsePayload == null);

            } catch (ClassNotFoundException | IOException e) {
                // lost connection to the master
                System.out.println("Client - Lost connection to master " + masterAddress.getHostAddress());
                lostMaster = true;
                try {
                    // the next host of the host list is the new master
                    // noinspection ConstantConditions
                    masterAddress = hosts.remove(0);
                    // wait for the new master to start
                    for (int i = 0; i < StaticConfiguration.MASTER_RESTART_TIMEOUT; i += 50) {
                        Thread.sleep(50);
                        System.out.print(".");
                    }
                    System.out.println();
                } catch (NullPointerException | IndexOutOfBoundsException f) {
                    // the hosts list is empty --> no more possible masters --> exit
                    System.err.println("Client - There are no known hosts left - " + f);
                    return;
                } catch (InterruptedException f) {
                    System.err.println("Client - error while waiting for the MASTER_RESTART_TIMEOUT to expire - " + f);
                }
            }

        } while (missionResponsePayload == null);

        // display the results
        System.out.println("Client - The solution is " + missionResponsePayload.getSolution());
        System.out.println("Client - The text is \"" + missionResponsePayload.getText() + "\"");
        System.out.println("Client - Bye :)");
    }
}
