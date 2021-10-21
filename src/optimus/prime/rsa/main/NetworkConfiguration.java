package optimus.prime.rsa.main;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class NetworkConfiguration {
    private final List<InetAddress> hosts = new ArrayList<>();
    private InetAddress masterAddress;

    public static final int PORT = 2504;

    public NetworkConfiguration(InetAddress masterAddress) {
        this.masterAddress = masterAddress;
    }

    public void addHost(InetAddress host) {
        hosts.add(host);
    }

    public List<InetAddress> getHosts() {
        return hosts;
    }

    public void setMasterAddress(InetAddress masterAddress) {
        this.masterAddress = masterAddress;
    }

    public InetAddress getMasterAddress() {
        return masterAddress;
    }
}
