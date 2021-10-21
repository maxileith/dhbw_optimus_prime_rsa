package optimus.prime.rsa.communication;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import optimus.prime.rsa.communication.payloads.HostsPayload;
import optimus.prime.rsa.communication.payloads.JoinPayload;
import optimus.prime.rsa.communication.payloads.ResultPayload;
import optimus.prime.rsa.communication.payloads.TaskPayload;
import optimus.prime.rsa.main.NetworkConfiguration;

public class Master implements Runnable {

    private ServerSocket serverSocket;
    private final List<ConnectionHandler> concurrentConnections = new ArrayList<>();
    private static final int MAX_INCOMING_CLIENTS = 1000;

    private final NetworkConfiguration networkConfig;
    private final List<Integer> primes;

    private final int SLICE_SIZE = 10;
    private List<Integer> startIndicesToDo;
    private List<Integer> startIndicesInProgress;

    private Solution solution;

    public Master(NetworkConfiguration networkConfig, List<Integer> primes) {
        this.networkConfig = networkConfig;
        this.primes = primes;
        this.startIndicesToDo = getIndicesToDo();

        try {
            this.serverSocket = new ServerSocket(
                    NetworkConfiguration.PORT,
                    MAX_INCOMING_CLIENTS,
                    InetAddress.getByName("0.0.0.0")
            );
        } catch (IOException e) {
            System.out.println("An error occured. " + e);
        }
    }

    @Override
    public void run() {
        try {
            this.distributeConnections();
        } catch (IOException e) {
            System.out.println("An error occured. " + e);
        }
    }

    private void distributeConnections() throws IOException {
        while (!this.serverSocket.isClosed()) {
            Socket slave = this.serverSocket.accept();
            System.out.println("Connection from " + slave + " established.");
            ConnectionHandler handler = new ConnectionHandler(slave, networkConfig, primes);
            Thread thread = new Thread(handler);
            thread.start();
            this.concurrentConnections.add(handler);
        }
        this.concurrentConnections.stream().map(ConnectionHandler::stop);
    }

    private List<Integer> getIndicesToDo() {
        List<Integer> indices = new ArrayList<>();
        int lastListIndex = this.primes.size() - 1;
        for(int i = 0; i <= lastListIndex; i += this.SLICE_SIZE) {
            indices.add(i);
        }
        return indices;
    }

    private synchronized void markAsSolved(Solution s) {
        this.solution = s;
        this.concurrentConnections.stream().map(ConnectionHandler::stop);
    }

    private synchronized TaskPayload getNextTaskPayload() {
        int newStartIndex = this.startIndicesToDo.get(0);
        // transfer index from ToDo to InProgress
        this.startIndicesInProgress.add(newStartIndex);
        this.startIndicesToDo.remove(Integer.valueOf(newStartIndex));
        return new TaskPayload(newStartIndex, this.SLICE_SIZE);
    }

    private synchronized void markIndexAsDone(int index) {
        this.startIndicesInProgress.remove(Integer.valueOf(index));
    }


    private class ConnectionHandler implements Runnable {

        private final Socket slave;
        private boolean running = true;

        private final NetworkConfiguration networkConfig;
        private final List<Integer> primes;

        private int currentIndex;

        public ConnectionHandler(Socket slave, NetworkConfiguration networkConfig, List<Integer> primes) {
            this.slave = slave;
            this.networkConfig = networkConfig;
            this.primes = primes;
        }

        @Override
        public void run() {
            try (
                    InputStream inputStream = this.slave.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    OutputStream outputStream = this.slave.getOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)
            ) {
                while (this.running) {
                    Message message = (Message) objectInputStream.readObject();
                    System.out.println(message);

                    final MultiMessage response = this.handleMessage(message);

                    objectOutputStream.writeObject(response);
                    objectOutputStream.flush();
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("An error occurred." + e);
            }
        }

        public int getCurrentIndex() {
            return this.currentIndex;
        }

        private MultiMessage handleMessage(Message m) {
            return switch (m.getType()) {
                case SLAVE_JOIN -> this.handleClientJoin(m);
                case SLAVE_FINISHED_WORK -> this.handleClientFinishedWork(m);
                case SLAVE_SOLUTION_FOUND -> this.handleSolutionFound(m);
                default -> MultiMessage.NONE;
            };
        }

        private MultiMessage handleClientJoin(Message m) {
            JoinPayload payload = (JoinPayload) m.getPayload();
            MultiMessage response = new MultiMessage();

            // Add slave IP-Address to network Information
            InetAddress slaveAddress = this.slave.getInetAddress();
            this.networkConfig.addHost(slaveAddress);

            // create payload of current hosts
            HostsPayload hostsPayload = new HostsPayload(this.networkConfig.getHosts());
            Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
            response.addMessage(hostsMessage);

            // create payload for next tasks
            TaskPayload taskPayload = getNextTaskPayload();
            this.currentIndex = taskPayload.getStartIndex();
            Message taskMessage = new Message(MessageType.DO_WORK, taskPayload);
            response.addMessage(taskMessage);

            return response;
        }

        private MultiMessage handleClientFinishedWork(Message m) {
            // except TaskPayload
            MultiMessage response = new MultiMessage();

            // remove done index from list
            markIndexAsDone(this.currentIndex);

            // create new Task for slave
            TaskPayload taskPayload = getNextTaskPayload();
            this.currentIndex = taskPayload.getStartIndex();
            Message taskMessage = new Message(MessageType.DO_WORK, taskPayload);
            response.addMessage(taskMessage);

            return response;
        }

        private MultiMessage handleSolutionFound(Message m) {
            // Key found - other slaves can stop working
            ResultPayload resultPayload = (ResultPayload) m.getPayload();
            int prime1 = resultPayload.getPrime1();
            int prime2 = resultPayload.getPrime2();

            Solution solution = new Solution(prime1, prime2);
            markAsSolved(solution);

            return new MultiMessage();
        }

        public synchronized boolean stop() {
            boolean success = true;
            try {
                this.slave.close();
            } catch (IOException e) {
                System.out.println("An error occurred." + e);
                success = false;
            }
            this.running = false;
            return success;
        }
    }

}
