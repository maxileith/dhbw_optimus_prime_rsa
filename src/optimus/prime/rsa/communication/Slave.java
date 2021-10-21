package optimus.prime;

import optimus.prime.rsa.main.NetworkConfiguration;
import java.io.IOException;
import java.net.Socket;

public class Slave implements Runnable {
    private Socket masterSocket;

    private NetworkConfiguration networkConfig;

    public Slave(NetworkConfiguration networkConfig) {
        this.networkConfig = networkConfig;

        try {

        } catch(IOException e) {

        }
    }

    @Override
    public void run() {

    }
}
