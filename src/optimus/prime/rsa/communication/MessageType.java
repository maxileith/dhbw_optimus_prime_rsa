package optimus.prime.rsa.communication;

public enum MessageType {
    /*
    every type of message sent by a slave has prefix "SLAVE"
    every type of message sent by the master has the prefix "MASTER"
     */
    SELECT_MASTER,
    MASTER_DISTRIBUTE_HOSTS,
    SLAVE_JOIN,
    SLAVE_FINISHED_WORK,
    MASTER_HOSTS_LIST,
    MASTER_PRIMES_LIST,
    SLAVE_SOLUTION_FOUND,
    DO_WORK,
    CONNECTION_LOST,
    DISCONNECT
}