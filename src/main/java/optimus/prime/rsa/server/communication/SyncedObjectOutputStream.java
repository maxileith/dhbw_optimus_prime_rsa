package optimus.prime.rsa.server.communication;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * A special version of an {@link ObjectOutputStream} of which you
 * don't have to worry about using in different threads
 */
class SyncedObjectOutputStream extends ObjectOutputStream {

    public SyncedObjectOutputStream(OutputStream out) throws IOException {
        super(out);
    }

    /**
     * Writes an object to the {@link ObjectOutputStream} and flushes
     *
     * @param obj The object to send
     * @throws IOException any {@link Exception} thrown by the underlying {@link OutputStream}
     */
    public synchronized void writeSyncedObjectFlush(Object obj) throws IOException{
        super.writeObject(obj);
        super.flush();
    }
}
