package optimus.prime.rsa.communication;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

import optimus.prime.rsa.communication.payloads.*;
import optimus.prime.rsa.crypto.RSAHelper;
import optimus.prime.rsa.main.*;

public class Master implements Runnable {

    private ServerSocket serverSocket;
    private final List<Thread> connectionHandlerThreads = new ArrayList<>();

    private final NetworkConfiguration networkConfig;
    private final List<BigInteger> primes;

    private final Queue<SlicePayload> slicesToDo;
    private final List<SlicePayload> slicesInProgress = new LinkedList<>();

    private SolutionPayload solution = null;

    public Master(NetworkConfiguration networkConfig) {
        this.networkConfig = networkConfig;
        this.primes = Utils.getPrimes();

        this.slicesToDo = Utils.getSlices(0, this.primes.size() - 1, MasterConfiguration.MASTER_SLICE_SIZE);

        try {
            this.serverSocket = new ServerSocket(
                    StaticConfiguration.PORT,
                    MasterConfiguration.MAX_INCOMING_SLAVES,
                    this.networkConfig.getMasterAddress()
            );
            this.serverSocket.setSoTimeout(1000);
            System.out.println("Master - Socket opened " + this.serverSocket);
        } catch (IOException e) {
            System.err.println("Master - failed while creating the serverSocket - " + e);
            System.exit(1);
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("Master - beginning to distribute connections ...");
            this.distributeConnections();
        } catch (IOException e) {
            System.err.println("Master - exception while distributing the connections - " + e);
        }
        this.stop();

        if (this.solution != null) {
            RSAHelper helper = new RSAHelper();
            System.out.println("Master - Decrypted text is \"" + helper.decrypt(this.solution.getPrime1().toString(), this.solution.getPrime2().toString(), MasterConfiguration.CHIFFRE) + "\"");
        } else {
            System.out.println("Master - The solution cannot be found in the given prime numbers.");
        }

        System.out.println("Master - Thread terminated");
    }

    private void distributeConnections() throws IOException {
        while (!this.serverSocket.isClosed() && this.solution == null && (!this.slicesToDo.isEmpty() || !this.slicesInProgress.isEmpty())) {
            try {
                Socket slave = this.serverSocket.accept();
                System.out.println("Master - Connection from " + slave + " established.");
                ConnectionHandler handler = new ConnectionHandler(slave, networkConfig);
                Thread thread = new Thread(handler);
                thread.start();
                this.connectionHandlerThreads.add(thread);
            } catch (SocketTimeoutException ignored) {
            }
        }
        System.out.println("Master - Stopping ConnectionHandlers");
    }

    private synchronized void markAsSolved(SolutionPayload s) {
        this.solution = s;
        System.out.println("Master - Solution found: " + s);
    }

    private synchronized SlicePayload getNextSlice() throws NoSuchElementException {
        SlicePayload slice = this.slicesToDo.remove();
        // transfer index from ToDo to InProgress
        this.slicesInProgress.add(slice);
        return slice;
    }

    private synchronized void markSliceAsDone(SlicePayload slice) {
        System.out.println("Master - Slice " + slice + " is done");
        this.slicesInProgress.remove(slice);
    }

    private synchronized List<BigInteger> getPrimes() {
        return this.primes;
    }

