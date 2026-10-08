# Suggested lab exercises

1. Run `demo`, then inspect the native calls and options in `NativeMqSession`. Save the passing output for your portfolio.
2. Use the shell to put two messages before one commit. Receive them separately. Repeat the two puts with backout and observe that neither send is finalized.
3. Open two shells. Put in the first without committing, then attempt a get in the second. Commit the first and retry the get. Explain connection ownership and visibility using what you observe.
4. Get a message then backout; get it again and compare message ID and backout count. The ID stays the same.
5. Disconnect a shell with pending work. Reconnect and verify rollback. This is resource cleanup, not the same as an uncertain remote commit outcome.
6. Commit a persistent message, restart only MQ (`docker compose restart mq`), wait until healthy, then retrieve it by ID.
7. Configure a wrong password via an ignored environment file or local override, then inspect MQ logs and reason 2035. Restore the valid password. Do not disable authentication to make the test pass.
8. Run the integration suite, build the CLI Docker image, and repeat the demo inside Compose.
9. Run the Kubernetes Job. Inspect its logs, pod exit status, StatefulSet and PVC. Recreate only the Job and confirm MQ data is still on its PVC.
10. Compare host-to-Compose, Compose-to-Compose and host-to-Kubernetes port-forward addresses. Explain why `localhost` inside a container is that container, not the MQ service.
