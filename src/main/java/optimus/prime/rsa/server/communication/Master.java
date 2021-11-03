package optimus.prime.rsa.server.communication;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

import optimus.prime.rsa.ConsoleColors;
import optimus.prime.rsa.server.Utils;
import optimus.prime.rsa.server.communication.payloads.*;
import optimus.prime.rsa.server.config.MasterConfiguration;
import optimus.prime.rsa.server.config.NetworkConfiguration;
import optimus.prime.rsa.server.config.StaticConfiguration;
import optimus.prime.rsa.server.crypto.RSAHelper;

public class Master implements Runnable {

    private ServerSocket serverSocket;
    private final List<Thread> connectionHandlerThreads = new ArrayList<>();
    private final Broadcaster broadcaster;
    private final Thread broadcasterThread;

    private final List<SlicePayload> slicesInProgress = new LinkedList<>();

    private SolutionPayload solution = null;

    public Master(String primeList) {
        this.broadcaster = new Broadcaster();
        this.broadcasterThread = new Thread(this.broadcaster);

        // only load primes if there are not already primes in the
        // master configuration. this is important, since on a host
        // that was a slave before, the primes that were received
        // by the slave have to be used in the future.
        if (StaticConfiguration.primes == null) {
            StaticConfiguration.primes = Utils.getPrimes(primeList);
        }
        // same reason as for the primes above
        if (MasterConfiguration.slicesToDo == null) {
            MasterConfiguration.slicesToDo = Utils.getSlices(StaticConfiguration.primes.size(), 0, StaticConfiguration.primes.size() - 1, MasterConfiguration.MASTER_CHECKS_PER_SLICE);
        }
        log("cipher: " + StaticConfiguration.CIPHER);
        log("public key: " + StaticConfiguration.PUB_RSA_KEY);
        log("doing " + MasterConfiguration.MASTER_CHECKS_PER_SLICE + " checks per slice");
        List<SlicePayload> slicesToDoList = MasterConfiguration.slicesToDo.stream().toList();
        StringBuilder slicesLogString = new StringBuilder();
        for (int i = 0; i < slicesToDoList.size(); i += 6) {
            slicesLogString.append("\n");
            for (int j = 0; j < 6 && i + j < slicesToDoList.size(); j++) {
                slicesLogString.append(String.format("%17s", slicesToDoList.get(i + j).toString()));
            }
        }
        log("slices:" + slicesLogString);

        // reset connected hosts
        NetworkConfiguration.hosts = new ArrayList<>();

        try {
            this.serverSocket = new ServerSocket(
                    StaticConfiguration.PORT,
                    MasterConfiguration.MAX_INCOMING_SLAVES,
                    NetworkConfiguration.masterAddress
            );
            this.serverSocket.setSoTimeout(1000);
            log("Socket opened " + this.serverSocket);
        } catch (IOException e) {
            err("failed while creating the serverSocket - " + e);
            System.exit(1);
        }
    }

    @Override
    public void run() {
        log("starting broadcaster ...");
        this.broadcasterThread.start();

        try {
            log("beginning to distribute connections ...");
            log("To join a slave use arguments " + ConsoleColors.RED_UNDERLINED + "--master-address " + NetworkConfiguration.masterAddress.getHostAddress());
            this.distributeConnections();
        } catch (IOException e) {
            err("exception while distributing the connections - " + e);
        }
        this.stop();

        if (this.solution != null) {
            RSAHelper helper = new RSAHelper();
            log("Decrypted text is \"" + helper.decrypt(this.solution.getPrime1().toString(), this.solution.getPrime2().toString(), StaticConfiguration.CIPHER) + "\"");
        } else {
            log("The solution cannot be found in the given prime numbers.");
        }

        log("Thread terminated");
    }

