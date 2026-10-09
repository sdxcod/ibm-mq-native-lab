## Executed successfully

- Built with Java 21 and Maven 3.9.9, including the generated Maven Wrapper (`./mvnw verify`). Spring Boot 4.1.1 and IBM MQ allclient 9.4.0.25 resolved from Maven Central.
- **19 unit tests: 0 failures, 0 errors, 0 skipped.** These use mock queue-manager/queue handles and real IBM message/options classes; they do not replace live integration tests.
- Maven Enforcer Java-version and banned-JMS-dependency rules passed. The executable JAR has no `spring-jms` or JMS API dependency JARs, and source code has no JMS imports.
- Executable JAR `help` and interactive shell startup/status/quit ran successfully.
- A real IBM native client connection attempt against an intentionally closed local port returned MQ reason 2538; one-shot receive exited nonzero. This confirms client loading and error propagation, not successful MQ connectivity.
- Docker Compose v2.39.4 `config -q` passed, without starting containers.
- YAML parsing and Bash syntax checks passed.
- Kustomize v5.7.1 rendered the MQ manifests; kubeconform v0.6.7 validated the rendered resources plus the demo Job against Kubernetes 1.34.0 schemas: **5 valid, 0 invalid, 0 errors, 0 skipped**. This is static validation, not deployment.
- MQSC copies for Compose/Kubernetes are identical.
- Secret setup creates exact passwords, preserves them on rerun, and Git ignores secrets and build output.
- The pinned IBM MQ image tag's registry manifest was reachable. The image was not pulled or executed here.

## Included but not executed here

Docker daemon and a Kubernetes cluster were not available in the preparation environment. Therefore these are **unverified at runtime**:

- IBM MQ container startup, secret authentication, custom queue initialization and successful puts/gets.
- `./scripts/lab.sh demo` against a live queue manager.
- Four `NativeMqIT` integration tests under `-Pmq-it`.
- Application Docker image build/run and Kubernetes StatefulSet/PVC/Job deployment.
- GitHub Actions workflow execution.

## Complete local acceptance

```bash
./scripts/init-lab.sh
./mvnw -B -ntp verify
docker compose up -d --wait --wait-timeout 360 mq
./mvnw -B -ntp -Pmq-it verify
./scripts/lab.sh demo
docker compose --profile app build agent
docker compose --profile app run --rm agent
```

Expect 19 passing unit tests, 4 passing live integration tests, and 3 `PASS:` lines from each demo. Test execution may need internet access for first-time Maven dependencies and image pulls. Use the README Kubernetes steps for deployment acceptance. Replace these preparation notes with your own observed local/CI results before presenting live-test claims in a portfolio.
