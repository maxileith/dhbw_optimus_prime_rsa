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

/**
 * The class that supplies the functionality of a slave
 */
public class Slave implements Runnable {

    private Socket socket;
    private ObjectOutputStream objectOutputStream;
    private Thread receiveThread;
    private ExecutorService es;
    private CompletionService<SolutionPayload> cs;

    private Queue<SlicePayload> currentMinorSlices;

    private boolean running = true;
    private boolean missionStarted = false;

    /**
     * Create a new {@link Slave}
     */
    public Slave() {

        try {
            // try to connect to the master
            log("trying to connect to master ...");
            this.socket = new Socket(
                    NetworkConfiguration.masterAddress,
                    StaticConfiguration.PORT
            );
            log("established connection to master");

            // create a new thread pool to execute the workers
            this.es = Executors.newFixedThreadPool(SlaveConfiguration.WORKERS);
            this.cs = new ExecutorCompletionService<>(es);

            // make the stream for communication ready
            InputStream inputStream = this.socket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            OutputStream outputStream = this.socket.getOutputStream();
            this.objectOutputStream = new ObjectOutputStream(outputStream);

            // start the receiver
            Receiver receiver = new Receiver(objectInputStream);
            this.receiveThread = new Thread(receiver);
        } catch (IOException e) {
            err("The master " + NetworkConfiguration.masterAddress.getHostAddress() + " is probably not reachable - " + e);
            // tell the Main class that the master is lost
            Main.reportMasterLost();
            this.running = false;
        }
    }

    /**
     * The method that is executed by a thread and contains the main loop
     */
    @Override
    public void run() {
        if (!this.running) {
            return;
        }

        // start receiver
        log("starting receiver ...");
        this.receiveThread.start();

        try {
            log("Sending hello message to master");

            // sending join message to the master with the number of workers
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
                        // if the mission is being started, request the first slice
                        // from the master
                        Message m = new Message(MessageType.SLAVE_GET_FIRST_SLICE);
                        this.objectOutputStream.writeObject(m);
                        this.objectOutputStream.flush();
                        missionStarted = false;
                        continue outerLoop;
                    }
                }

                log("Assigning new work to the workers ...");
                int concurrentSlices = this.currentMinorSlices == null ? 0 : this.currentMinorSlices.size();
                // assign work to the workers
                while (this.currentMinorSlices != null && !this.currentMinorSlices.isEmpty()) {
                    this.cs.submit(new Worker(
                            this.currentMinorSlices.remove(),
                            StaticConfiguration.primes,
                            StaticConfiguration.PUB_RSA_KEY
                    ));
                }

                // collect all the results
                for (int resultsReceived = 0; resultsReceived < concurrentSlices && this.running; resultsReceived++) {
                    try {
                        Future<SolutionPayload> f = this.cs.take();
                        SolutionPayload s = f.get();
                        log("received new result from a worker");
                        // Solution found
                        if (s != null) {
                            // tell the master, that we have found the solution
                            Message m = new Message(MessageType.SLAVE_SOLUTION_FOUND, s);
                            this.objectOutputStream.writeObject(m);
                            this.objectOutputStream.flush();
                            // stop the main loop
                            this.running = false;
                            log("worker found a solution! " + s);
                        }
                    } catch (ExecutionException e) {
                        // a serious error --> exit
                        err("Error in Worker: " + e);
                        e.printStackTrace();
                        this.running = false;
                    }
                }

                // tell the master that we have finished working on the
                // given slice. Skip this if running is false, because then
                // the slave is exiting, and we don't want any more work.
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

    /**
     * Supply a new major slice that the slave has to work on
     *
     * @param majorSlice the major slice that the slave has to work on
     */
    private synchronized void setCurrentSlice(SlicePayload majorSlice) {
        this.currentMinorSlices = Utils.getNSlices(majorSlice, SlaveConfiguration.WORKERS);
    }

    /**
     * Stop the slave
     *
     * @param force stopping gracefully or immediately
     */
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
            // interrupt all workers in the thread pool to
            // shut them down immediately
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

    /**
     * Use to log
     *
     * @param s {@link String} to log
     */
    private static void log(String s) {
        System.out.println(ConsoleColors.MAGENTA_BRIGHT + "Slave         - " + s + ConsoleColors.RESET);
    }

    /**
     * Use to log errors
     *
     * @param s {@link String} to log as an error
     */
    private static void err(String s) {
        Utils.err("Slave         - " + s);
    }

    /**
     * The receiver is responsible for handling incoming messages from the master
     */
    private class Receiver implements Runnable {

        private boolean running = true;
        private final ObjectInputStream objectInputStream;

        /**
         * Create a new {@link Receiver}
         *
         * @param objectInputStream the {@link ObjectOutputStream} used for receiving
         */
        public Receiver(ObjectInputStream objectInputStream) {
            this.objectInputStream = objectInputStream;
        }

