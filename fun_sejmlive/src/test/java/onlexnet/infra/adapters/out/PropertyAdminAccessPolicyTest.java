package onlexnet.infra.adapters.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminActor;

class PropertyAdminAccessPolicyTest {

    @Test
    void givenMatchingTelegramActor_whenChecked_thenAccessIsAllowed() {
        var policy = new PropertyAdminAccessPolicy("1001");

        var allowed = policy.isAllowed(
                new AdminActor.ExternalActor("1001"),
                AdminAction.Help.INSTANCE);

        assertThat(allowed).isTrue();
    }

    @Test
    void givenDifferentTelegramActor_whenChecked_thenAccessIsDenied() {
        var policy = new PropertyAdminAccessPolicy("1001");

        var allowed = policy.isAllowed(
                new AdminActor.ExternalActor("2002"),
                AdminAction.Help.INSTANCE);

        assertThat(allowed).isFalse();
    }
}
