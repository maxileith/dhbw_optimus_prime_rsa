package optimus.prime.rsa.communication;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

import optimus.prime.rsa.communication.payloads.*;
import optimus.prime.rsa.config.MasterConfiguration;
import optimus.prime.rsa.config.NetworkConfiguration;
import optimus.prime.rsa.config.StaticConfiguration;
import optimus.prime.rsa.crypto.RSAHelper;
import optimus.prime.rsa.main.*;

public class Master implements Runnable {

    private ServerSocket serverSocket;
    private final List<Thread> connectionHandlerThreads = new ArrayList<>();
    private final Broadcaster broadcaster;
    private final Thread broadcasterThread;

    private final List<SlicePayload> slicesInProgress = new LinkedList<>();

    private SolutionPayload solution = null;

    public Master() {
        this.broadcaster = new Broadcaster();
        this.broadcasterThread = new Thread(this.broadcaster);

        // only load primes if there are not already primes in the
        // master configuration. this is important, since on a host
        // that was a slave before, the primes that were received
        // by the slave have to be used in the future.
        if (MasterConfiguration.primes == null) {
            MasterConfiguration.primes = Utils.getPrimes();
        }
        // same reason as for the primes above
        if (MasterConfiguration.slicesToDo == null) {
            MasterConfiguration.slicesToDo = Utils.getSlices(0, MasterConfiguration.primes.size() - 1, MasterConfiguration.MASTER_SLICE_SIZE);
        }
        log("Master - slices: " + MasterConfiguration.slicesToDo);
        log("Master - cipher: " + MasterConfiguration.CIPHER);
        log("Master - public key: " + MasterConfiguration.PUB_RSA_KEY);

        try {
            this.serverSocket = new ServerSocket(
                    StaticConfiguration.PORT,
                    MasterConfiguration.MAX_INCOMING_SLAVES,
                    NetworkConfiguration.masterAddress
            );
            this.serverSocket.setSoTimeout(1000);
            log("Master - Socket opened " + this.serverSocket);
        } catch (IOException e) {
            System.err.println("Master - failed while creating the serverSocket - " + e);
            System.exit(1);
        }
    }

    @Override
    public void run() {
        log("Master - starting broadcaster ...");
        this.broadcasterThread.start();

        try {
            log("Master - beginning to distribute connections ...");
            this.distributeConnections();
        } catch (IOException e) {
            System.err.println("Master - exception while distributing the connections - " + e);
        }
        this.stop();

        if (this.solution != null) {
            RSAHelper helper = new RSAHelper();
            log("Master - Decrypted text is \"" + helper.decrypt(this.solution.getPrime1().toString(), this.solution.getPrime2().toString(), MasterConfiguration.CIPHER) + "\"");
        } else {
            log("Master - The solution cannot be found in the given prime numbers.");
        }

        log("Master - Thread terminated");
    }

    private void distributeConnections() throws IOException {
        while (!this.serverSocket.isClosed() && this.solution == null && (!MasterConfiguration.slicesToDo.isEmpty() || !this.slicesInProgress.isEmpty())) {
            try {
                Socket slave = this.serverSocket.accept();
                log("Master - Connection from " + slave + " established.");
                ConnectionHandler handler = new ConnectionHandler(
                        slave,
                        this.broadcaster
                );
                Thread thread = new Thread(handler);
                thread.start();
                this.connectionHandlerThreads.add(thread);
            } catch (SocketTimeoutException ignored) {
            }
        }
        log("Master - Stopping ConnectionHandlers");
    }

    private static void log(String s) {
        System.out.println(ConsoleColors.BLUE_BRIGHT + s + ConsoleColors.RESET);
    }

    private synchronized void markAsSolved(SolutionPayload s) {
        this.solution = s;
        log("Master - Solution found: " + s);
    }

    private synchronized SlicePayload getNextSlice() throws NoSuchElementException {
        SlicePayload slice = MasterConfiguration.slicesToDo.remove();
        // transfer index from ToDo to InProgress
        this.slicesInProgress.add(slice);
        return slice;
    }

    private synchronized void markSliceAsDone(SlicePayload slice) {
        log("Master - Slice " + slice + " is done");
        this.slicesInProgress.remove(slice);
    }

    private synchronized void abortSlice(SlicePayload slice) {
        log("Master - Slice " + slice + " added back to queue");
        this.slicesInProgress.remove(slice);
        MasterConfiguration.slicesToDo.add(slice);
    }

    private synchronized List<BigInteger> getPrimes() {
        return MasterConfiguration.primes;
    }

    private Queue<SlicePayload> getUnfinishedSlices() {
        final Queue<SlicePayload> unfinishedSlices = new LinkedList<>(MasterConfiguration.slicesToDo);
        unfinishedSlices.addAll(this.slicesInProgress);
        return unfinishedSlices;
    }

