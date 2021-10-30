package optimus.prime.rsa.config;

import optimus.prime.rsa.communication.payloads.SlicePayload;

import java.math.BigInteger;
import java.util.Queue;

public class MasterConfiguration {
    public static long MASTER_CHECKS_PER_SLICE;
    public static int MAX_INCOMING_SLAVES;
    public static Queue<SlicePayload> slicesToDo = null;
    public static boolean isMaster = false;
}
