package org.jls42.leapmultix;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import java.util.concurrent.TimeUnit;

/**
 * Invite de mise à jour au lancement, par Google Play (« in-app updates », mise à jour souple).
 *
 * L'appli demande au Play Store installé sur le téléphone si une version plus récente existe ;
 * s'il y en a une, Google Play affiche sa propre fenêtre (« Mettre à jour » ou « Non merci »),
 * dans la langue du téléphone. Acceptée, la mise à jour se télécharge pendant que l'enfant joue,
 * sans jamais couper une partie : elle s'installe au lancement suivant. Refusée, la même version
 * n'est reproposée qu'après ASK_AGAIN_MS.
 *
 * Tout échec reste muet et le jeu démarre comme avant : appli installée hors du Play Store
 * (appli d'essai), Play Store absent, pas de réseau.
 */
final class UpdatePrompt {

    static final long ASK_AGAIN_MS = TimeUnit.DAYS.toMillis(3);
    private static final String PREFS = "mise_a_jour";
    private static final String DECLINED_VERSION = "version_refusee";
    private static final String DECLINED_AT = "refusee_le";

    private final ComponentActivity activity;
    private final AppUpdateManager manager;
    private final SharedPreferences prefs;
    private final ActivityResultLauncher<IntentSenderRequest> launcher;
    private int offeredVersion;

    /** À créer dans onCreate : l'activité n'accepte plus de nouveau lanceur une fois démarrée */
    UpdatePrompt(ComponentActivity activity) {
        this.activity = activity;
        manager = AppUpdateManagerFactory.create(activity);
        prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        launcher =
            activity.registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), this::onAnswer);
    }

    /** Au lancement, une fois le jeu affiché : installe la mise à jour déjà téléchargée, sinon propose celle qui existe */
    void checkAtLaunch() {
        manager
            .getAppUpdateInfo()
            .addOnSuccessListener(this::onInfo)
            .addOnFailureListener(error -> {
                // Pas de Play Store, appli d'essai, pas de réseau : le jeu continue sans invite
            });
    }

    private void onInfo(AppUpdateInfo info) {
        // La réponse de Google Play peut arriver après la fermeture de l'appli
        if (activity.isFinishing() || activity.isDestroyed()) return;
        int status = info.installStatus();
        if (status == InstallStatus.DOWNLOADED) {
            // Téléchargée lors d'un lancement précédent : Google Play l'installe et relance l'appli
            manager.completeUpdate();
            return;
        }
        // Déjà acceptée, encore en route : pas de seconde invite
        if (inProgress(status)) return;
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return;
        AppUpdateOptions options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build();
        if (!info.isUpdateTypeAllowed(options)) return;
        int version = info.availableVersionCode();
        if (
            declinedRecently(
                prefs.getInt(DECLINED_VERSION, 0),
                prefs.getLong(DECLINED_AT, 0),
                version,
                System.currentTimeMillis()
            )
        ) return;
        offeredVersion = version;
        try {
            manager.startUpdateFlowForResult(info, launcher, options);
        } catch (IllegalStateException | ActivityNotFoundException error) {
            // La fenêtre de Google Play ne peut pas s'ouvrir : pas d'invite cette fois
        }
    }

    /** Mise à jour acceptée dont le téléchargement ou l'installation est en cours */
    static boolean inProgress(int installStatus) {
        return (
            installStatus == InstallStatus.PENDING ||
            installStatus == InstallStatus.DOWNLOADING ||
            installStatus == InstallStatus.INSTALLING
        );
    }

    private void onAnswer(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_CANCELED) return;
        prefs.edit().putInt(DECLINED_VERSION, offeredVersion).putLong(DECLINED_AT, System.currentTimeMillis()).apply();
    }

    /**
     * La version proposée vient-elle d'être refusée ? Une version plus récente se propose tout de
     * suite ; une horloge reculée ne bloque pas l'invite.
     */
    static boolean declinedRecently(int declinedVersion, long declinedAt, int availableVersion, long now) {
        if (declinedVersion != availableVersion) return false;
        long since = now - declinedAt;
        return since >= 0 && since < ASK_AGAIN_MS;
    }
}
