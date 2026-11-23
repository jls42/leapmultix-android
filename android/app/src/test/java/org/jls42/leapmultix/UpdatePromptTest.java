package org.jls42.leapmultix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.play.core.install.model.InstallStatus;
import org.junit.Test;

/** Invite de mise à jour : une version refusée n'est pas reproposée à chaque lancement */
public class UpdatePromptTest {

    private static final long REFUSED_AT = 1_800_000_000_000L;
    private static final long HOUR = 3_600_000L;

    @Test
    public void refuseeAlInstant_pasDeNouvelleInvite() {
        assertTrue(UpdatePrompt.declinedRecently(10500, REFUSED_AT, 10500, REFUSED_AT + HOUR));
        assertTrue(UpdatePrompt.declinedRecently(10500, REFUSED_AT, 10500, REFUSED_AT + UpdatePrompt.ASK_AGAIN_MS - 1));
    }

    @Test
    public void delaiPasse_inviteDeNouveau() {
        assertFalse(UpdatePrompt.declinedRecently(10500, REFUSED_AT, 10500, REFUSED_AT + UpdatePrompt.ASK_AGAIN_MS));
    }

    @Test
    public void versionPlusRecente_inviteToutDeSuite() {
        assertFalse(UpdatePrompt.declinedRecently(10500, REFUSED_AT, 10600, REFUSED_AT + HOUR));
    }

    @Test
    public void jamaisRefusee_ouHorlogeReculee_invite() {
        assertFalse(UpdatePrompt.declinedRecently(0, 0, 10500, REFUSED_AT));
        assertFalse(UpdatePrompt.declinedRecently(10500, REFUSED_AT, 10500, REFUSED_AT - HOUR));
    }

    @Test
    public void telechargementEnCours_pasDeSecondeInvite() {
        assertTrue(UpdatePrompt.inProgress(InstallStatus.PENDING));
        assertTrue(UpdatePrompt.inProgress(InstallStatus.DOWNLOADING));
        assertTrue(UpdatePrompt.inProgress(InstallStatus.INSTALLING));
        assertFalse(UpdatePrompt.inProgress(InstallStatus.UNKNOWN));
        assertFalse(UpdatePrompt.inProgress(InstallStatus.DOWNLOADED));
        assertFalse(UpdatePrompt.inProgress(InstallStatus.FAILED));
        assertFalse(UpdatePrompt.inProgress(InstallStatus.CANCELED));
    }
}
