package optimus.prime.rsa.config;

import optimus.prime.rsa.communication.payloads.SlicePayload;

import java.math.BigInteger;
import java.util.Queue;

public class MasterConfiguration {
    public static BigInteger PUB_RSA_KEY;
    public static String CIPHER;
    public static int MASTER_SLICE_SIZE;
    public static int MAX_INCOMING_SLAVES;
    public static Queue<SlicePayload> slicesToDo;
    public static boolean isMaster = false;
}