    private void distributeConnections() throws IOException {
        while (!this.serverSocket.isClosed() && this.solution == null && (!MasterConfiguration.slicesToDo.isEmpty() || !this.slicesInProgress.isEmpty())) {
            try {
                Socket slave = this.serverSocket.accept();
                log("Connection from " + slave + " established.");
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
        log("Stopping ConnectionHandlers");
    }

    private synchronized void markAsSolved(SolutionPayload s) {
        this.solution = s;
        log("Solution found: " + s);
    }

    private synchronized SlicePayload getNextSlice() throws NoSuchElementException {
        SlicePayload slice = MasterConfiguration.slicesToDo.remove();
        this.slicesInProgress.add(slice);
        return slice;
    }

    private synchronized void markSliceAsDone(SlicePayload slice) {
        log("Slice " + slice + " is done");
        this.slicesInProgress.remove(slice);
    }

    private synchronized void abortSlice(SlicePayload slice) {
        log("Slice " + slice + " added back to queue");
        this.slicesInProgress.remove(slice);
        MasterConfiguration.slicesToDo.add(slice);
    }

    private Queue<SlicePayload> getUnfinishedSlices() {
        final Queue<SlicePayload> unfinishedSlices = new LinkedList<>(MasterConfiguration.slicesToDo);
        unfinishedSlices.addAll(this.slicesInProgress);
        return unfinishedSlices;
    }

    private void stop() {
        log("waiting for ConnectionHandlers to terminate ...");
        this.connectionHandlerThreads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                err("error while waiting for the ConnectionHandlers to terminate - " + e);
            }
        });

        log("sending stop signal to broadcaster ...");
        this.broadcaster.stop();
        log("waiting for Broadcaster to terminate ...");
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

    private static void log(String s) {
        System.out.println(ConsoleColors.BLUE_BRIGHT + "Master - " + s + ConsoleColors.RESET);
    }

    private static void err(String s) {
        Utils.err("Master - " + s);
    }

    private class ConnectionHandler implements Runnable {

        private final Socket slave;
        private boolean running = true;
        private final Broadcaster broadcaster;

        private SlicePayload currentSlice;

        public ConnectionHandler(Socket slave, Broadcaster broadcaster) {
            this.slave = slave;
            log("Initializing new ConnectionHandler.");
            this.broadcaster = broadcaster;
        }

        @Override
        public void run() {
            log("Starting ConnectionHandler");
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
                    log("waiting for message to be received ...");
                    Message message = (Message) objectInputStream.readObject();
                    final MultiMessage response = this.handleMessage(message);

                    if (response != null) {
                        objectOutputStream.writeSyncedObjectFlush(response);
                    }
                }
            } catch (IOException e) {
                if (this.running) {
                    err("Object Input stream closed " + e);
                    // Slave died
                    abortSlice(this.currentSlice);
                } else {
                    log("Slave disconnected because solution was found.");
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
            }

            log("Terminated");
        }

        private MultiMessage handleMessage(Message m) {
            log("Received message");
            return switch (m.getType()) {
                case SLAVE_JOIN -> this.handleJoin();
                case SLAVE_FINISHED_WORK -> this.handleSlaveFinishedWork();
                case SLAVE_SOLUTION_FOUND -> this.handleSolutionFound(m);
                case SLAVE_EXIT_ACKNOWLEDGE -> this.handleExitAcknowledge();
                default -> MultiMessage.NONE;
            };
        }

        private MultiMessage handleJoin() {
            log("Slave wants to join");
            MultiMessage response = new MultiMessage();

            InetAddress slaveAddress = this.slave.getInetAddress();
            // if slave is not on the same host provide information
            // that is needed in case the master goes down
            if (!NetworkConfiguration.ownAddresses.contains(slaveAddress)) {
                // Add slave IP-Address to network Information
                NetworkConfiguration.hosts.add(slaveAddress);

                // create payload of primes
                PrimesPayload primesPayload = new PrimesPayload(StaticConfiguration.primes);
                Message primesMessage = new Message(MessageType.MASTER_SEND_PRIMES, primesPayload);
                response.addMessage(primesMessage);
                log("Sending primes to Slave");

                // create payload for the public key
                PubKeyRsaPayload pubKeyRsaPayload = new PubKeyRsaPayload(StaticConfiguration.PUB_RSA_KEY);
                Message pubKeyRsaMessage = new Message(MessageType.MASTER_SEND_PUB_KEY_RSA, pubKeyRsaPayload);
                response.addMessage(pubKeyRsaMessage);
                log("Sending the public key: \"" + StaticConfiguration.PUB_RSA_KEY + "\"");

                // create payload for the cipher
                CipherPayload cipherPayload = new CipherPayload(StaticConfiguration.CIPHER);
                Message cipherMessage = new Message(MessageType.MASTER_CIPHER, cipherPayload);
                response.addMessage(cipherMessage);
                log("Sending the cipher: \"" + StaticConfiguration.CIPHER + "\"");

                // send new hosts list to all slaves
                HostsPayload hostsPayload = new HostsPayload(NetworkConfiguration.hosts);
                Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
                this.broadcaster.send(hostsMessage);
            } else {
                log("Skip sending of primes, public key, cipher and host list, because the slave is hosted on the same host as the master");
            }

            // create payload for next tasks
            this.currentSlice = getNextSlice();
            Message sliceMessage = new Message(MessageType.MASTER_DO_WORK, this.currentSlice);
            response.addMessage(sliceMessage);
            log("Sending new work to Slave: " + this.currentSlice);

            return response;
        }

