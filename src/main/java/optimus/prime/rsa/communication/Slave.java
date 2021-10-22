package optimus.prime.rsa.communication;

import optimus.prime.rsa.communication.payloads.HostsPayload;
import optimus.prime.rsa.communication.payloads.SolutionPayload;
import optimus.prime.rsa.communication.payloads.TaskPayload;
import optimus.prime.rsa.crypto.Worker;
import optimus.prime.rsa.main.Main;
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

    private int sliceSize;
    private int startIndex;

    private Queue<Integer> sliceQueue;

    private boolean running = true;

    public Slave(NetworkConfiguration networkConfig, List<BigInteger> primes) {
        this.networkConfig = networkConfig;
        this.primes = primes;

        try {
            this.socket = new Socket(
                    networkConfig.getMasterAddress(),
                    NetworkConfiguration.PORT
            );

            InputStream inputStream = this.socket.getInputStream();
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            this.receiver = new Receiver(objectInputStream);
            this.receiveThread = new Thread(this.receiver);
            this.receiveThread.start();
        } catch(IOException e) {
            System.err.println("Slave  - The master is probably not reachable. " + e);
            this.running = false;
        }
    }

    @Override
    public void run() {
        if (!this.running) {
            return;
        }
        try  (
            OutputStream outputStream = this.socket.getOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)
        ) {
            ExecutorService es = Executors.newFixedThreadPool(10); //FIXME: change dynamic
            CompletionService<SolutionPayload> cs = new ExecutorCompletionService<>(es);

            System.out.println("Slave  - Sending hello message to master" );

            Message initM = new Message(MessageType.SLAVE_JOIN);
            objectOutputStream.writeObject(initM);
            objectOutputStream.flush();

            while(this.running) {

                // wait for the slice queue to get filled
                while (this.sliceQueue == null || this.sliceQueue.isEmpty()) {
                    Thread.sleep(5);
                }

                int workers = this.sliceQueue.size();

                // do the math
                while (!this.sliceQueue.isEmpty()) {
                    cs.submit(new Worker(
                            this.sliceQueue.remove() + this.startIndex,
                            Main.SLAVE_SLICE_SIZE,
                            this.primes
                    ));
                }

                // collect the results
                int resultsReceived = 0;
                while(resultsReceived < workers && this.running) {
                    try {
                        Future<SolutionPayload> f = cs.take();
                        SolutionPayload s = f.get();
                        resultsReceived++;
                        // Solution found
                        if(!s.equals(SolutionPayload.NO_SOLUTION)){
                            Message m = new Message(MessageType.SLAVE_SOLUTION_FOUND, s);
                            objectOutputStream.writeObject(m);
                            objectOutputStream.flush();
                            this.running = false;
                        }
                    } catch(ExecutionException e) { // FIXME: macht das sinn?
                        this.running = false;
                    }
                }

                if (this.running) {
                    Message m = new Message(MessageType.SLAVE_FINISHED_WORK);
                    objectOutputStream.writeObject(m);
                    objectOutputStream.flush();
                    System.out.println("Slave  - finished work");
                } else {
                    System.out.println("Salve  - stopped on purpose");
                }
            }

            // TODO: Sender
        } catch(IOException | InterruptedException e) {
            System.out.println("An error occurred." + e);
        }
    }

    private synchronized void setTaskDetails(TaskPayload taskPayload) {
        this.startIndex = taskPayload.getStartIndex();
        this.sliceSize = taskPayload.getSliceSize();

        this.sliceQueue = Utils.getIndicesToDo(sliceSize, Main.SLAVE_SLICE_SIZE);
    }

    private void stopSlave() {
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
                System.out.println("An error occurred." + e);
                e.printStackTrace();
            }
        }

        private void handleMessages(MultiMessage messages) {
            for (Message m: messages.getAllMessages()) {
                switch(m.getType()) {
                    case MASTER_HOSTS_LIST -> this.handleHostList(m);
                    case DO_WORK -> this.handleNewWork(m);
                    case MASTER_EXIT -> this.stop();
                    default -> System.out.println("I do not know what to do");
                };
            }
        }

        private void handleHostList(Message m) {
            HostsPayload hostsPayload = (HostsPayload) m.getPayload();
            networkConfig.setHosts(hostsPayload.getHosts());
        }

        private void handleNewWork(Message m) {
            TaskPayload taskPayload = (TaskPayload) m.getPayload();
            setTaskDetails(taskPayload);
        }

        public void stop() {
            try {
                this.objectInputStream.close();
            } catch (IOException e) {
                System.out.println("An error occured. " + e);
            }
            this.running = false;
            stopSlave();
        }
    }
}
