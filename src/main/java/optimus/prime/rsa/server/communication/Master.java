package optimus.prime.rsa.server.communication;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import optimus.prime.rsa.ConsoleColors;
import optimus.prime.rsa.Message;
import optimus.prime.rsa.MessageType;
import optimus.prime.rsa.MultiMessage;
import optimus.prime.rsa.server.Utils;
import optimus.prime.rsa.payloads.*;
import optimus.prime.rsa.server.config.MasterConfiguration;
import optimus.prime.rsa.server.config.NetworkConfiguration;
import optimus.prime.rsa.server.config.StaticConfiguration;
import optimus.prime.rsa.server.crypto.RSAHelper;

/**
 * The class that supplies the functionality of the master
 */
public class Master implements Runnable {

    private ServerSocket serverSocket;
    private final List<Thread> connectionHandlerThreads = new ArrayList<>();
    private final Broadcaster broadcaster;
    private final Thread broadcasterThread;

    private final List<SlicePayload> slicesInProgress = new LinkedList<>();

    private boolean alreadyStarted = false;

    /**
     * Create a new {@link Master}
     */
    public Master() {
        // create a new broadcaster
        this.broadcaster = new Broadcaster();
        this.broadcasterThread = new Thread(this.broadcaster);

        // reset connected hosts
        NetworkConfiguration.hosts = new ArrayList<>();

        try {
            this.serverSocket = new ServerSocket(
                    StaticConfiguration.PORT,
                    MasterConfiguration.MAX_INCOMING_SLAVES,
                    NetworkConfiguration.masterAddress
            );
            // set timeout to prevent getting stuck in the 'accept' method
            // of the server socket
            this.serverSocket.setSoTimeout(1000);
            log("Socket opened " + this.serverSocket);
        } catch (IOException e) {
            err("failed while creating the serverSocket - " + e);
            System.exit(1);
        }
    }

    /**
     * Method for the thread to execute.
     * Contains the main loop to accept new connections from slaves
     */
    @Override
    public void run() {
        log("starting broadcaster ...");
        this.broadcasterThread.start();

        try {
            log("beginning to distribute connections ...");
            log("To join a slave use arguments " + ConsoleColors.RED_UNDERLINED + "--master-address " + NetworkConfiguration.masterAddress.getHostAddress());
            // begin to distribute connections to connection handlers
            this.distributeConnections();
            // at this point the mission is finished
        } catch (IOException e) {
            err("exception while distributing the connections - " + e);
        }
        // stop the master
        this.stop();

        // show solution if one was found
        if (MasterConfiguration.solution != null) {
            RSAHelper helper = new RSAHelper();
            log("Decrypted text is " + ConsoleColors.UNDERLINE + helper.decrypt(MasterConfiguration.solution.getPrime1().toString(), MasterConfiguration.solution.getPrime2().toString(), StaticConfiguration.CIPHER));
        } else {
            log("The solution cannot be found in the given prime numbers.");
        }

        // send the solution to the client
        ClientHandler.getInstance().sendSolution();

        // calculate the time needed to finish the mission
        long endMillis = System.currentTimeMillis();
        long duration = endMillis - MasterConfiguration.startMillis;

        log("end millis: " + endMillis);
        log("The whole process (including all masters) took " + ConsoleColors.UNDERLINE + TimeUnit.MILLISECONDS.toMinutes(duration) + "m" + TimeUnit.MILLISECONDS.toSeconds(duration) % 60 + "s" + duration % 1000 + "ms");

        log("Thread terminated");
    }

    /**
     * This method accepts connections by slaves and starts a connection handler
     *
     * @throws IOException if an I/O error occurs when waiting for a connection.
     */
    private void distributeConnections() throws IOException {
        // accept connections as long as
        // - the mission is not already started or
        // - no solution has been found yet and there are still slices to process
        while (!alreadyStarted || (!this.serverSocket.isClosed() && MasterConfiguration.solution == null && (MasterConfiguration.currentSliceStart != StaticConfiguration.primes.size() || !this.slicesInProgress.isEmpty() || !MasterConfiguration.lostSlices.isEmpty()))) {
            try {
                Socket slave = this.serverSocket.accept();
                log("Connection from " + slave + " established.");
                // start the new connection handler
                ConnectionHandler handler = new ConnectionHandler(
                        slave,
                        this.broadcaster
                );
                Thread thread = new Thread(handler);
                thread.start();
                this.connectionHandlerThreads.add(thread);
            } catch (SocketTimeoutException ignored) {
            }
            // As soon as the cipher, public key and the number of primes are set
            // there is a mission that can be started --> start
            if (!this.alreadyStarted && !StaticConfiguration.PUB_RSA_KEY.equals(BigInteger.ZERO) && !StaticConfiguration.CIPHER.equals("") && StaticConfiguration.primes != null) {
                this.alreadyStarted = true;
                log("Broadcasting mission details.");
                // tell every slave about the mission
                broadcastMissionDetails();
            }
        }
    }

