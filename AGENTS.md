# Backend Agent Rules

Read and follow `../AGENTS.md` before changing backend code.

Backend-specific additions:

- Treat authentication, authorization and all client input as untrusted boundaries.
- Keep controllers thin and place business rules in domain or service code.
- Validate multiplayer actions on the server; never trust client-calculated permissions or game state.
- Add tests for new endpoints, authorization rules and important domain behavior.
- Run the Gradle tests and build before considering backend work complete.
