package optimus.prime.rsa.communication;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

class SyncedObjectOutputStream extends ObjectOutputStream {

    public SyncedObjectOutputStream(OutputStream out) throws IOException {
        super(out);
    }

    protected SyncedObjectOutputStream() throws IOException, SecurityException {
    }

    public synchronized void writeSyncedObjectFlush(Object obj) throws IOException{
        super.writeObject(obj);
        super.flush();
    }
}