    /**
     * Mark the mission as solved, because a {@link SolutionPayload} was found
     *
     * @param s {@link SolutionPayload} of the solution that has been found
     */
    private synchronized void markAsSolved(SolutionPayload s) {
        MasterConfiguration.solution = s;
        log("Solution found: " + s);
    }

    /**
     * Get the next major slice for a slave
     *
     * @param workers number of workers that the slave works with
     * @return the major slice
     * @throws NoSuchElementException there are no more major slices
     */
    private synchronized SlicePayload getNextSlice(int workers) throws NoSuchElementException {
        SlicePayload slice;

        if (!MasterConfiguration.lostSlices.isEmpty()) {
            // use a slice that was lost in a previous attempt to finish this slice
            // if one is available
            slice = MasterConfiguration.lostSlices.remove();
        } else if (MasterConfiguration.currentSliceStart != StaticConfiguration.primes.size()) {
            // make a new slice depending on the number of workers.
            int numberOfPrimes = StaticConfiguration.primes.size();
            int currentStart = MasterConfiguration.currentSliceStart;
            long checksPerSlice = workers * MasterConfiguration.MASTER_CHECKS_PER_SLICE_PER_WORKER;

            // Don't worry if you don't understand the following line of code.
            // You need to reed the documentation to understand the derivation
            // of this mathematical formula.
            int sliceEnd = numberOfPrimes - (int) Math.round(Math.sqrt(Math.pow(numberOfPrimes - currentStart, 2) - 2 * checksPerSlice));
            // current end is at least at current start
            sliceEnd = Math.max(sliceEnd, currentStart);
            // current end must be smaller or equal to end
            sliceEnd = Math.min(sliceEnd, numberOfPrimes - 1);

            slice = new SlicePayload(currentStart, sliceEnd);
            MasterConfiguration.currentSliceStart = slice.getEnd() + 1;
        } else {
            throw new NoSuchElementException();
        }

        // add to the slices that are currently in progress
        this.slicesInProgress.add(slice);
        // return the slice
        return slice;
    }

    /**
     * Mark a slice as done
     *
     * @param slice {@link SlicePayload} that is finished
     */
    private synchronized void markSliceAsDone(SlicePayload slice) {
        log("Slice " + slice + " is done");
        // the slice is no longer in progress
        this.slicesInProgress.remove(slice);
    }

    /**
     * Report a lost slice
     *
     * @param slice {@link SlicePayload} that has been lost during processing
     */
    private synchronized void lostSlice(SlicePayload slice) {
        log("Slice " + slice + " added to lost slices");
        // the slice is no longer in progress
        this.slicesInProgress.remove(slice);
        if (slice != null) {
            // add the slice to the list slices
            MasterConfiguration.lostSlices.add(slice);
            // send progress to all slaves
            ProgressPayload progressPayload = new ProgressPayload(getLostSlices(), MasterConfiguration.currentSliceStart);
            Message progressMessage = new Message(MessageType.MASTER_PROGRESS, progressPayload);
            this.broadcaster.send(progressMessage);
        }
    }

    /**
     * Get all slices that are being lost at the point where the master fails
     *
     * @return {@link Queue} of lost slices
     */
    private Queue<SlicePayload> getLostSlices() {
        final Queue<SlicePayload> lostSlices = new LinkedList<>(MasterConfiguration.lostSlices);
        // slices in progress are lost as well when the
        // master dies.
        lostSlices.addAll(this.slicesInProgress);
        return lostSlices;
    }

