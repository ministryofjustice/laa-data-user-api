package uk.gov.justice.laa.datauserapi.security.rbac;

import org.jspecify.annotations.NonNull;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

/**
 * Fine-grained (resource/attribute level) authorisation rule evaluated after the coarse
 * {@link RequiresPermission} check and before the handler runs.
 *
 * <p>Implementations are Spring beans opted into per message via {@link RequiresPermission#policies()}.
 * Command-side policies belong in {@code application.command.policy} and query-side policies in
 * {@code application.query.policy}, so each may only read state through its own side's services.
 *
 * @param <M> the message (or message capability, e.g. {@link TargetsUser}) this policy understands
 */
public interface AuthorizationPolicy<M> {

    @NonNull PolicyDecision evaluate(M message, AuthenticatedEntraUser actor);
}
