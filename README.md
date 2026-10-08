# IBM MQ Native Java Lab

A small Java agent lab for learning **IBM MQ classes for Java** through explicit connections and transactions.

Spring Boot starts the CLI, binds `application.yml`, and wires components. **All messaging uses `com.ibm.mq.*` directly.** There is no Spring JMS, `JmsTemplate`, JMS listener, Jakarta Messaging, web server, or database.

## Stack and scope

| Component               | Version / role                              |
|-------------------------|---------------------------------------------|
| Java                    | 21 LTS                                      |
| Spring Boot             | 4.1.1; CLI bootstrap and configuration      |
| Maven Wrapper           | 3.9.9                                       |
| IBM MQ client           | `com.ibm.mq:com.ibm.mq.allclient:9.4.0.25`  |
| Local IBM MQ            | `icr.io/ibm-messaging/mq:9.4.0.25-r2`       |
| Queue manager / channel | `QM1` / `DEV.APP.SVRCONN`                   |
| Queues                  | `DEV.LAB.IN`, `DEV.LAB.OUT`, `DEV.LAB.TEST` |

The IBM allclient vendor JAR contains multiple APIs. Its transitive `javax.jms-api` dependency is excluded, and Maven Enforcer rejects JMS dependencies. The application imports only the proprietary MQ API. This is a learning baseline for understanding MQI, not a recommendation to choose the stabilized native Java API for every new production system.

## Run locally

Prerequisites: JDK 21, Docker with Docker Compose v2 (`--wait` support), Bash, OpenSSL, and internet access for dependencies/images. Maven is downloaded by the wrapper; it need not be installed. On Windows, use WSL2 with Docker Desktop integration. Run commands from the repository root. On macOS select Java 21 with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` and include `$JAVA_HOME/bin` in `PATH`.

```bash
./scripts/init-lab.sh
./mvnw -B -ntp verify
docker compose up -d --wait --wait-timeout 360 mq
./scripts/lab.sh demo
```

The demo checks three scenarios using only its own MQ message IDs on `DEV.LAB.TEST`: put/backout, put/commit plus get/backout with redelivery, and get/commit. It includes Persian UTF-8 text. Expected output contains three `PASS:` lines. Any failed assertion exits with an error.

The MQ image is configured with `LICENSE=accept` for IBM MQ Advanced for Developers. Read IBM's license terms before running it; this lab is intended for local development and learning. The MQ image is pinned to amd64. Docker Desktop on Apple Silicon needs amd64 emulation; Kubernetes manifests require amd64 nodes. Allocate roughly 4 GiB to your local Docker/Kubernetes VM. See [IBM image documentation](https://www.ibm.com/docs/en/ibm-mq/9.4.x?topic=reference-mq-advanced-developers-container-image).

## Send and receive

```bash
./scripts/lab.sh send --lab.queue=in --lab.message='Hello IBM MQ'
./scripts/lab.sh depth --lab.queue=in
./scripts/lab.sh receive --lab.queue=in --lab.wait=3s

# Roll back the send: no new committed message should be published.
./scripts/lab.sh send --lab.queue=out --lab.message='discard me' --lab.outcome=backout

# Roll back the receive: the message becomes available again.
./scripts/lab.sh send --lab.queue=out --lab.message='read me twice'
./scripts/lab.sh receive --lab.queue=out --lab.outcome=backout
./scripts/lab.sh receive --lab.queue=out --lab.outcome=commit
```

`send` returns a 48-character hexadecimal native MQ message ID. To select only that message, pass `--lab.message-id=THE_ID` to `receive`. These are independent one-shot commands: each opens and closes its own connection. Use the shell to keep a connection and control a multi-operation unit of work.

```bash
./scripts/lab.sh shell
```

```text
connect
status
put in Hello from the native MQ API
commit
get in
backout
get in
commit
depth in
disconnect
quit
```

A shell `put`/`get` remains pending until `commit` or `backout`. Commit covers all pending operations on that connection, even across queues. Closing the shell (including EOF) rolls back known pending work. Queue depth is a server snapshot and is not a reliable count of messages available to a particular consumer during concurrent/uncommitted work. `status` in the shell reports local connection state; one-shot `status` also performs a queue inquiry.

## Configuration

All properties are in [`application.yml`](src/main/resources/application.yml). Spring Boot's normal command-line overrides work; for example `--lab.mq.port=1514`. The common environment variables are:

| Variable                                                     | Default                                                 |
|--------------------------------------------------------------|---------------------------------------------------------|
| `LAB_MQ_HOST`                                                | `localhost` on the host; `mq` inside Compose/Kubernetes |
| `LAB_MQ_PORT`                                                | `1414`                                                  |
| `LAB_MQ_QMGR`                                                | `QM1`                                                   |
| `LAB_MQ_CHANNEL`                                             | `DEV.APP.SVRCONN`                                       |
| `LAB_MQ_USER`                                                | `app`                                                   |
| `LAB_MQ_PASSWORD_FILE`                                       | `.secrets/mqAppPassword`                                |
| `LAB_MQ_PASSWORD`                                            | Empty; optional override of the password file           |
| `LAB_MQ_IN_QUEUE` / `LAB_MQ_OUT_QUEUE` / `LAB_MQ_TEST_QUEUE` | `DEV.LAB.IN` / `DEV.LAB.OUT` / `DEV.LAB.TEST`           |

`init-lab.sh` creates random passwords without trailing newlines and preserves existing secrets. `.secrets` is ignored by Git and Docker builds. Local files are readable inside the containers; the enclosing host directory is mode 700. The app does not log passwords. This local lab uses password authentication over a loopback/private network; it does not configure TLS. Do not expose the MQ listener outside this local lab as a production setup.

IBM MQ 9.4 developer authentication is enabled with `MQ_CONNAUTH_USE_HTP=true` and mounted `mqAppPassword` / `mqAdminPassword` files. No authentication or channel-security bypass is needed. Custom queues use `DEV.**` developer app permissions. See [IBM developer configuration](https://github.com/ibm-messaging/mq-container/blob/master/docs/developer-config.md) and [secret authentication](https://github.com/ibm-messaging/mq-container/blob/master/docs/pluggable-connauth.md).

## Tests

```bash
# Unit tests and dependency rules; no MQ server required
./mvnw -B -ntp verify

