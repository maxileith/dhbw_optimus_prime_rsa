package optimus.prime.rsa.payloads;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
public class MasterAddressPayload implements Serializable {
    private final InetAddress masterAddress;

    public MasterAddressPayload(InetAddress masterAddress) {
        this.masterAddress = masterAddress;
    }

    public InetAddress getMasterAddress() {
        return this.masterAddress;
    }

}
