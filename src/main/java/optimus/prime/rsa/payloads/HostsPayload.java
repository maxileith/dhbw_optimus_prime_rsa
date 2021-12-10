package optimus.prime.rsa.payloads;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;


/**
 * This payload can be used to exchange the host list
 */
@SuppressWarnings("ClassCanBeRecord")
public class HostsPayload implements Serializable {
    private final List<InetAddress> hosts;

    public HostsPayload(List<InetAddress> hosts) {
        this.hosts = hosts;
    }

    /**
     * Get the list of hosts
     *
     * @return the {@link List} of hosts
     */
    public List<InetAddress> getHosts() {
        return this.hosts;
    }

}
