# Optimus Prime RSA

This program can find two prime numbers from a set of given prime numbers, from which a private key can be generated
that matches a given public key. Afterwards, the private key can be used to decrypt a cipher.

The special feature is that this is done in a distributed manner. This means that the program can run on several hosts
simultaneously to find a solution.

## Pre-start information

- The same program is executed on all hosts.
- Within the process, a distinction is made between _masters_ and _slaves_.
    - The _master_ is the process that tells the other processes (_slaves_) what their task is. In addition, information
      about the _master_ is distributed, which is needed should a _slave_ take over the role of the _master_ in the
      future.
    - _Slaves_ provide workers that process the task assigned by the _master_. If a _master_ fails, a _slave_ becomes
      the _master_.
- The process that is the master also hosts a slave unless otherwise specified.

## Starting the process

The master must be started first. After that, slaves can connect.

**Requirements:**

* The Java Runtime supports class file version `60.0`
* On each host, the firewall is opened for port `2504` or as specified in `--port` for incoming TCP traffic.

In the following, the following **preconditions are assumed**:

* The current master-ip is `192.168.42.100`
* _Master_ and _Slaves_ are on the same network

### Starting the _master_

The following arguments are required:

- `--cipher`
- `--pub-key-rsa`

The following arguments are recommended to use:

- `--master-address`
- `--primes`

```bash
java -jar optimus-prime-rsa-0.1-all.jar --master-address 192.168.42.100 --pub-rsa-key <key> --cipher <cipher>
```

### Starting a _slave_

There are no arguments required.

The following arguments are recommended to use:

- `--master-address`

```bash
java -jar optimus-prime-rsa-0.1-all.jar --master-address 192.168.42.100
```

## Command line arguments

| Key                                    | Description                                                             | Master-Only | Default         | Required    |
| -------------------------------------- | ----------------------------------------------------------------------- | ----------- | :-------------- | ----------- |
| `--master-address`                     | defines the ip-address of the current master                            | `false`     | `localhost`     | `false`     |
| `--master-checks-per-slice-per-worker` | defines the number of checks per slice per worker                       | `true`      | `150000`        | `false`     |
| `--port`                               | defines the TCP port to use for communication                           | `false`     | `2504`          | `false`     |
| `--workers`                            | defines the number of the threads that are used to crack the key        | `false`     | `<threads> - 1` | `false`     |
| `--pub-key-rsa`                        | defines the public-key to crack                                         | `true`      |                 | `if master` |
| `--cipher`                             | defines encrypted payload to decrypt                                    | `true`      |                 | `if master` |
| `--max-slaves`                         | defines how many slaves can connect to the master                       | `true`      | `1000`          | `false`     |
| `--primes`                             | defines the prime list to use (100, 1000, 10000, 100000 or custom file) | `true`      | `100`           | `false`     |

## Tips

* set `--workers` to `0` on the master to use it for communication only.
