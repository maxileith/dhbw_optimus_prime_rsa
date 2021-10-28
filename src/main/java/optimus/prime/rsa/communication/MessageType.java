package optimus.prime.rsa.communication;

public enum MessageType {
    /*
    every type of message sent by a slave has prefix "SLAVE"
    every type of message sent by the master has the prefix "MASTER"
     */
    SLAVE_JOIN,
    SLAVE_FINISHED_WORK,
    MASTER_HOSTS_LIST,
    SLAVE_SOLUTION_FOUND,
    MASTER_DO_WORK,
    MASTER_EXIT,
    SLAVE_EXIT_ACKNOWLEDGE,
    MASTER_SEND_PRIMES,
    MASTER_SEND_PUB_KEY_RSA
}
