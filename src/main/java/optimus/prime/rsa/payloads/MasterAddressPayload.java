package optimus.prime.rsa.payloads;

import java.io.Serializable;
import java.net.InetAddress;

/**
 * This payload can be used to tell a client the right master-address
 */
@SuppressWarnings("ClassCanBeRecord")
public class MasterAddressPayload implements Serializable {
    private final InetAddress masterAddress;

    /**
     * Create a new {@link MasterAddressPayload}
     *
     * @param masterAddress the ip-address of the current master
     */
    public MasterAddressPayload(InetAddress masterAddress) {
        this.masterAddress = masterAddress;
    }

    /**
     * Get the ip-address of the master
     *
     * @return the {@link InetAddress} of the master
     */
    public InetAddress getMasterAddress() {
        return this.masterAddress;
    }

}