    /**
     * Get the mission details that a slave need to start working
     *
     * @return {@link MultiMessage} with the mission details
     */
    private MultiMessage collectMissionDetails() {
        MultiMessage out = new MultiMessage();

        // create payload of primes
        PrimesPayload primesPayload = new PrimesPayload(StaticConfiguration.primes);
        Message primesMessage = new Message(MessageType.MASTER_SEND_PRIMES, primesPayload);
        out.addMessage(primesMessage);
        log("mission details: primes");

        // create payload for the public key
        PubKeyRsaPayload pubKeyRsaPayload = new PubKeyRsaPayload(StaticConfiguration.PUB_RSA_KEY);
        Message pubKeyRsaMessage = new Message(MessageType.MASTER_SEND_PUB_KEY_RSA, pubKeyRsaPayload);
        out.addMessage(pubKeyRsaMessage);
        log("mission details: public key: \"" + StaticConfiguration.PUB_RSA_KEY + "\"");

        // create payload for the cipher
        CipherPayload cipherPayload = new CipherPayload(StaticConfiguration.CIPHER);
        Message cipherMessage = new Message(MessageType.MASTER_CIPHER, cipherPayload);
        out.addMessage(cipherMessage);
        log("mission details: cipher: \"" + StaticConfiguration.CIPHER + "\"");

        // create payload for the start time
        StartMillisPayload startMillisPayload = new StartMillisPayload(MasterConfiguration.startMillis);
        Message startMillisMessage = new Message(MessageType.MASTER_START_MILLIS, startMillisPayload);
        out.addMessage(startMillisMessage);
        log("mission details: start millis: " + MasterConfiguration.startMillis);

        // start message
        // empty payload is the "start"
        Message startMessage = new Message(MessageType.MASTER_START_MESSAGE);
        out.addMessage(startMessage);
        log("mission details: start message");

        return out;
    }

    /**
     * Send the mission details to every slave
     */
    private void broadcastMissionDetails() {
        if (MasterConfiguration.startMillis == 0) {
            MasterConfiguration.startMillis = System.currentTimeMillis();
        }
        log("start millis: " + MasterConfiguration.startMillis);

        this.collectMissionDetails().getAllMessages().forEach(broadcaster::send);
    }


