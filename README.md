# Optimus Prime RSA

This program can find two prime numbers from a set of given prime numbers, from which a private key can be generated
that matches a given public key. Afterwards, the private key can be used to decrypt a cipher.

The special feature is that this is done in a distributed manner. This means that the program can run on several hosts
simultaneously to find a solution.

## Pre-start information

* The same program is executed on all hosts.
* Within the process, a distinction is made between _masters_ and _slaves_.
    * The _master_ is the process that tells the other processes (_slaves_) what their task is. In addition, information
      about the _master_ is distributed, which is needed should a _slave_ take over the role of the _master_ in the
      future.
    * _Slaves_ provide workers that process the task assigned by the _master_. If a _master_ fails, a _slave_ becomes
      the _master_.
* The process that is the master also hosts a slave unless otherwise specified.
* To give a task into the distributed system, there is a client software.

## Starting a _server_

The master must be started first. After that, slaves and clients can connect.

**Requirements:**

* The Java Runtime supports class file version `60.0`
* On each server, the firewall is opened for port `2504` and `2505` or as specified in `--intra-port` and `--client-port`
  for incoming TCP traffic.

In the following, the following **preconditions are assumed**:

* The ip of the host that the master is running on is `192.168.42.100`
* The ip of the host that a slave is running on is `192.168.42.200`
* _Master_ and _Slaves_ are on the same network: `192.168.42.0/24`

### Starting the _master_

No arguments are required to start a master, since the default master address ist `localhost`.

The following arguments are **recommended** to use:

* `--master-address`: Use a non-localhost address, that is used by a NIC on the host

```bash
java -jar optimus-prime-rsa-0.1-server.jar --master-address 192.168.42.100
```

### Starting a _slave_

The following arguments are required:

* `--master-address`: Specify the master address. Hint: This has to be an address not associated to any NIC on the host.

```bash
java -jar optimus-prime-rsa-0.1-server.jar --master-address 192.168.42.100
```

### Command line arguments

| Key                                    | Description                                                      | Master-Only | Default         | Required |
|----------------------------------------|------------------------------------------------------------------|-------------|:----------------|----------|
| `--master-address`                     | defines the ip-address of the current master                     | `false`     | `localhost`     | `false`  |
| `--master-checks-per-slice-per-worker` | defines the number of checks per slice per worker                | `true`      | `150000`        | `false`  |
| `--intra-port`                         | defines the TCP port to use for communication between server     | `false`     | `2504`          | `false`  |
| `--client-port`                        | defines the TCP port to use for communication with the client    | `false`     | `2505`          | `false`  |
| `--workers`                            | defines the number of the threads that are used to crack the key | `false`     | `<threads> - 1` | `false`  |
| `--max-slaves`                         | defines how many slaves can connect to the master                | `true`      | `1000`          | `false`  |

## Starting the _client_

The master must be started first. After that a client can connect.

**Requirements:**

* The Java Runtime supports class file version `60.0`

In the following, the following **preconditions are assumed**:

* The _master_ is running
* The ip of the host that the master is running on is `192.168.42.100`
* _Client_ and _master_ are on the same network: `192.168.42.0/24`

The following arguments are required:

* `--ip-address`: Specify an ip-address of a server of the distributed system
* `--pub-key-rsa`: Specify the public key as BigInt
* `--cipher`: The cipher to crack

The following arguments are **recommended** to use:

* `--primes`: Specify the primes list to use

```bash
java -jar optimus-prime-rsa-0.1-client.jar \
    --ip-address 192.168.42.100 \
    --pub-key-rsa 237023640130486964288372516117459992717 \
    --cipher a9fc180908ad5f60556fa42b3f76e30f48bcddfad906f312b6ca429f25cebbd0 \
    --primes 10000
```

### Command line arguments

| Key             | Description                                                             | Default | Required |
|-----------------|-------------------------------------------------------------------------|:--------|----------|
| `--ip-address`  | ip-address of a random server                                           |         | `true`   |
| `--port`        | defines the TCP port to use for communication with the system           | `2505`  | `false`  |
| `--pub-key-rsa` | defines the public-key to crack                                         |         | `true`   |
| `--cipher`      | defines encrypted payload to decrypt                                    |         | `true`   |
| `--primes`      | defines the prime list to use (100, 1000, 10000, 100000 or custom file) | `100`   | `false`  |

## Hints

* set `--workers` to `0` on the master to use it for communication only.
