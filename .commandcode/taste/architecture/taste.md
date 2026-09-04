# Architecture preferences

- Treats model management differently from MCP/Skill: models have no market and no user publishing — they come in two scopes, OFFICIAL (admin-reviewed, usable by all logged-in users) and PRIVATE (user's own, usable only by that user). A published model release is directly usable without binding to an agent or per-binding secret provisioning. Confidence: 0.85
- In agent conversation, model selection should be remembered/persisted for the session and reused automatically on the next run (not a per-message temporary override); re-selecting overwrites the remembered model. Confidence: 0.8
- Model entities should carry a stable short `code` field to tag special functional types (e.g., `video`, `image`), kept separate from runtime capability flags (`capabilities`); schema additions like this are applied to both paired tables (draft and release). Confidence: 0.6
