# Environments

- local: developer machine / Docker
- CI: ephemeral Testcontainers
- integration: externally deployed ledger if configured

Configuration must be environment-driven; test code must not hardcode secrets.