        /**
         * The method that is executed by a thread and contains the main loop
         */
        @Override
        public void run() {
            log("started");
            try {
                while (this.running) {
                    // wait for a message
                    MultiMessage messages = (MultiMessage) this.objectInputStream.readObject();
                    // handle the message
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

        /**
         * This method handles incoming messages.
         *
         * @param messages {@link MultiMessage} to handle
         */
        private void handleMessages(MultiMessage messages) {
            log("Received MultiMessage");
            for (Message m : messages.getAllMessages()) {
                switch (m.getType()) {
                    case MASTER_HOSTS_LIST:
                        this.handleHostList(m);
                        break;
                    case MASTER_DO_WORK:
                        this.handleDoWork(m);
                        break;
                    case MASTER_EXIT:
                        this.stopReceiver();
                        break;
                    case MASTER_SEND_PRIMES:
                        this.handleMasterSendPrimes(m);
                        break;
                    case MASTER_SEND_PUB_KEY_RSA:
                        this.handleMasterSendPubKeyRsa(m);
                        break;
                    case MASTER_PROGRESS:
                        this.handleProgressUpdate(m);
                        break;
                    case MASTER_CIPHER:
                        this.handleCipher(m);
                        break;
                    case MASTER_START_MILLIS:
                        this.handleStartMillis(m);
                        break;
                    case MASTER_START_MESSAGE:
                        this.handleStartMessage();
                        break;
                    default:
                        log("Unexpected message type");
                        break;
                }
            }
        }

        /**
         * Save the current hosts
         *
         * @param m {@link Message} of type MASTER_HOSTS_LIST
         */
        private void handleHostList(Message m) {
            HostsPayload hostsPayload = (HostsPayload) m.getPayload();
            NetworkConfiguration.hosts = hostsPayload.getHosts();
            log("Received new host list");
        }

        /**
         * Update the current major slice to work on
         *
         * @param m {@link Message} of type MASTER_DO_WORK
         */
        private void handleDoWork(Message m) {
            SlicePayload slicePayload = (SlicePayload) m.getPayload();
            setCurrentSlice(slicePayload);
            log("Received new slice to do - " + slicePayload);
        }

        /**
         * Save the prime numbers
         *
         * @param m {@link Message} of type MASTER_SEND_PRIMES
         */
        private void handleMasterSendPrimes(Message m) {
            PrimesPayload primesPayload = (PrimesPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                StaticConfiguration.primes = primesPayload.getPrimes();
                log("set primes - length: " + StaticConfiguration.primes.size());
            } else {
                log("skip updating primes because master is the same host");
            }
        }

        /**
         * Save the public key
         *
         * @param m {@link Message} of type MASTER_SEND_PUB_KEY_RSA
         */
        private void handleMasterSendPubKeyRsa(Message m) {
            PubKeyRsaPayload pubKeyRsaPayload = (PubKeyRsaPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                log("set public key to \"" + pubKeyRsaPayload.getPubKeyRsa() + "\"");
                StaticConfiguration.PUB_RSA_KEY = pubKeyRsaPayload.getPubKeyRsa();
            } else {
                log("skip updating public key because master is the same host");
            }
        }

        /**
         * Save the current progress
         *
         * @param m {@link Message} of type MASTER_PROGRESS
         */
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

        /**
         * Save the cipher
         *
         * @param m {@link Message} of type MASTER_CIPHER
         */
        private void handleCipher(Message m) {
            CipherPayload cipherPayload = (CipherPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                log("received cipher: \"" + cipherPayload.getCipher() + "\"");
                StaticConfiguration.CIPHER = cipherPayload.getCipher();
            } else {
                log("skip updating cipher because master is the same host");
            }
        }

        /**
         * Save the starting time of the mission
         *
         * @param m {@link Message} of type MASTER_START_MILLIS
         */
        private void handleStartMillis(Message m) {
            StartMillisPayload startMillisPayload = (StartMillisPayload) m.getPayload();
            if (!MasterConfiguration.isMaster) {
                log("received start millis: " + startMillisPayload.getStartMillis());
                MasterConfiguration.startMillis = startMillisPayload.getStartMillis();
            } else {
                log("skip updating start millis because master is the same host");
            }
        }

        /**
         * Start the mission
         */
        private void handleStartMessage() {
            log("starting the mission");
            missionStarted = true;
        }

        /**
         * Stop the receiver gracefully
         */
        public void stopReceiver() {
            log("MASTER_EXIT");
            log("stopping receiver");
            this.running = false;
            stopSlave(false);
        }

        /**
         * Use to log
         *
         * @param s {@link String} to log
         */
        private void log(String s) {
            System.out.println(ConsoleColors.CYAN_BRIGHT + "Slave         - Receiver - " + s + ConsoleColors.RESET);
        }

        /**
         * Use to log errors
         *
         * @param s {@link String} to log as an error
         */
        private void err(String s) {
            Utils.err("Slave         - Receiver - " + s);
        }
    }
}
