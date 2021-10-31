package optimus.prime.rsa.communication;

import optimus.prime.rsa.communication.payloads.*;
import optimus.prime.rsa.config.MasterConfiguration;
import optimus.prime.rsa.crypto.Worker;
import optimus.prime.rsa.config.NetworkConfiguration;
import optimus.prime.rsa.config.StaticConfiguration;
import optimus.prime.rsa.main.ConsoleColors;
import optimus.prime.rsa.main.Main;
import optimus.prime.rsa.main.Utils;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class Slave implements Runnable {
    private Socket socket;
    private ObjectOutputStream objectOutputStream;
    private Thread receiveThread;
    private ExecutorService es;
    private CompletionService<SolutionPayload> cs;

    private Queue<SlicePayload> currentMinorSlices;

    private boolean running = true;

    public Slave() {

        try {
            log("trying to connect to master ...");
            this.socket = new Socket(
                    NetworkConfiguration.masterAddress,
                    StaticConfiguration.PORT
            );
            log("established connection to master");

            this.es = Executors.newFixedThreadPool(StaticConfiguration.SLAVE_WORKERS);
            this.cs = new ExecutorCompletionService<>(es);

            InputStream inputStream = this.socket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            OutputStream outputStream = this.socket.getOutputStream();
            this.objectOutputStream = new ObjectOutputStream(outputStream);

            Receiver receiver = new Receiver(objectInputStream);
            this.receiveThread = new Thread(receiver);
            this.receiveThread.start();
        } catch (IOException e) {
            err("The master is probably not reachable. " + e);
            this.running = false;
        }
    }

    @Override
    public void run() {
        if (!this.running) {
            return;
        }
        try {
            log("Sending hello message to master");

            Message joinMessage = new Message(MessageType.SLAVE_JOIN);
            this.objectOutputStream.writeObject(joinMessage);
            this.objectOutputStream.flush();

            while (this.running) {

                // wait for the slice queue to get filled
                while ((this.currentMinorSlices == null || this.currentMinorSlices.isEmpty()) && !this.socket.isClosed()) {
                    // noinspection BusyWait
                    Thread.sleep(5);
                }

                // do the math
                log("Assigning new work to the workers ...");
                int concurrentSlices = this.currentMinorSlices.size();
                while (!this.currentMinorSlices.isEmpty()) {
                    this.cs.submit(new Worker(
                            this.currentMinorSlices.remove(),
                            StaticConfiguration.primes,
                            StaticConfiguration.PUB_RSA_KEY
                    ));
                }

                // collect the results
                for (int resultsReceived = 0; resultsReceived < concurrentSlices && this.running; resultsReceived++) {
                    try {
                        Future<SolutionPayload> f = this.cs.take();
                        SolutionPayload s = f.get();
                        log("received new result from a worker");
                        // Solution found
                        if (!s.equals(SolutionPayload.NO_SOLUTION)) {
                            Message m = new Message(MessageType.SLAVE_SOLUTION_FOUND, s);
                            this.objectOutputStream.writeObject(m);
                            this.objectOutputStream.flush();
                            this.running = false;
                            log("worker found a solution! " + s);
                        }
                    } catch (ExecutionException e) {
                        err("Error in Worker: " + e);
                        e.printStackTrace();
                        this.running = false;
                    }
                }

                if (this.running) {
                    Message m = new Message(MessageType.SLAVE_FINISHED_WORK);
                    this.objectOutputStream.writeObject(m);
                    this.objectOutputStream.flush();
                    log("finished work");
                }
            }
            log("stopped");

            // executor service could already be shutdown by
            // the stopSlave method
            if (!this.es.isShutdown()) {
                this.es.shutdownNow();
                //noinspection ResultOfMethodCallIgnored
                this.es.awaitTermination(10, TimeUnit.SECONDS);
            }

            // wait for the receiver to terminate
            this.receiveThread.join();

            log("Thread terminated");

        } catch (IOException | InterruptedException e) {
            err("An error occurred." + e);
        }
    }

    private synchronized void setCurrentSlice(SlicePayload majorSlice) {
        this.currentMinorSlices = Utils.getNSlices(majorSlice.getStart(), majorSlice.getEnd(), StaticConfiguration.SLAVE_WORKERS);
    }

    private void stopSlave(boolean force) {
        if (!force) {
            log("sending SLAVE_EXIT_ACKNOWLEDGE");
            Message m = new Message(MessageType.SLAVE_EXIT_ACKNOWLEDGE);
            try {
                this.objectOutputStream.writeObject(m);
                this.objectOutputStream.flush();
                this.socket.close();
            } catch (IOException ignored) {
                err("failed to send SLAVE_EXIT_ACKNOWLEDGE");
            }
        } else {
            try {
                this.socket.close();
            } catch (IOException ignored) {
            }
        }

        // do not wait for the receiver thread to be terminated here,
        // because this method gets called by the receiver. so if joining
        // here the receiver is stuck waiting for itself.
        this.running = false;

        if (force) {
            log("sending interrupting workers ...");
            this.es.shutdownNow();
            try {
                // noinspection ResultOfMethodCallIgnored
                this.es.awaitTermination(10, TimeUnit.SECONDS);
                log("interrupting off all workers is done");
            } catch (InterruptedException e) {
                err("error while interrupting workers - " + e);
            }
        }
    }

    private static void log(String s) {
        System.out.println(ConsoleColors.MAGENTA_BRIGHT + "Slave  - " + s + ConsoleColors.RESET);
    }

    private static void err(String s) {
        System.err.println("Slave  - " + s);
    }

    private class Receiver implements Runnable {

        private boolean running = true;
        private final ObjectInputStream objectInputStream;

        public Receiver(ObjectInputStream objectInputStream) {
            this.objectInputStream = objectInputStream;
        }

        @Override
        public void run() {
            log("started");
            try {
                while (this.running) {
                    MultiMessage messages = (MultiMessage) this.objectInputStream.readObject();
                    this.handleMessages(messages);
                }
            } catch (IOException | ClassNotFoundException e) {
                if (this.running) {
                    err("lost connection to master - " + e);
                    log("reporting lost connection to main thread");
                    Main.reportMasterLost();
                    stopSlave(true);
                } else {
                    log("stopped on purpose");
                }
            }
            log("terminated");
        }

        private void handleMessages(MultiMessage messages) {
            log("Received MultiMessage");
            for (Message m : messages.getAllMessages()) {
                switch (m.getType()) {
                    case MASTER_HOSTS_LIST -> this.handleHostList(m);
                    case MASTER_DO_WORK -> this.handleDoWork(m);
                    case MASTER_EXIT -> this.stopReceiver();
                    case MASTER_SEND_PRIMES -> this.handleMasterSendPrimes(m);
                    case MASTER_SEND_PUB_KEY_RSA -> this.handleMasterSendPubKeyRsa(m);
                    case MASTER_UNFINISHED_SLICES -> this.handleUnfinishedSlicesUpdate(m);
                    case MASTER_CIPHER -> this.handleCipher(m);
                    default -> log("Unknown message type");
                }
            }
        }

        private void handleHostList(Message m) {
            HostsPayload hostsPayload = (HostsPayload) m.getPayload();
            NetworkConfiguration.hosts = hostsPayload.getHosts();
            log("Received new host list");
        }

        private void handleDoWork(Message m) {
            SlicePayload slicePayload = (SlicePayload) m.getPayload();
            setCurrentSlice(slicePayload);
            log("Received new working package");
        }

        public void stopReceiver() {
            log("MASTER_EXIT");
            log("stopping receiver");
            this.running = false;
            stopSlave(false);
        }

        private void handleMasterSendPrimes(Message m) {
            PrimesPayload primesPayload = (PrimesPayload) m.getPayload();
            StaticConfiguration.primes = primesPayload.getPrimes();
            log("set primes");
        }

        private void handleMasterSendPubKeyRsa(Message m) {
            PubKeyRsaPayload pubKeyRsaPayload = (PubKeyRsaPayload) m.getPayload();
            StaticConfiguration.PUB_RSA_KEY = pubKeyRsaPayload.getPubKeyRsa();
            log("set public key to \"" + pubKeyRsaPayload.getPubKeyRsa() + "\"");
        }

        private void handleUnfinishedSlicesUpdate(Message m) {
            UnfinishedSlicesPayload unfinishedSlicesPayload = (UnfinishedSlicesPayload) m.getPayload();
            log("received update of unfinished work");
            if (!MasterConfiguration.isMaster) {
                MasterConfiguration.slicesToDo = unfinishedSlicesPayload.getUnfinishedSlices();
            }
        }

        private void handleCipher(Message m) {
            CipherPayload cipherPayload = (CipherPayload) m.getPayload();
            log("received cipher: \"" + cipherPayload.getCipher() + "\"");
            StaticConfiguration.CIPHER = cipherPayload.getCipher();
        }

        private static void log(String s) {
            System.out.println(ConsoleColors.CYAN_BRIGHT + "Slave  - Receiver - " + s + ConsoleColors.RESET);
        }

        private static void err(String s) {
            System.err.println("Slave  - Receiver - " + s);
        }
    }
}
