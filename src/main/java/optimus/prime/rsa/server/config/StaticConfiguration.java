package optimus.prime.rsa.server.config;

import java.math.BigInteger;
import java.util.List;

public class StaticConfiguration {
    public static int PORT;
    public static final int MASTER_RESTART_TIMEOUT = 5000;
    public static List<BigInteger> primes = null;
    public static BigInteger PUB_RSA_KEY;
    public static String CIPHER;
}
