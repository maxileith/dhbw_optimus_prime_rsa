package optimus.prime.rsa;

public enum MessageType {
    /*
    every type of message sent by a slave has prefix "SLAVE"
    every type of message sent by the master has the prefix "MASTER"
    every type of message sent by the client has the prefix "CLIENT"
    */
    MASTER_HOSTS_LIST,
    MASTER_DO_WORK,
    MASTER_EXIT,
    MASTER_SEND_PRIMES,
    MASTER_SEND_PUB_KEY_RSA,
    MASTER_LOST_SLICES,
    MASTER_CIPHER,
    MASTER_START_MILLIS,
    MASTER_SOLUTION_FOUND,
    MASTER_CONFIRM,
    MASTER_START_MESSAGE,
    SLAVE_JOIN,
    SLAVE_FINISHED_WORK,
    SLAVE_SOLUTION_FOUND,
    SLAVE_EXIT_ACKNOWLEDGE,
    SLAVE_NOT_MASTER,
    SLAVE_GET_FIRST_SLICE,
    CLIENT_NEW_MISSION,
}
