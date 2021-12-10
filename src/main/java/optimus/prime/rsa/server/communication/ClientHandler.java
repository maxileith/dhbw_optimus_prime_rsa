package optimus.prime.rsa.server.communication;

import optimus.prime.rsa.ConsoleColors;
import optimus.prime.rsa.Message;
import optimus.prime.rsa.MessageType;
import optimus.prime.rsa.payloads.HostsPayload;
import optimus.prime.rsa.payloads.MasterAddressPayload;
import optimus.prime.rsa.payloads.MissionPayload;
import optimus.prime.rsa.payloads.MissionResponsePayload;
import optimus.prime.rsa.server.Utils;
import optimus.prime.rsa.server.config.MasterConfiguration;
import optimus.prime.rsa.server.config.NetworkConfiguration;
import optimus.prime.rsa.server.config.StaticConfiguration;
import optimus.prime.rsa.server.crypto.RSAHelper;

import java.io.*;
import java.math.BigInteger;
import java.net.*;

public class ClientHandler implements Runnable {

    // as long as this variable is set, the client handler should
    // be running
    private boolean running = true;
    private ServerSocket serverSocket;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;

    private static ClientHandler instance;


    /**
     * this method creates a new instance of {@link ClientHandler}
     *
     * @return the only instance of {@link ClientHandler}
     */
    public synchronized static ClientHandler newInstance() {
        instance = new ClientHandler();
        return instance;
    }

    /**
     * this method returns the only instance of {@link ClientHandler}
     *
     * @return the only instance of {@link ClientHandler}
     */
    public synchronized static ClientHandler getInstance() {
        if (instance == null) {
            instance = new ClientHandler();
        }
        return instance;
    }

    /**
     * this method notifies the client about an update of
     * the host list
     */
    public synchronized void notifyHostListChanged() {
        HostsPayload hostsPayload = new HostsPayload(NetworkConfiguration.hosts);
        Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
        try {
            getInstance().objectOutputStream.writeObject(hostsMessage);
            getInstance().objectOutputStream.flush();
        } catch (NullPointerException | IOException e) {
            log("no client to send the host list to.");
        }
    }

