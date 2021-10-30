package optimus.prime.rsa.config;

import java.math.BigInteger;
import java.util.List;

public class StaticConfiguration {
    public static int PORT;
    public static int SLAVE_WORKERS;
    public static final int MASTER_RESTART_TIMEOUT = 5000;
    public static List<BigInteger> primes = null;
}
