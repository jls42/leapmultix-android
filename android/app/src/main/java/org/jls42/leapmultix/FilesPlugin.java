package org.jls42.leapmultix;

import android.app.Activity;
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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Enregistre un fichier du jeu là où le joueur le choisit : la page de l'appli ne télécharge
 * rien, alors « Enregistrer les joueurs dans un fichier » appelle ce plugin, qui ouvre la
 * fenêtre « Enregistrer sous » d'Android (ACTION_CREATE_DOCUMENT) puis écrit le contenu dans le
 * fichier choisi. Aucune autorisation n'est demandée : Android ne donne accès qu'à ce fichier.
 * Répond { saved: true } une fois le fichier écrit, { saved: false } si la fenêtre est fermée
 * sans choix.
 */
@CapacitorPlugin(name = "LeapMultixFiles")
public class FilesPlugin extends Plugin {

    private static final String JSON_TYPE = "application/json";

    @PluginMethod
    public void save(PluginCall call) {
        String name = call.getString("name");
        String content = call.getString("content");
        if (name == null || name.isEmpty() || content == null) {
            call.reject("name et content attendus");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(JSON_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(call, intent, "written");
    }

    @ActivityCallback
    private void written(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        Uri target = data == null ? null : data.getData();
        JSObject answer = new JSObject();
        if (result.getResultCode() != Activity.RESULT_OK || target == null) {
            answer.put("saved", false);
            call.resolve(answer);
            return;
        }
        String content = call.getString("content", "");
        // « wt » : un fichier remplacé est vidé avant l'écriture
        try (OutputStream out = getContext().getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) throw new IOException("fichier inaccessible");
            out.write(content.getBytes(StandardCharsets.UTF_8));
            answer.put("saved", true);
            call.resolve(answer);
        } catch (IOException | SecurityException error) {
            Logger.error("LeapMultixFiles", "Écriture du fichier impossible", error);
            call.reject("écriture impossible", error);
        }
    }
}
