package optimus.prime.rsa.config;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class NetworkConfiguration {
    public static List<InetAddress> hosts = new ArrayList<>();
    public static InetAddress masterAddress;
    public static List<InetAddress> ownAddresses;
}
