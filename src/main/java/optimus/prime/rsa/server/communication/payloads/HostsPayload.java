package optimus.prime.rsa.server.communication.payloads;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
public class HostsPayload implements Serializable {
    private final List<InetAddress> hosts;

    public HostsPayload(List<InetAddress> hosts) {
        this.hosts = hosts;
    }

    public List<InetAddress> getHosts() {
        return this.hosts;
    }

}
