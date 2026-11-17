package com.sprintflowinsight.realtime;

/**
 * What subscribers of {@code /topic/projects/{projectId}} receive, and what the gap endpoint
 * returns. It names what changed, never its content; clients reload the entity through the REST
 * API, which applies the normal authorisation.
 *
 * @param seq strictly increasing per project, without holes except where retention pruned rows
 * @param type the module event name, for example {@code TaskMoved}
 * @param entityId the entity identified by type (task, sprint, story or project); comment events
 *     identify the commented task, while notification events have no entity id
 * @param version the entity version after the change, {@code null} when deleted or unknown
 */
public record RealtimeMessage(long projectId, long seq, String type, Long entityId, Long version) {}