    /**
     * private constructor to make the class singleton
     */
    private ClientHandler() {
        try {
            this.serverSocket = new ServerSocket(
                    StaticConfiguration.CLIENT_PORT,
                    1000,
                    InetAddress.getByName("0.0.0.0")
            );
            // set timeout to prevent getting stuck in the 'accept' method
            // of the server socket
            this.serverSocket.setSoTimeout(1000);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Method for the thread to execute.
     * Contains the main loop to communicate with a client
     */
    @Override
    public void run() {

        log("started");

        while (this.running) {
            try (
                    // a new client connected
                    Socket client = this.serverSocket.accept();
                    OutputStream outputStream = client.getOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                    InputStream inputStream = client.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)
            ) {
                this.objectOutputStream = objectOutputStream;
                this.objectInputStream = objectInputStream;
                log(client.getInetAddress().getHostAddress() + " established a connection");
                // check if this host is the master
                if (MasterConfiguration.isMaster) {
                    // this host is the master
                    // confirming that this host is the master
                    log("sending confirmation that this host is the master");
                    Message masterConfirmMessage = new Message(MessageType.MASTER_CONFIRM);
                    this.objectOutputStream.writeObject(masterConfirmMessage);
                    this.objectOutputStream.flush();

                    // telling the client which hosts are part of the distributed
                    // systeme as well
                    log("sending hosts list to the client");
                    HostsPayload hostsPayload = new HostsPayload(NetworkConfiguration.hosts);
                    Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
                    this.objectOutputStream.writeObject(hostsMessage);
                    this.objectOutputStream.flush();

                    // handling further communication with the client
                    this.handleClientAsMaster();
                } else {
                    // this host is a slave
                    // telling the client to connect to the actual master
                    log("forward client to master " + NetworkConfiguration.masterAddress.getHostAddress());
                    MasterAddressPayload masterAddressPayload = new MasterAddressPayload(NetworkConfiguration.masterAddress);
                    Message messageMasterAddress = new Message(MessageType.SLAVE_NOT_MASTER, masterAddressPayload);
                    this.objectOutputStream.writeObject(messageMasterAddress);
                    this.objectOutputStream.flush();
                }
            } catch (SocketTimeoutException ignored) {
                // timeout can be ignored and is totally fine, as you can tell
                // from the constructor
            } catch (IOException e) {
                if (this.running) {
                    // lost connection, but this.running is true
                    // --> log error and repeat the loop to wait for a new client
                    err("Lost connection to the client unexpectedly - " + e);
                }
            }
        }

        log("Thread terminated");
    }

    /**
     * handles the communication with the client if this host is
     * the master
     *
     * @throws IOException while reading an incoming object
     */
    private void handleClientAsMaster() throws IOException {

        while (this.running) {
            try {
                // waiting for a message
                Message message = (Message) this.objectInputStream.readObject();
                switch (message.getType()) {
                    case CLIENT_NEW_MISSION -> {
                        // client wants to submit a new mission
                        if (StaticConfiguration.CIPHER.equals("") && StaticConfiguration.PUB_RSA_KEY.equals(BigInteger.ZERO) && StaticConfiguration.primes == null) {
                            log("Message received - " + message.getType());

                            MissionPayload missionPayload = (MissionPayload) message.getPayload();
                            StaticConfiguration.CIPHER = missionPayload.getCipher();
                            StaticConfiguration.PUB_RSA_KEY = missionPayload.getPubKeyRsa();
                            StaticConfiguration.primes = missionPayload.getPrimes();

                            log("cipher: " + StaticConfiguration.CIPHER);
                            log("public key: " + StaticConfiguration.PUB_RSA_KEY);
                            log("primes length: " + StaticConfiguration.primes.size());
                            log("doing " + MasterConfiguration.MASTER_CHECKS_PER_SLICE_PER_WORKER + " checks per slice per worker");
                        } else {
                            // the master is already busy --> tell the client
                            // however, the client can wait for the solution of the current mission
                            // --> don't disconnect
                            log("refuse new mission - already busy");
                            Message masterConfirmMessage = new Message(MessageType.MASTER_BUSY);
                            this.objectOutputStream.writeObject(masterConfirmMessage);
                            this.objectOutputStream.flush();
                        }
                    }
                    case CLIENT_EXIT_ACKNOWLEDGE -> {
                        // client acknowledged exit
                        // return to main loop
                        log("Received exit acknowledgement. Closing connection.");
                        return;
                    }
                    default -> err("Unexpected message type - " + message.getType());
                }
            } catch (ClassNotFoundException e) {
                err("Class of incoming object unknown - " + e);
            }
        }
    }

    /**
     * inform the client about the solution
     */
    public synchronized void sendSolution() {
        log("Sending solution " + MasterConfiguration.solution + " to the client.");

        // gathering all information to send
        String text = null;
        // if there is a solution decrypt the cipher
        if (MasterConfiguration.solution != null) {
            RSAHelper rsaHelper = new RSAHelper();
            text = rsaHelper.decrypt(MasterConfiguration.solution.getPrime1().toString(), MasterConfiguration.solution.getPrime2().toString(), StaticConfiguration.CIPHER);
        }
        MissionResponsePayload missionResponsePayload = new MissionResponsePayload(MasterConfiguration.solution, text);
        Message solutionMessage = new Message(MessageType.MASTER_SOLUTION_FOUND, missionResponsePayload);

        // send the solution
        try {
            this.objectOutputStream.writeObject(solutionMessage);
            this.objectOutputStream.flush();
        } catch (NullPointerException | IOException e) {
            err("Failed to send the solution to the client - " + e);
        }
    }


    /**
     * stop the client handler thread
     */
    public synchronized void stop() {
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            log("error while waiting for the server socket to be closed - " + e);
        }
        this.running = false;
    }

    /**
     * Use to log
     *
     * @param s {@link String} to log
     */
    private static void log(String s) {
        System.out.println(ConsoleColors.UNDERLINE + "ClientHandler - " + s + ConsoleColors.RESET);
    }

    /**
     * Use to log errors
     *
     * @param s {@link String} to log as an error
     */
    private static void err(String s) {
        Utils.err(ConsoleColors.UNDERLINE + "ClientHandler - " + s + ConsoleColors.RESET);
    }
}
