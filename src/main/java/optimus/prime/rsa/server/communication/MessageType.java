package optimus.prime.rsa.server.communication;

enum MessageType {
    /*
    every type of message sent by a slave has prefix "SLAVE"
    every type of message sent by the master has the prefix "MASTER"
    */
    MASTER_HOSTS_LIST,
    MASTER_DO_WORK,
    MASTER_EXIT,
    MASTER_SEND_PRIMES,
    MASTER_SEND_PUB_KEY_RSA,
    SLAVE_JOIN,
    SLAVE_FINISHED_WORK,
    SLAVE_SOLUTION_FOUND,
    SLAVE_EXIT_ACKNOWLEDGE,
    MASTER_UNFINISHED_SLICES,
    MASTER_CIPHER,
}