    private void stop() {
        System.out.println("Master - waiting for ConnectionHandlers to terminate ...");
        this.connectionHandlerThreads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println("Master - error while waiting for the ConnectionHandlers to terminate - " + e);
            }
        });

        try {
            this.serverSocket.close();
        } catch (IOException e) {
            System.err.println("Master - Failed to close the serverSocket - " + e);
        }
    }

    private class ConnectionHandler implements Runnable {

        private final Socket slave;
        private boolean running = true;

        private final NetworkConfiguration networkConfig;

        private SlicePayload currentSlice;

        public ConnectionHandler(Socket slave, NetworkConfiguration networkConfig) {
            System.out.println("Master - ConnectionHandler - " + slave + " - Initializing new ConnectionHandler.");
            this.slave = slave;
            this.networkConfig = networkConfig;
        }

        @Override
        public void run() {
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Starting ConnectionHandler");
            try (
                    OutputStream outputStream = this.slave.getOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                    InputStream inputStream = this.slave.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)
            ) {
                while (this.running) {
                    System.out.println("Master - ConnectionHandler - " + this.slave + " - waiting for message to be received ...");
                    Message message = (Message) objectInputStream.readObject();
                    final MultiMessage response = this.handleMessage(message);

                    if (response != null) {
                        objectOutputStream.writeObject(response);
                        objectOutputStream.flush();
                    }
                }
            } catch (IOException e) {
                if (this.running) {
                    System.err.println("Master - ConnectionHandler - " + this.slave + " - Object Input stream closed " + e);
                    // FIXME: Slave died
                } else {
                    System.out.println("Master - ConnectionHandler - " + this.slave + " - Slave disconnected because solution was found.");
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Master - ConnectionHandler - Class of incoming object unknown - " + e);
            }

            System.out.println("Master - ConnectionHandler - Terminated");
        }

        private MultiMessage handleMessage(Message m) {
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Received message");
            return switch (m.getType()) {
                case SLAVE_JOIN -> this.handleSlaveJoin(m);
                case SLAVE_FINISHED_WORK -> this.handleSlaveFinishedWork();
                case SLAVE_SOLUTION_FOUND -> this.handleSolutionFound(m);
                case SLAVE_EXIT_ACKNOWLEDGE -> this.handleExitAcknowledge();
                default -> MultiMessage.NONE;
            };
        }

        private MultiMessage handleSlaveJoin(Message m) {
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Slave wants to join");
            // JoinPayload payload = (JoinPayload) m.getPayload();
            MultiMessage response = new MultiMessage();

            // Add slave IP-Address to network Information
            InetAddress slaveAddress = this.slave.getInetAddress();
            this.networkConfig.addHost(slaveAddress);

            // create payload of current hosts
            HostsPayload hostsPayload = new HostsPayload(this.networkConfig.getHosts());
            Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
            response.addMessage(hostsMessage);

            // create payload of primes
            PrimesPayload primesPayload = new PrimesPayload(getPrimes());
            Message primesMessage = new Message(MessageType.MASTER_SEND_PRIMES, primesPayload);
            response.addMessage(primesMessage);
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Sending primes to Slave");

            // create payload for the public kry
            PubKeyRsaPayload pubKeyRsaPayload = new PubKeyRsaPayload(MasterConfiguration.PUB_RSA_KEY);
            Message pubKeyRsaMessage = new Message(MessageType.MASTER_SEND_PUB_KEY_RSA, pubKeyRsaPayload);
            response.addMessage(pubKeyRsaMessage);
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Sending the public key: \"" + MasterConfiguration.PUB_RSA_KEY + "\"");

            // create payload for next tasks
            this.currentSlice = getNextSlice();
            Message sliceMessage = new Message(MessageType.MASTER_DO_WORK, this.currentSlice);
            response.addMessage(sliceMessage);
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Sending new work to Slave: " + this.currentSlice);

            // TODO: send new host list to all slaves

            return response;
        }

        private MultiMessage handleSlaveFinishedWork() {
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Slave finished Work");
            // except TaskPayload
            MultiMessage response = new MultiMessage();

            // remove done slice from list
            // TODO: Es wird noch nicht gehandelt, dass der start_index in die Queue aufgenommen wird, sofern der Slave disconnected
            markSliceAsDone(this.currentSlice);

            // create new slice for slave
            try {
                this.currentSlice = getNextSlice();
                System.out.println("Master - ConnectionHandler - " + this.slave + " - Sending new slice to slave: " + this.currentSlice);
                Message sliceMessage = new Message(MessageType.MASTER_DO_WORK, this.currentSlice);
                response.addMessage(sliceMessage);
            } catch (NoSuchElementException ignored) {
                System.out.println("Master - ConnectionHandler - " + this.slave + " - No more slices to do -> sending MASTER_EXIT to Slave");
                Message exitMessage = new Message(MessageType.MASTER_EXIT);
                response.addMessage(exitMessage);
            }

            return response;
        }

        private MultiMessage handleSolutionFound(Message m) {
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Found solution");

            // Key found - other slaves can stop working
            SolutionPayload solution = (SolutionPayload) m.getPayload();
            markAsSolved(solution);

            // TODO: MASTER_EXIT an alle senden
            MultiMessage response = new MultiMessage();
            response.addMessage(new Message(MessageType.MASTER_EXIT));

            return response;
        }

        private MultiMessage handleExitAcknowledge() {
            System.out.println("Master - ConnectionHandler - " + this.slave + " - Slave acknowledged exit");
            this.running = false;
            try {
                this.slave.close();
            } catch (IOException ignored) {
            }
            return null;
        }
    }

}