    private void stop() {
        log("Master - waiting for ConnectionHandlers to terminate ...");
        this.connectionHandlerThreads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println("Master - error while waiting for the ConnectionHandlers to terminate - " + e);
            }
        });

        log("Master - sending stop signal to broadcaster ...");
        this.broadcaster.stop();
        log("Master - waiting for Broadcaster to terminate ...");
        try {
            this.broadcasterThread.join();
        } catch (InterruptedException e) {
            System.err.println("Master - error while waiting for the Broadcaster to terminate - " + e);
        }

        try {
            this.serverSocket.close();
        } catch (IOException e) {
            System.err.println("Master - Failed to close the serverSocket - " + e);
        }
    }

    private class ConnectionHandler implements Runnable {

        private final Socket slave;
        private boolean running = true;
        private final Broadcaster broadcaster;

        private SlicePayload currentSlice;

        public ConnectionHandler(Socket slave, Broadcaster broadcaster) {
            log("Master - ConnectionHandler - " + slave + " - Initializing new ConnectionHandler.");
            this.slave = slave;
            this.broadcaster = broadcaster;
        }

        @Override
        public void run() {
            log("Master - ConnectionHandler - " + this.slave + " - Starting ConnectionHandler");
            try (
                    OutputStream outputStream = this.slave.getOutputStream();
                    SyncedObjectOutputStream objectOutputStream = new SyncedObjectOutputStream(outputStream);
                    InputStream inputStream = this.slave.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)
            ) {
                // add to output streams for broadcasting
                broadcaster.addOutputStream(this.slave.getInetAddress(), objectOutputStream);
                // main loop to receive messages
                while (this.running) {
                    log("Master - ConnectionHandler - " + this.slave + " - waiting for message to be received ...");
                    Message message = (Message) objectInputStream.readObject();
                    final MultiMessage response = this.handleMessage(message);

                    if (response != null) {
                        objectOutputStream.writeSyncedObjectFlush(response);
                    }
                }
            } catch (IOException e) {
                if (this.running) {
                    System.err.println("Master - ConnectionHandler - " + this.slave + " - Object Input stream closed " + e);
                    // Slave died
                    abortSlice(this.currentSlice);
                } else {
                    log("Master - ConnectionHandler - " + this.slave + " - Slave disconnected because solution was found.");
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Master - ConnectionHandler - " + this.slave + " - Class of incoming object unknown - " + e);
            } finally {
                // remove the host from networking
                broadcaster.removeOutputStream(this.slave.getInetAddress());
                NetworkConfiguration.hosts.remove(this.slave.getInetAddress());
                // tell everybody that there is one slave less
                HostsPayload hostsPayload = new HostsPayload(NetworkConfiguration.hosts);
                Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
                broadcaster.send(hostsMessage);
            }

            log("Master - ConnectionHandler - " + this.slave + " - Terminated");
        }

        private MultiMessage handleMessage(Message m) {
            log("Master - ConnectionHandler - " + this.slave + " - Received message");
            return switch (m.getType()) {
                case SLAVE_JOIN -> this.handleJoin();
                case SLAVE_FINISHED_WORK -> this.handleSlaveFinishedWork();
                case SLAVE_SOLUTION_FOUND -> this.handleSolutionFound(m);
                case SLAVE_EXIT_ACKNOWLEDGE -> this.handleExitAcknowledge();
                default -> MultiMessage.NONE;
            };
        }

        private MultiMessage handleJoin() {
            log("Master - ConnectionHandler - " + this.slave + " - Slave wants to join");
            MultiMessage response = new MultiMessage();

            // Add slave IP-Address to network Information
            InetAddress slaveAddress = this.slave.getInetAddress();
            NetworkConfiguration.hosts.add(slaveAddress);

            // create payload of primes
            PrimesPayload primesPayload = new PrimesPayload(getPrimes());
            Message primesMessage = new Message(MessageType.MASTER_SEND_PRIMES, primesPayload);
            response.addMessage(primesMessage);
            log("Master - ConnectionHandler - " + this.slave + " - Sending primes to Slave");

            // create payload for the public key
            PubKeyRsaPayload pubKeyRsaPayload = new PubKeyRsaPayload(MasterConfiguration.PUB_RSA_KEY);
            Message pubKeyRsaMessage = new Message(MessageType.MASTER_SEND_PUB_KEY_RSA, pubKeyRsaPayload);
            response.addMessage(pubKeyRsaMessage);
            log("Master - ConnectionHandler - " + this.slave + " - Sending the public key: \"" + MasterConfiguration.PUB_RSA_KEY + "\"");

            // create payload for the cipher
            CipherPayload cipherPayload = new CipherPayload(MasterConfiguration.CIPHER);
            Message cipherMessage = new Message(MessageType.MASTER_CIPHER, cipherPayload);
            response.addMessage(cipherMessage);
            log("Master - ConnectionHandler - " + this.slave + " - Sending the cipher: \"" + MasterConfiguration.CIPHER + "\"");

            // create payload for next tasks
            this.currentSlice = getNextSlice();
            Message sliceMessage = new Message(MessageType.MASTER_DO_WORK, this.currentSlice);
            response.addMessage(sliceMessage);
            log("Master - ConnectionHandler - " + this.slave + " - Sending new work to Slave: " + this.currentSlice);

            // send new hosts list to all slaves
            HostsPayload hostsPayload = new HostsPayload(NetworkConfiguration.hosts);
            Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
            this.broadcaster.send(hostsMessage);

            // send new unfinished slices to all slaves
            UnfinishedSlicesPayload unfinishedSlicesPayload = new UnfinishedSlicesPayload(getUnfinishedSlices());
            Message unfinishedSlicesMessage = new Message(MessageType.MASTER_UNFINISHED_SLICES, unfinishedSlicesPayload);
            this.broadcaster.send(unfinishedSlicesMessage);

            return response;
        }

        private MultiMessage handleSlaveFinishedWork() {
            log("Master - ConnectionHandler - " + this.slave + " - Slave finished Work");
            // except TaskPayload
            MultiMessage response = new MultiMessage();

            markSliceAsDone(this.currentSlice);

            // create new slice for slave
            try {
                this.currentSlice = getNextSlice();
                log("Master - ConnectionHandler - " + this.slave + " - Sending new slice to slave: " + this.currentSlice);
                Message sliceMessage = new Message(MessageType.MASTER_DO_WORK, this.currentSlice);
                response.addMessage(sliceMessage);

                // send new unfinished slices to all slaves
                UnfinishedSlicesPayload unfinishedSlicesPayload = new UnfinishedSlicesPayload(getUnfinishedSlices());
                Message unfinishedSlicesMessage = new Message(MessageType.MASTER_UNFINISHED_SLICES, unfinishedSlicesPayload);
                this.broadcaster.send(unfinishedSlicesMessage);

            } catch (NoSuchElementException ignored) {
                log("Master - ConnectionHandler - " + this.slave + " - No more slices to do -> sending MASTER_EXIT to Broadcaster");
                Message exitMessage = new Message(MessageType.MASTER_EXIT);
                response.addMessage(exitMessage);
            }

            return response;
        }

        @SuppressWarnings("SameReturnValue")
        private MultiMessage handleSolutionFound(Message m) {
            log("Master - ConnectionHandler - " + this.slave + " - Found solution");

            // Key found - other slaves can stop working
            SolutionPayload solution = (SolutionPayload) m.getPayload();
            markAsSolved(solution);

            log("Master - ConnectionHandler - " + this.slave + " - No more slices to do -> sending MASTER_EXIT to Broadcaster");
            Message exitMessage = new Message(MessageType.MASTER_EXIT);
            this.broadcaster.send(exitMessage);

            return null;
        }

        @SuppressWarnings("SameReturnValue")
        private MultiMessage handleExitAcknowledge() {
            log("Master - ConnectionHandler - " + this.slave + " - Slave acknowledged exit");
            this.running = false;
            try {
                this.slave.close();
            } catch (IOException ignored) {
            }
            return null;
        }

        private static void log(String s) {
            System.out.println(ConsoleColors.GREEN_BRIGHT + s + ConsoleColors.RESET);
        }
    }

    private static class Broadcaster implements Runnable {

        private final Map<InetAddress, SyncedObjectOutputStream> streams = new HashMap<>();
        private final Queue<Message> queue = new LinkedList<>();
        private boolean running = true;

        @Override
        public void run() {
            while (this.running) {
                if (this.queue.isEmpty()) {
                    try {
                        // noinspection BusyWait
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        log("Master - Broadcaster - Error while waiting - " + e);
                    }
                    continue;
                }

                MultiMessage mm = new MultiMessage();
                while (!this.queue.isEmpty()) {
                    Message m = this.queue.remove();
                    log("Master - Broadcaster - broadcasting message of type: " + m.getType());
                    mm.addMessage(m);
                }
                for (InetAddress i : this.streams.keySet()) {
                    try {
                        this.streams.get(i).writeSyncedObjectFlush(mm);
                    } catch (IOException e) {
                        log("Master - Broadcaster - Failed to send to " + this.streams.get(i) + " - " + e);
                    }
                }
            }
        }

        public synchronized void addOutputStream(InetAddress address, SyncedObjectOutputStream stream) {
            log("Master - Broadcaster - Adding stream for broadcasting: " + address);
            this.streams.put(address, stream);
        }

        public synchronized void removeOutputStream(InetAddress address) {
            log("Master - Broadcaster - Removing stream for broadcasting: " + address);
            this.streams.remove(address);
        }

        public synchronized void send(Message m) {
            log("Master - Broadcaster - queued message of type: " + m.getType());
            this.queue.add(m);
        }

        private static void log(String s) {
            System.out.println(ConsoleColors.YELLOW + s + ConsoleColors.RESET);
        }

        public void stop() {
            this.running = false;
        }
    }
}
