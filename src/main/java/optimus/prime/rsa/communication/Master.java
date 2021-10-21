package optimus.prime.rsa.communication;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import optimus.prime.rsa.communication.payloads.HostsPayload;
import optimus.prime.rsa.communication.payloads.JoinPayload;
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
            Socket client = this.serverSocket.accept();
            System.out.println("Connection from " + client + " established.");
            ConnectionHandler handler = new ConnectionHandler(client, networkConfig, primes);
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


    private class ConnectionHandler implements Runnable {

        private final Socket client;
        private boolean running = true;

        private final NetworkConfiguration networkConfig;
        private final List<Integer> primes;

        public ConnectionHandler(Socket client, NetworkConfiguration networkConfig, List<Integer> primes) {
            this.client = client;
            this.networkConfig = networkConfig;
            this.primes = primes;
        }

        @Override
        public void run() {
            try (
                    InputStream inputStream = this.client.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    OutputStream outputStream = this.client.getOutputStream();
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
            InetAddress slaveAddress = this.client.getInetAddress();
            this.networkConfig.addHost(slaveAddress);

            // create payload of current hosts
            HostsPayload hostsPayload = new HostsPayload(this.networkConfig.getHosts());
            Message hostsMessage = new Message(MessageType.MASTER_HOSTS_LIST, hostsPayload);
            response.addMessage(hostsMessage);

            return response;
        }

        private MultiMessage handleClientFinishedWork(Message m) {
            MultiMessage response = new MultiMessage();

            // remove done index from list


            // create new Task for slave
            TaskPayload taskPayload = getNextTaskPayload();
            Message taskMessage = new Message(MessageType.DO_WORK, taskPayload);
            response.addMessage(taskMessage);

            return response;
        }

        private MultiMessage handleSolutionFound(Message m) {
            // Key found - other slaves can stop working

            Solution solution = new Solution(1, 1);
            markAsSolved(solution);

            return new MultiMessage();
        }

        public synchronized boolean stop() {
            boolean success = true;
            try {
                this.client.close();
            } catch (IOException e) {
                System.out.println("An error occurred." + e);
                success = false;
            }
            this.running = false;
            return success;
        }
    }

}