    /**
     * Stop the master gracefully
     */
    private synchronized void stop() {
        log("waiting for ConnectionHandlers to terminate ...");
        // wait for all connection handlers to terminate.
        // the connection handlers terminate on their own without
        // sending a signal to them, because ...
        // - the slave wants to get a new slice
        // - the master has no more slices --> sends MASTER_EXIT
        // - the slave exits
        // - the connection handler terminates
        this.connectionHandlerThreads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                err("error while waiting for the ConnectionHandlers to terminate - " + e);
            }
        });

        // send the stop signal to the broadcaster
        log("sending stop signal to broadcaster ...");
        this.broadcaster.stop();
        // wait for the broadcaster to terminate
        log("waiting for broadcaster to terminate ...");
        try {
            this.broadcasterThread.join();
        } catch (InterruptedException e) {
            err("error while waiting for the Broadcaster to terminate - " + e);
        }

        try {
            this.serverSocket.close();
        } catch (IOException e) {
            err("Failed to close the serverSocket - " + e);
        }
    }

    /**
     * Use to log
     *
     * @param s {@link String} to log
     */
    private static void log(String s) {
        System.out.println(ConsoleColors.BLUE_BRIGHT + "Master        - " + s + ConsoleColors.RESET);
    }

    /**
     * Use to log errors
     *
     * @param s {@link String} to log as an error
     */
    private static void err(String s) {
        Utils.err("Master        - " + s);
    }

    /**
     * Class to handle specific connections to slaves
     */
    private class ConnectionHandler implements Runnable {

        private final Socket slave;
        private boolean running = true;
        private final Broadcaster broadcaster;

        private SlicePayload currentSlice;
        private int workers;

        /**
         * Create a new {@link ConnectionHandler}
         *
         * @param slave the socket of the slave
         * @param broadcaster a broadcaster
         */
        public ConnectionHandler(Socket slave, Broadcaster broadcaster) {
            this.slave = slave;
            log("Initializing new ConnectionHandler.");
            this.broadcaster = broadcaster;
        }

        /**
         * Method for the thread to execute.
         * Contains the main loop to communicate with a slave
         */
        @Override
        public void run() {
            log("Starting ConnectionHandler");
            try (
                    OutputStream outputStream = this.slave.getOutputStream();
                    SyncedObjectOutputStream objectOutputStream = new SyncedObjectOutputStream(outputStream);
                    InputStream inputStream = this.slave.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)
            ) {
                // main loop to receive messages
                while (this.running) {
                    // wait for a message to be received
                    log("waiting for message to be received ...");
                    Message message = (Message) objectInputStream.readObject();

                    // handle the incoming
                    final MultiMessage response = this.handleMessage(message);

                    // The SLAVE_JOIN message is the first message, so we have to
                    // add the slave to the broadcaster
                    if (message.getType() == MessageType.SLAVE_JOIN) {
                        // add to output streams for broadcasting
                        broadcaster.addOutputStream(this.slave.getInetAddress(), objectOutputStream);
                    }

                    // if there is a response, send it to the slave
                    if (response != null) {
                        objectOutputStream.writeSyncedObjectFlush(response);
                    }
                }
            } catch (IOException e) {
                if (this.running) {
                    err("Object Input stream closed " + e);
                } else {
                    log("Slave disconnected on purpose.");
                }
            } catch (ClassNotFoundException e) {
                err("Class of incoming object unknown - " + e);
            } finally {
                // remove the host from networking
                broadcaster.removeOutputStream(this.slave.getInetAddress());
                NetworkConfiguration.hosts.remove(this.slave.getInetAddress());
                // tell everybody that there is one slave less
                HostsPayload hostsPayload = new HostsPayload(NetworkConfiguration.hosts);
                Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
                broadcaster.send(hostsMessage);
                ClientHandler.getInstance().notifyHostListChanged();
                if (this.running) {
                    // Slave died
                    // this method cannot be called in the catch block, because
                    // the method triggers a broadcast to all slaves. However,
                    // in the catch block the stream of this slave is still
                    // in the broadcaster (but not active anymore).
                    lostSlice(this.currentSlice);
                }
            }

            log("Terminated");
        }

        /**
         * This method handles incoming messages.
         *
         * @param m {@link MultiMessage} to handle
         */
        private MultiMessage handleMessage(Message m) {
            log("Received message");
            MultiMessage response;

            switch (m.getType()) {
                case SLAVE_JOIN:
                    response = this.handleJoin(m);
                    break;
                case SLAVE_FINISHED_WORK:
                    response = this.handleWorkNeeded(false);
                    break;
                case SLAVE_SOLUTION_FOUND:
                    response = this.handleSolutionFound(m);
                    break;
                case SLAVE_EXIT_ACKNOWLEDGE:
                    response = this.handleExitAcknowledge();
                    break;
                case SLAVE_GET_FIRST_SLICE:
                    response = this.handleWorkNeeded(true);
                    break;
                default:
                    response = MultiMessage.NONE;
                    break;
            }

            return response;
        }

        /**
         * Via this method a slave can join.
         *
         * @param m {@link Message} of type SLAVE_JOIN
         * @return the mission details
         */
        @SuppressWarnings("SameReturnValue")
        private MultiMessage handleJoin(Message m) {
            JoinPayload joinPayload = (JoinPayload) m.getPayload();
            this.workers = joinPayload.getWorkers();

            log("Slave wants to join with " + this.workers + " workers");

            InetAddress slaveAddress = this.slave.getInetAddress();
            // if slave is not on the same host provide information
            // that is needed in case the master goes down
            if (!NetworkConfiguration.ownAddresses.contains(slaveAddress)) {
                // Add slave IP-Address to network Information
                NetworkConfiguration.hosts.add(slaveAddress);

                // send new hosts list to all slaves
                HostsPayload hostsPayload = new HostsPayload(NetworkConfiguration.hosts);
                Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
                this.broadcaster.send(hostsMessage);
                ClientHandler.getInstance().notifyHostListChanged();
            } else {
                log("Skip sending updating the hosts list because slave is hosted on the same system as the master");
            }

            if (alreadyStarted) {
                return collectMissionDetails();
            } else {
                return null;
            }
        }

        /**
         * Get new work for a slave
         *
         * @param firstWork specifies if this is the first time the slave needs work
         * @return {@link MultiMessage}
         */
        private MultiMessage handleWorkNeeded(boolean firstWork) {
            log("Slave needs new work");
            // except TaskPayload
            MultiMessage response = new MultiMessage();

            if (!firstWork) {
                markSliceAsDone(this.currentSlice);
            }

            // create new slice for slave
            try {
                this.currentSlice = getNextSlice(this.workers);
                log("Sending new slice to slave: " + this.currentSlice);
                Message sliceMessage = new Message(MessageType.MASTER_DO_WORK, this.currentSlice);
                response.addMessage(sliceMessage);

                // send progress to all slaves
                ProgressPayload progressPayload = new ProgressPayload(getLostSlices(), MasterConfiguration.currentSliceStart);
                Message progressMessage = new Message(MessageType.MASTER_PROGRESS, progressPayload);
                this.broadcaster.send(progressMessage);

            } catch (NoSuchElementException ignored) {
                // send MASTER_EXIT if there are no more slices
                log("No more slices to do -> sending MASTER_EXIT");
                Message exitMessage = new Message(MessageType.MASTER_EXIT);
                response.addMessage(exitMessage);
            }

            // return multi message
            return response;
        }

        /**
         * report that a solution has been found
         *
         * @param m {@link Message} of type SLAVE_SOLUTION_FOUND
         * @return null
         */
        @SuppressWarnings("SameReturnValue")
        private MultiMessage handleSolutionFound(Message m) {
            log("Found solution");

            // Key found - other slaves can stop working
            SolutionPayload solution = (SolutionPayload) m.getPayload();
            markAsSolved(solution);

            log("No more slices to do -> sending MASTER_EXIT to Broadcaster");
            Message exitMessage = new Message(MessageType.MASTER_EXIT);
            this.broadcaster.send(exitMessage);

            return null;
        }

        /**
         * Do some required stuff after the slave exits
         *
         * @return null
         */
        @SuppressWarnings("SameReturnValue")
        private MultiMessage handleExitAcknowledge() {
            log("Slave acknowledged exit");
            // stop main loop
            this.running = false;
            try {
                this.slave.close();
            } catch (IOException ignored) {
            }
            return null;
        }

        /**
         * Use to log
         *
         * @param s {@link String} to log
         */
        private void log(String s) {
            System.out.println(ConsoleColors.GREEN_BRIGHT + "Master        - ConnectionHandler - " + this.slave.getInetAddress().getHostAddress() + " - " + s + ConsoleColors.RESET);
        }

        /**
         * Use to log errors
         *
         * @param s {@link String} to log as an error
         */
        private void err(String s) {
            Utils.err("Master        - ConnectionHandler - " + this.slave.getInetAddress().getHostAddress() + " - " + s);
        }
    }

    /**
     * Class to broadcast messages to every slave
     */
    private static class Broadcaster implements Runnable {

        private final ConcurrentMap<InetAddress, SyncedObjectOutputStream> streams = new ConcurrentHashMap<>();
        private final Queue<Message> queue = new LinkedList<>();
        private boolean running = true;

        /**
         * Method for the thread to execute.
         * Contains the main loop for sending messages to every slave.
         */
        @Override
        public void run() {
            while (this.running) {
                // wait until there are messages in the queue to send
                if (this.queue.isEmpty()) {
                    try {
                        // noinspection BusyWait
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        log("Error while waiting - " + e);
                    }
                    continue;
                }

                // put all messages to send in a multi message
                MultiMessage mm = new MultiMessage();
                while (!this.queue.isEmpty()) {
                    Message m = this.queue.remove();
                    log("broadcasting message of type: " + m.getType());
                    mm.addMessage(m);
                }

                // send the multi message to all slaves
                for (InetAddress i : this.streams.keySet()) {
                    try {
                        this.streams.get(i).writeSyncedObjectFlush(mm);
                    } catch (IOException e) {
                        log("Failed to send to " + i.getHostAddress() + " - " + e);
                    }
                }
            }
        }

        /**
         * Add an {@link SyncedObjectOutputStream} to send the broadcasts to
         *
         * @param address {@link InetAddress} of the slave
         * @param stream {@link SyncedObjectOutputStream} the output stream of the slave to add
         */
        public synchronized void addOutputStream(InetAddress address, SyncedObjectOutputStream stream) {
            log("Adding stream for broadcasting: " + address.getHostAddress());
            this.streams.put(address, stream);
        }

        /**
         * Remove a slave
         *
         * @param address {@link InetAddress} of the slave to remove
         */
        public synchronized void removeOutputStream(InetAddress address) {
            log("Removing stream for broadcasting: " + address.getHostAddress());
            this.streams.remove(address);
        }

        /**
         * Boradcast a message to all slaves
         *
         * @param m {@link Message} submit a message to broadcast to the slaves
         */
        public synchronized void send(Message m) {
            log("queued message of type: " + m.getType());
            this.queue.add(m);
        }

        /**
         * Use to log
         *
         * @param s {@link String} to log
         */
        private static void log(String s) {
            System.out.println(ConsoleColors.YELLOW_BRIGHT + "Master        - Broadcaster - " + s + ConsoleColors.RESET);
        }

        /**
         * Stop the running broadcaster
         */
        public synchronized void stop() {
            this.running = false;
        }
    }
}
