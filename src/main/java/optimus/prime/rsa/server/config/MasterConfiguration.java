package optimus.prime.rsa.server.config;

import optimus.prime.rsa.server.communication.payloads.SlicePayload;

import java.util.Queue;

public class MasterConfiguration {
    public static long MASTER_CHECKS_PER_SLICE;
    public static int MAX_INCOMING_SLAVES;
    public static Queue<SlicePayload> slicesToDo = null;
    public static boolean isMaster = false;
}