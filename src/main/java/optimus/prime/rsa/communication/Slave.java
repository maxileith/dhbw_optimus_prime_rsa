package optimus.prime.rsa.communication;

import optimus.prime.rsa.main.NetworkConfiguration;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class Slave implements Runnable {
    private Socket socket;
    private ObjectOutputStream objectOutputStream;
    private Receiver receiver;
    private Thread receiveThread;

    private final NetworkConfiguration networkConfig;

    public Slave(NetworkConfiguration networkConfig) {
        this.networkConfig = networkConfig;

        try {

        } catch(IOException e) {

        }
    }

    @Override
    public void run() {

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
                    Message message = (Message) this.objectInputStream.readObject();
                    Consumer<Message> callback = callbacks.get(message.getMessageId());
                    callback.accept(message);
                    callbacks.remove(message.getMessageId());
                }
                this.objectInputStream.close();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("An error occurred." + e);
                e.printStackTrace();
            }
        }

        public void stop() {
            try {
                this.objectInputStream.close();
            } catch (IOException e) {
                System.out.println("An error occured. " + e);
            }
            this.running = false;
        }
    }
}