# Live integration tests: MQ must already be ready
./mvnw -B -ntp -Pmq-it verify
```

Unit tests exercise real MQ message/options objects and mock server handles. Integration tests require a live queue manager and test committed persistent UTF-8 delivery, put/backout, get/backout/redelivery, and automatic rollback on close. They select only IDs created by the tests and never purge queues. GitHub Actions defines separate unit and Docker MQ integration jobs. See [verification notes](docs/verification.md) for checks actually performed during preparation; included workflows are not evidence of a successful CI run.

## Docker application

```bash
docker compose --profile app build agent
docker compose --profile app run --rm agent
docker compose --profile app run --rm agent --lab.command=shell
```

The application is a CLI container that exits after its command. It is not an HTTP service and does not publish a port. The interactive shell requires an interactive terminal. `docker compose logs mq` helps diagnose startup/authentication failures.

`scripts/lab.sh` uses an existing built JAR. After changing Java code or `application.yml`, run `./mvnw verify` to rebuild it.

The MQ data lives in a named volume. Stop without deleting data:

```bash
docker compose down
```

For a deliberate complete local reset, `docker compose down -v` deletes this project's MQ messages and data. If you change passwords, restart MQ so its secret authentication files are reread; do not regenerate secrets on every boot. Do not delete `.secrets` while reusing an existing setup without planning credential changes.

MQ Web Console: https://localhost:9443/ibmmq/console — log in as `admin` using the value in `.secrets/mqAdminPassword`. The local certificate is self-signed.

## Kubernetes

A single MQ StatefulSet with a PVC and headless service models persistent infrastructure. The agent runs as a one-shot Job, appropriate for this CLI lab. No operator or production HA is implied. A default storage class and amd64 worker node are required.

```bash
# Example local cluster; install kind separately
kind create cluster --name mq-lab

docker build -t ibm-mq-native-lab:0.1.0 .
kind load docker-image ibm-mq-native-lab:0.1.0 --name mq-lab
./scripts/k8s-up.sh
kubectl apply -f k8s/demo-job.yaml
kubectl -n ibm-mq-lab wait --for=condition=complete job/native-mq-demo --timeout=180s
kubectl -n ibm-mq-lab logs job/native-mq-demo
```

For minikube, use `minikube image load ibm-mq-native-lab:0.1.0` instead of `kind load`. `imagePullPolicy: Never` deliberately uses the image you loaded locally. The Job has no automatic retries (`backoffLimit: 0`). Before rerunning it, delete the old Job, then apply the Job file again. To connect your host app to Kubernetes MQ, run `kubectl -n ibm-mq-lab port-forward svc/mq 1514:1414` and use `--lab.mq.port=1514` so it can coexist with Compose.

The MQSC file exists in both `docker/mq` and `k8s` because kustomize reads files within its own directory; keep the copies identical. Kubernetes password secrets are created from ignored local files by the script. A namespace deletion removes the lab workloads and PVC; review storage cleanup before running `kubectl delete namespace ibm-mq-lab`.

## Code guide and portfolio

- [`NativeMqSession`](src/main/java/dev/sdxcod/mqlab/transport/NativeMqSession.java): connect/disconnect, local connection state, put/get, depth, commit/backout, resource cleanup.
- [`MessageSender`](src/main/java/dev/sdxcod/mqlab/service/MessageSender.java) and [`MessageReceiver`](src/main/java/dev/sdxcod/mqlab/service/MessageReceiver.java): separate modules using the transport contract.
- [`LabRunner`](src/main/java/dev/sdxcod/mqlab/cli/LabRunner.java): commands, interactive shell and transaction demo.
- [Architecture and transaction semantics](docs/architecture.md), [lab exercises](docs/exercises.md), [Persian quickstart](docs/quickstart-fa.md).