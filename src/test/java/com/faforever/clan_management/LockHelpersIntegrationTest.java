//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.service.MembershipService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

//the clan lock helpers called with no transaction around them, which is a mistake in the caller and is refused before any query runs
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class LockHelpersIntegrationTest {

    @Autowired
    private MembershipService members;

    @Test
    @DisplayName("the lock helpers refuse to run outside a transaction, where a row lock would mean nothing")
    void lockHelpersNeedATransaction() {
        AppUser nobody = new AppUser("lock-helper-test", "lock_helper_test","local", null); //never saved, nothing reaches the database
        assertThatThrownBy(() -> members.lockClan(1L)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> members.lockedMembership(nobody)).isInstanceOf(IllegalTransactionStateException.class);
        //System.out.println("refused outside a transaction");
        assertThatThrownBy(() -> members.manager(nobody, "unused")).isInstanceOf(IllegalTransactionStateException.class);
    }
}
