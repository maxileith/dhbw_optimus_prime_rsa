package optimus.prime.rsa.communication;

import optimus.prime.rsa.communication.payloads.HostsPayload;
import optimus.prime.rsa.communication.payloads.SlicePayload;
import optimus.prime.rsa.communication.payloads.SolutionPayload;
import optimus.prime.rsa.crypto.Worker;
import optimus.prime.rsa.main.NetworkConfiguration;
import optimus.prime.rsa.main.Utils;

import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class Slave implements Runnable {
    private Socket socket;
    private ObjectOutputStream objectOutputStream;
    private Receiver receiver;
    private Thread receiveThread;

    private final NetworkConfiguration networkConfig;
    private final List<BigInteger> primes;
    private Queue<SlicePayload> currentMinorSlices;

    private final int WORKERS = 7;

    private boolean running = true;

    public Slave(NetworkConfiguration networkConfig, List<BigInteger> primes) {
        this.networkConfig = networkConfig;
        this.primes = primes;

        try {
            this.socket = new Socket(
                    networkConfig.getMasterAddress(),
                    NetworkConfiguration.PORT
            );
            System.out.println("Slave  - established connection to master");

            InputStream inputStream = this.socket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            OutputStream outputStream = this.socket.getOutputStream();
            this.objectOutputStream = new ObjectOutputStream(outputStream);
            this.receiver = new Receiver(objectInputStream);
            this.receiveThread = new Thread(this.receiver);
            this.receiveThread.start();
            System.out.println("Slave  - started receiveThread");
        } catch (IOException e) {
            System.err.println("Slave  - The master is probably not reachable. " + e);
            this.running = false;
        }
    }

    @Override
    public void run() {
        if (!this.running) {
            return;
        }
        try {
            ExecutorService es = Executors.newFixedThreadPool(this.WORKERS); //FIXME: change dynamic
            CompletionService<SolutionPayload> cs = new ExecutorCompletionService<>(es);

            System.out.println("Slave  - Sending hello message to master");

            Message initM = new Message(MessageType.SLAVE_JOIN);
            this.objectOutputStream.writeObject(initM);
            this.objectOutputStream.flush();

            while (this.running) {

                // wait for the slice queue to get filled
                while ((this.currentMinorSlices == null || this.currentMinorSlices.isEmpty()) && !this.socket.isClosed()) {
                    Thread.sleep(5);
                }

                System.out.println("Slave  - New work is assigned to the workers");
                // do the math
                while (!this.currentMinorSlices.isEmpty()) {
                    cs.submit(new Worker(
                            this.currentMinorSlices.remove(),
                            this.primes
                    ));
                }

                // collect the results
                for (int resultsReceived = 0; resultsReceived < this.WORKERS && this.running; resultsReceived++) {
                    try {
                        Future<SolutionPayload> f = cs.take();
                        SolutionPayload s = f.get();
                        System.out.println("Slave  - received new result from a worker");
                        // Solution found
                        if (!s.equals(SolutionPayload.NO_SOLUTION)) {
                            Message m = new Message(MessageType.SLAVE_SOLUTION_FOUND, s);
                            this.objectOutputStream.writeObject(m);
                            this.objectOutputStream.flush();
                            this.running = false;
                            System.out.println("Slave  - worker found a solution! " + s);
                        }
                    } catch (ExecutionException e) { // FIXME: macht das sinn?
                        System.err.println("Slave - Error in Worker: ");
                        e.printStackTrace();
                        this.running = false;
                    }
                }

                if (this.running) {
                    Message m = new Message(MessageType.SLAVE_FINISHED_WORK);
                    this.objectOutputStream.writeObject(m);
                    this.objectOutputStream.flush();
                    System.out.println("Slave  - finished work");
                }
            }
            System.out.println("Slave  - stopped");
            es.shutdown();

            // wait for the receiver to terminate gracefully
            this.receiveThread.join();

            System.out.println("Slave  - Thread terminated");

            // TODO: Sender
        } catch (IOException | InterruptedException e) {
            System.err.println("Slave  - An error occurred." + e);
        }
    }

    private synchronized void setCurrentSlice(SlicePayload majorSlice) {
        this.currentMinorSlices = Utils.getNSlices(majorSlice.getStart(), majorSlice.getEnd(), WORKERS);
    }

    private void stopSlave() {
        System.out.println("Slave  - sending SLAVE_EXIT_ACKNOWLEDGE");
        Message m = new Message(MessageType.SLAVE_EXIT_ACKNOWLEDGE);
        try {
            this.objectOutputStream.writeObject(m);
            this.objectOutputStream.flush();
            this.socket.close();
        } catch (IOException e) {
            System.err.println("Slave  - failed to send SLAVE_EXIT_ACKNOWLEDGE");
        }
        // do not wait for the receiver thread to be terminated here,
        // because this method gets called by the receiver. so if joining
        // here the receiver is stuck waiting for itself.
        this.running = false;
    }

    private class Receiver implements Runnable {

        private boolean running = true;
        private final ObjectInputStream objectInputStream;

        public Receiver(ObjectInputStream dataInputStream) {
            this.objectInputStream = dataInputStream;
        }

        @Override
        public void run() {
            try {
                while (this.running) {
                    MultiMessage messages = (MultiMessage) this.objectInputStream.readObject();
                    this.handleMessages(messages);
                }
            } catch (IOException | ClassNotFoundException e) {
                if (this.running) {
                    System.err.println("Slave  - Receiver - failed to read incoming object - " + e);
                } else {
                    System.out.println("Slave  - Receiver - stopped on purpose");
                }
            }
            System.out.println("Slave  - Receiver - terminated");
        }

        private void handleMessages(MultiMessage messages) {
            System.out.println("Master - ConnectionHandler - Received MultiMessage");
            for (Message m : messages.getAllMessages()) {
                switch (m.getType()) {
                    case MASTER_HOSTS_LIST -> this.handleHostList(m);
                    case MASTER_DO_WORK -> this.handleNewWork(m);
                    case MASTER_EXIT -> this.stopReceiver();
                    default -> System.out.println("I do not know what to do");
                }
            }
        }

        private void handleHostList(Message m) {
            HostsPayload hostsPayload = (HostsPayload) m.getPayload();
            networkConfig.setHosts(hostsPayload.getHosts());
            System.out.println("Slave  - Receiver - Received new host list");
        }

        private void handleNewWork(Message m) {
            SlicePayload slicePayload = (SlicePayload) m.getPayload();
            setCurrentSlice(slicePayload);
            System.out.println("Slave  - Receiver - Received new working package");
        }

        public void stopReceiver() {
            System.out.println("Slave  - Receiver - MASTER_EXIT");
            System.out.println("Slave  - Receiver - stopping receiver");
            this.running = false;
            stopSlave();
        }
    }
}
