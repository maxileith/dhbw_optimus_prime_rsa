package optimus.prime.rsa.communication.payloads;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class HostsPayload implements Serializable {
    private List<InetAddress> hosts;

    public HostsPayload() {
        this.hosts = new ArrayList<>();
    }

    public HostsPayload(List<InetAddress> hosts) {
        this.hosts = hosts;
    }

    public void setHosts(List<InetAddress> hosts) {
        this.hosts = hosts;
    }

    public List<InetAddress> getHosts() {
        return this.hosts;
    }

}