        private MultiMessage handleSlaveFinishedWork() {
            log("Slave finished Work");
            // except TaskPayload
            MultiMessage response = new MultiMessage();

            markSliceAsDone(this.currentSlice);

            // create new slice for slave
            try {
                this.currentSlice = getNextSlice();
                log("Sending new slice to slave: " + this.currentSlice);
                Message sliceMessage = new Message(MessageType.MASTER_DO_WORK, this.currentSlice);
                response.addMessage(sliceMessage);

                // send new unfinished slices to all slaves
                UnfinishedSlicesPayload unfinishedSlicesPayload = new UnfinishedSlicesPayload(getUnfinishedSlices());
                Message unfinishedSlicesMessage = new Message(MessageType.MASTER_UNFINISHED_SLICES, unfinishedSlicesPayload);
                this.broadcaster.send(unfinishedSlicesMessage);

            } catch (NoSuchElementException ignored) {
                log("No more slices to do -> sending MASTER_EXIT to Broadcaster");
                Message exitMessage = new Message(MessageType.MASTER_EXIT);
                response.addMessage(exitMessage);
            }

            return response;
        }

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

        @SuppressWarnings("SameReturnValue")
        private MultiMessage handleExitAcknowledge() {
            log("Slave acknowledged exit");
            this.running = false;
            try {
                this.slave.close();
            } catch (IOException ignored) {
            }
            return null;
        }

        private void log(String s) {
            System.out.println(ConsoleColors.GREEN_BRIGHT + "Master - ConnectionHandler - " + this.slave.getInetAddress().getHostAddress() + " - " + s + ConsoleColors.RESET);
        }

        private void err(String s) {
            Utils.err("Master - ConnectionHandler - " + this.slave.getInetAddress().getHostAddress() + " - " + s);
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
                        log("Error while waiting - " + e);
                    }
                    continue;
                }

                MultiMessage mm = new MultiMessage();
                while (!this.queue.isEmpty()) {
                    Message m = this.queue.remove();
                    log("broadcasting message of type: " + m.getType());
                    mm.addMessage(m);
                }
                for (InetAddress i : this.streams.keySet()) {
                    try {
                        this.streams.get(i).writeSyncedObjectFlush(mm);
                    } catch (IOException e) {
                        log("Failed to send to " + i.getHostAddress() + " - " + e);
                    }
                }
            }
        }

        public synchronized void addOutputStream(InetAddress address, SyncedObjectOutputStream stream) {
            log("Adding stream for broadcasting: " + address.getHostAddress());
            this.streams.put(address, stream);
        }

        public synchronized void removeOutputStream(InetAddress address) {
            log("Removing stream for broadcasting: " + address.getHostAddress());
            this.streams.remove(address);
        }

        public synchronized void send(Message m) {
            log("queued message of type: " + m.getType());
            this.queue.add(m);
        }

        private static void log(String s) {
            System.out.println(ConsoleColors.YELLOW_BRIGHT + "Master - Broadcaster - " +  s + ConsoleColors.RESET);
        }

        public void stop() {
            this.running = false;
        }
    }
}
