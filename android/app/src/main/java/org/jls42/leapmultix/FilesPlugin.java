package org.jls42.leapmultix;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Enregistre un fichier du jeu là où le joueur le choisit : la page de l'appli ne télécharge
 * rien, alors « Enregistrer les joueurs dans un fichier » appelle ce plugin, qui ouvre la
 * fenêtre « Enregistrer sous » d'Android (ACTION_CREATE_DOCUMENT) puis écrit le contenu dans le
 * fichier choisi. Aucune autorisation n'est demandée : Android ne donne accès qu'à ce fichier.
 * Répond { saved: true } une fois le fichier écrit, { saved: false } si la fenêtre est fermée
 * sans choix.
 *
 * Le contenu attend dans un fichier du cache privé de l'appli, pas dans l'appel : tant que la
 * fenêtre d'Android est ouverte, Capacitor recopie l'appel dans l'état de l'activité, dont
 * Android limite la taille. Ce fichier est supprimé dès que la fenêtre a répondu.
 */
@CapacitorPlugin(name = "LeapMultixFiles")
public class FilesPlugin extends Plugin {

    private static final String JSON_TYPE = "application/json";
    private static final String PENDING_FILE = "enregistrement-en-attente.json";

    @PluginMethod
    public void save(PluginCall call) {
        String name = call.getString("name");
        String content = call.getString("content");
        if (name == null || name.isEmpty() || content == null) {
            call.reject("name et content attendus");
            return;
        }
        File pending = pendingFile();
        try (OutputStream out = new FileOutputStream(pending)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            discard(pending);
            Logger.error("LeapMultixFiles", "Préparation du fichier impossible", error);
            call.reject("écriture impossible", error);
            return;
        }
        call.getData().remove("content");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(JSON_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(call, intent, "written");
        } catch (ActivityNotFoundException error) {
            // Aucune appli de l'appareil ne sait enregistrer un fichier
            discard(pending);
            call.reject("aucune appli pour enregistrer un fichier", error);
        }
    }

    /**
     * Android peut avoir recréé l'appli pendant que sa fenêtre était ouverte : l'appel est alors
     * perdu (call vaut null), mais le fichier choisi est écrit quand même.
     */
    @ActivityCallback
    private void written(PluginCall call, ActivityResult result) {
        File pending = pendingFile();
        try {
            Intent data = result.getData();
            Uri target = data == null ? null : data.getData();
            if (result.getResultCode() != Activity.RESULT_OK || target == null) {
                answer(call, false);
                return;
            }
            // « wt » : un fichier remplacé est vidé avant l'écriture
            try (
                InputStream in = new FileInputStream(pending);
                OutputStream out = getContext().getContentResolver().openOutputStream(target, "wt")
            ) {
                if (out == null) throw new IOException("fichier inaccessible");
                byte[] buffer = new byte[65536];
                for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                    out.write(buffer, 0, read);
                }
            }
            answer(call, true);
        } catch (IOException | SecurityException error) {
            Logger.error("LeapMultixFiles", "Écriture du fichier impossible", error);
            if (call != null) call.reject("écriture impossible", error);
        } finally {
            discard(pending);
        }
    }

    private File pendingFile() {
        return new File(getContext().getCacheDir(), PENDING_FILE);
    }

    private static void discard(File pending) {
        if (pending.exists() && !pending.delete()) {
            Logger.error("LeapMultixFiles", "Fichier en attente non supprimé", null);
        }
    }

    private static void answer(PluginCall call, boolean saved) {
        if (call == null) return;
        JSObject answer = new JSObject();
        answer.put("saved", saved);
        call.resolve(answer);
    }
}
