/**
 * Application-level encryption of secrets at rest: AES-256-GCM with versioned keys and a
 * re-encryption job for key rotation (ADR 0011).
 */
@org.springframework.modulith.NamedInterface("crypto")
package com.sprintflowinsight.common.crypto;
