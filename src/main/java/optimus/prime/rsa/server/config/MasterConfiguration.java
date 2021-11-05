package optimus.prime.rsa.server.config;

import optimus.prime.rsa.payloads.SlicePayload;
import optimus.prime.rsa.payloads.SolutionPayload;

import java.util.LinkedList;
import java.util.Queue;

public class MasterConfiguration {
    public static long MASTER_CHECKS_PER_SLICE_PER_WORKER;
    public static int MAX_INCOMING_SLAVES;
    public static int currentSliceStart = 0;
    public static Queue<SlicePayload> lostSlices = new LinkedList<>();
    public static boolean isMaster = false;
    public static long startMillis = 0;
    public static SolutionPayload solution = null;
}
