package optimus.prime.rsa.server.communication;

import optimus.prime.rsa.Message;
import optimus.prime.rsa.MessageType;
import optimus.prime.rsa.MultiMessage;
import optimus.prime.rsa.payloads.*;
import optimus.prime.rsa.server.config.MasterConfiguration;
import optimus.prime.rsa.server.config.SlaveConfiguration;
import optimus.prime.rsa.server.crypto.Worker;
import optimus.prime.rsa.server.config.NetworkConfiguration;
import optimus.prime.rsa.server.config.StaticConfiguration;
import optimus.prime.rsa.ConsoleColors;
import optimus.prime.rsa.server.Main;
import optimus.prime.rsa.server.Utils;

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
    private boolean missionStarted = false;

    public Slave() {

        try {
            log("trying to connect to master ...");
            this.socket = new Socket(
                    NetworkConfiguration.masterAddress,
                    StaticConfiguration.PORT
            );
            log("established connection to master");

            this.es = Executors.newFixedThreadPool(SlaveConfiguration.WORKERS);
            this.cs = new ExecutorCompletionService<>(es);

            InputStream inputStream = this.socket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            OutputStream outputStream = this.socket.getOutputStream();
            this.objectOutputStream = new ObjectOutputStream(outputStream);

            Receiver receiver = new Receiver(objectInputStream);
            this.receiveThread = new Thread(receiver);
        } catch (IOException e) {
            err("The master " + NetworkConfiguration.masterAddress.getHostAddress() + " is probably not reachable - " + e);
            Main.reportMasterLost();
            this.running = false;
        }
    }

    @Override
    public void run() {
        if (!this.running) {
            return;
        }

        log("starting receiver ...");
        this.receiveThread.start();

        try {
            log("Sending hello message to master");

            // sending join message to the master incl. the number of workers
            JoinPayload joinPayload = new JoinPayload(SlaveConfiguration.WORKERS);
            Message joinMessage = new Message(MessageType.SLAVE_JOIN, joinPayload);
            this.objectOutputStream.writeObject(joinMessage);
            this.objectOutputStream.flush();

            outerLoop:
            while (this.running) {

                // wait for the slice queue to get filled
                while ((this.currentMinorSlices == null || this.currentMinorSlices.isEmpty()) && !this.socket.isClosed()) {
                    // noinspection BusyWait
                    Thread.sleep(5);
                    if (missionStarted) {
                        Message m = new Message(MessageType.SLAVE_GET_FIRST_SLICE);
                        this.objectOutputStream.writeObject(m);
                        this.objectOutputStream.flush();
                        missionStarted = false;
                        continue outerLoop;
                    }
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
                        if (s != null) {
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
        this.currentMinorSlices = Utils.getNSlices(majorSlice, SlaveConfiguration.WORKERS);
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
        System.out.println(ConsoleColors.MAGENTA_BRIGHT + "Slave         - " + s + ConsoleColors.RESET);
    }

    private static void err(String s) {
        Utils.err("Slave         - " + s);
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
                    case MASTER_LOST_SLICES -> this.handleProgressUpdate(m);
                    case MASTER_CIPHER -> this.handleCipher(m);
                    case MASTER_START_MILLIS -> this.handleStartMillis(m);
                    case MASTER_START_MESSAGE -> this.handleStartMessage();
                    default -> log("Unexpected message type");
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
            log("Received new slice to do - " + slicePayload);
        }

        public void stopReceiver() {
            log("MASTER_EXIT");
            log("stopping receiver");
            this.running = false;
            stopSlave(false);
        }

        private void handleMasterSendPrimes(Message m) {
            PrimesPayload primesPayload = (PrimesPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                StaticConfiguration.primes = primesPayload.getPrimes();
                log("set primes - length: " + StaticConfiguration.primes.size());
            } else {
                log("skip updating primes because master is the same host");
            }
        }

        private void handleMasterSendPubKeyRsa(Message m) {
            PubKeyRsaPayload pubKeyRsaPayload = (PubKeyRsaPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                log("set public key to \"" + pubKeyRsaPayload.getPubKeyRsa() + "\"");
                StaticConfiguration.PUB_RSA_KEY = pubKeyRsaPayload.getPubKeyRsa();
            } else {
                log("skip updating public key because master is the same host");
            }
        }

        private void handleProgressUpdate(Message m) {
            ProgressPayload progressPayload = (ProgressPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                log("received update of progress");
                MasterConfiguration.lostSlices = progressPayload.getLostSlices();
                MasterConfiguration.currentSliceStart = progressPayload.getCurrentSliceStart();
            } else {
                log("skip updating progress because master is the same host");
            }
        }

        private void handleCipher(Message m) {
            CipherPayload cipherPayload = (CipherPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                log("received cipher: \"" + cipherPayload.getCipher() + "\"");
                StaticConfiguration.CIPHER = cipherPayload.getCipher();
            } else {
                log("skip updating cipher because master is the same host");
            }
        }

        private void handleStartMillis(Message m) {
            StartMillisPayload startMillisPayload = (StartMillisPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                log("received start millis: " + startMillisPayload.getStartMillis());
                MasterConfiguration.startMillis = startMillisPayload.getStartMillis();
            } else {
                log("skip updating start millis because master is the same host");
            }
        }

        private void handleStartMessage() {
            log("starting the mission");
            missionStarted = true;
        }

        private static void log(String s) {
            System.out.println(ConsoleColors.CYAN_BRIGHT + "Slave         - Receiver - " + s + ConsoleColors.RESET);
        }

        private static void err(String s) {
            Utils.err("Slave         - Receiver - " + s);
        }
    }
}
