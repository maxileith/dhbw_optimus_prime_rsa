package optimus.prime.rsa.server.communication;

import optimus.prime.rsa.ConsoleColors;
import optimus.prime.rsa.Message;
import optimus.prime.rsa.MessageType;
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

    private boolean running = true;
    private ServerSocket serverSocket;
    private ObjectOutputStream objectOutputStream;
    private ObjectInputStream objectInputStream;

    public ClientHandler() {
        try {
            this.serverSocket = new ServerSocket(
                    StaticConfiguration.CLIENT_PORT,
                    1000,
                    InetAddress.getByName("0.0.0.0")
            );
            this.serverSocket.setSoTimeout(1000);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void run() {

        log("started");

        while (this.running) {
            try (
                    Socket client = this.serverSocket.accept();
                    OutputStream outputStream = client.getOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                    InputStream inputStream = client.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)
            ) {
                this.objectOutputStream = objectOutputStream;
                this.objectInputStream = objectInputStream;
                log(client.getInetAddress().getHostAddress() + " established a connection");
                if (MasterConfiguration.isMaster) {
                    // this host is the master
                    log("sending confirmation that this host is the master");

                    Message masterConfirmMessage = new Message(MessageType.MASTER_CONFIRM);
                    this.objectOutputStream.writeObject(masterConfirmMessage);
                    this.objectOutputStream.flush();

                    this.handleClientAsMaster();
                } else {
                    // this host is a slave
                    log("forward client to master " + NetworkConfiguration.masterAddress.getHostAddress());
                    MasterAddressPayload masterAddressPayload = new MasterAddressPayload(NetworkConfiguration.masterAddress);
                    Message messageMasterAddress = new Message(MessageType.SLAVE_NOT_MASTER, masterAddressPayload);
                    this.objectOutputStream.writeObject(messageMasterAddress);
                    this.objectOutputStream.flush();
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (this.running) {
                    err("Lost connection to the client unexpectedly - " + e);
                }
            }
        }

        log("Thread terminated");
    }

    private void handleClientAsMaster() throws IOException {

        while (this.running) {
            try {
                Message message = (Message) this.objectInputStream.readObject();
                switch (message.getType()) {
                    case CLIENT_NEW_MISSION -> {
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
                            log("refuse new mission - already busy");
                            Message masterConfirmMessage = new Message(MessageType.MASTER_BUSY);
                            this.objectOutputStream.writeObject(masterConfirmMessage);
                            this.objectOutputStream.flush();
                        }
                    }
                    case CLIENT_EXIT_ACKNOWLEDGE -> {
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

    public synchronized void sendSolution() {
        log("Sending solution " + MasterConfiguration.solution + " to the client.");

        String text = null;
        if (MasterConfiguration.solution != null) {
            RSAHelper rsaHelper = new RSAHelper();
            text = rsaHelper.decrypt(MasterConfiguration.solution.getPrime1().toString(), MasterConfiguration.solution.getPrime2().toString(), StaticConfiguration.CIPHER);

        }
        MissionResponsePayload missionResponsePayload = new MissionResponsePayload(MasterConfiguration.solution, text);
        Message solutionMessage = new Message(MessageType.MASTER_SOLUTION_FOUND, missionResponsePayload);

        try {
            this.objectOutputStream.writeObject(solutionMessage);
            this.objectOutputStream.flush();
        } catch (IOException e) {
            err("Failed to send the solution to the client - " + e);
        }
    }

    public synchronized void stop() {
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            log("error while waiting for the server socket to be closed - " + e);
        }
        this.running = false;
    }

    private static void log(String s) {
        System.out.println(ConsoleColors.UNDERLINE + "ClientHandler - " + s + ConsoleColors.RESET);
    }

    private static void err(String s) {
        Utils.err(ConsoleColors.UNDERLINE + "ClientHandler - " + s + ConsoleColors.RESET);
    }
}
