package optimus.prime.rsa.server.communication;

import optimus.prime.rsa.ConsoleColors;
import optimus.prime.rsa.Message;
import optimus.prime.rsa.MessageType;
import optimus.prime.rsa.payloads.MasterAddressPayload;
import optimus.prime.rsa.payloads.MissionPayload;
import optimus.prime.rsa.server.Utils;
import optimus.prime.rsa.server.config.MasterConfiguration;
import optimus.prime.rsa.server.config.NetworkConfiguration;
import optimus.prime.rsa.server.config.StaticConfiguration;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ClientHandler implements Runnable {

    private boolean running = true;
    private ServerSocket serverSocket;

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

        while (this.running) {
            try (
                    Socket client = this.serverSocket.accept();
                    OutputStream outputStream = client.getOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                    InputStream inputStream = client.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)
            ) {
                log(client.getInetAddress().getHostAddress() + " established a connection");
                if (MasterConfiguration.isMaster) {
                    // this host is the master
                    log("send confirmation that this host is the master");

                    Message masterConfirmMessage = new Message(MessageType.MASTER_CONFIRM);
                    objectOutputStream.writeObject(masterConfirmMessage);
                    objectOutputStream.flush();

                    this.handleClientAsMaster(objectInputStream);
                } else {
                    // this host is a slave
                    log("forward client to master " + NetworkConfiguration.masterAddress.getHostAddress());
                    MasterAddressPayload masterAddressPayload = new MasterAddressPayload(NetworkConfiguration.masterAddress);
                    Message messageMasterAddress = new Message(MessageType.SLAVE_NOT_MASTER, masterAddressPayload);
                    objectOutputStream.writeObject(messageMasterAddress);
                    objectOutputStream.flush();
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        log("Thread terminated");
    }

    private void handleClientAsMaster(ObjectInputStream objectInputStream) {

        while (this.running) {
            try {
                Message message = (Message) objectInputStream.readObject();
                if (message.getType() == MessageType.CLIENT_NEW_MISSION) {
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
                    err("Unexpected message type - " + message.getType());
                }
            } catch (IOException e) {
                log("Client disconnected unexpectedly.");
            } catch (ClassNotFoundException e) {
                err("Class of incoming object unknown - " + e);
            }
        }
    }

    public synchronized void stop() {
        this.running = false;
    }

    private static void log(String s) {
        System.out.println(ConsoleColors.UNDERLINE + "ClientHandler - " + s + ConsoleColors.RESET);
    }

    private static void err(String s) {
        Utils.err(ConsoleColors.UNDERLINE + "ClientHandler - " + s + ConsoleColors.RESET);
    }
}
