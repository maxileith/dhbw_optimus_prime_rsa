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

    private boolean running = true;

    public Slave(NetworkConfiguration networkConfig) {
        this.networkConfig = networkConfig;

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
            System.out.println("An error occurred. " + e);
        }
    }

    @Override
    public void run() {
        try  (
            OutputStream outputStream = this.socket.getOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)
        ) {
            // TODO: ExecutorService
        } catch(IOException e) {
            System.out.println("An error occurred." + e);
        }
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
