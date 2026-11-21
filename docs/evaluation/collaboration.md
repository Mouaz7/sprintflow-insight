# Collaborative text verification

| Check | Reproducible input | Result |
| --- | --- | --- |
| Yjs convergence | Seed `20261121`, 64 trials with three independent clients and 24 random insertions or deletions per client | All 4,608 operations converged to identical text and encoded Yjs state after independent delivery orders and duplicate delivery. |
| Snapshot framing | jqwik seed `2026112105`, 100 cases using randomly generated binary frames | All cases preserved the frame sequence and every byte. |
| PostgreSQL integration | 130 ordered updates against PostgreSQL 16, with snapshot compaction at revision 128 | The snapshot preserved 128 updates, recovery returned the two-frame tail and retry reused the original revision. |
| PIT for snapshot framing | `CrdtFrames` with the STRONGER mutator set, threshold 70% | 35 of 38 mutations killed (92%). |
| Real WebSockets and network faults | Two Chromium contexts with Toxiproxy 2.12.0, 450 ms latency in each direction, then disable and re-enable the peer proxy | Both browsers converged after simultaneous and offline edits. A new browser load recovered the same text, and the task and retrospective APIs exposed the saved text. The open task editor passed its axe check. |

The network test assigns one client a prefix and the other a suffix. This gives the test an exact expected result. The fixed-seed property also exercises competing changes at the same index, noncausal delivery and repeated updates.

Run the property and STOMP framing tests with `npm --prefix frontend test -- src/shared/collaboration/text.test.ts src/features/realtime/stomp.test.ts`. The Java integration tests use PostgreSQL 16 via Testcontainers. Run them from `backend` with `./mvnw -Dtest=CrdtFramesTest,CrdtStoreIntegrationTest,CrdtHttpIntegrationTest,RealtimeWebSocketIntegrationTest,OpenApiContractTest test`.

For the browser flow, start the application, start Toxiproxy on loopback port 8474 and run `E2E_TOXIPROXY_API=http://127.0.0.1:8474 npm --prefix frontend/e2e test -- tests/shared-text.e2e.ts`. The test creates and removes its proxy on port 8089. Set `E2E_BASE_URL` or `E2E_TOXIPROXY_PORT` when using a different port. CI enables both latency and disconnection. See [shared text protocol](../collaboration.md).
