package org.jls42.leapmultix;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lit une phrase du jeu avec la synthèse vocale du téléphone. La page de l'appli n'a pas la
 * synthèse vocale d'un navigateur : dans une appli construite sans voix enregistrée, le jeu
 * confie ses phrases à ce plugin. Le site ne l'appelle pas quand une voix enregistrée est
 * embarquée.
 *
 * speak({ text, lang, rate, pitch, volume }) répond quand la phrase est finie ({ stopped: false })
 * ou coupée ({ stopped: true }), et refuse quand elle ne peut pas se dire : synthèse absente,
 * langue absente du téléphone. Une phrase à la fois : la suivante coupe celle qui se dit.
 * stop() coupe la phrase en cours. Chaque appel reçoit une seule réponse.
 */
@CapacitorPlugin(name = "LeapMultixSpeech")
public class SpeechPlugin extends Plugin {

    private static final String TAG = "LeapMultixSpeech";
    private static final String DEFAULT_LANG = "fr-FR";

    private final Object lock = new Object();
    // Phrases confiées à la synthèse, par identifiant d'énoncé : retirée d'ici, une phrase a
    // reçu sa réponse
    private final Map<String, PluginCall> spoken = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private TextToSpeech tts;
    private boolean ready;
    private boolean failed;
    // Phrase demandée pendant que la synthèse se prépare (une seule : la dernière)
    private PluginCall waiting;

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text");
        if (text == null || text.trim().isEmpty()) {
            call.reject("text attendu");
            return;
        }
        if (text.length() > TextToSpeech.getMaxSpeechInputLength()) {
            call.reject("phrase trop longue");
            return;
        }
        synchronized (lock) {
            if (failed) {
                call.reject("synthèse vocale indisponible");
                return;
            }
            if (!ready) {
                answerStopped(waiting);
                waiting = call;
                // La synthèse se prépare en arrière-plan : onInit dira quand elle est prête
                if (tts == null) tts = new TextToSpeech(getContext().getApplicationContext(), this::onInit);
                return;
            }
            say(call);
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        synchronized (lock) {
            answerStopped(waiting);
            waiting = null;
            if (ready) tts.stop();
            answerAllStopped();
        }
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        synchronized (lock) {
            answerStopped(waiting);
            waiting = null;
            answerAllStopped();
            if (tts != null) {
                tts.stop();
                tts.shutdown();
                tts = null;
            }
            ready = false;
        }
        super.handleOnDestroy();
    }

    /** Fin de la préparation de la synthèse (parfois appelée avant la fin du constructeur) */
    private void onInit(int status) {
        synchronized (lock) {
            PluginCall first = waiting;
            waiting = null;
            if (status != TextToSpeech.SUCCESS) {
                failed = true;
                Logger.error(TAG, "Synthèse vocale indisponible (" + status + ")", null);
                if (first != null) first.reject("synthèse vocale indisponible");
                return;
            }
            // Une réussite n'arrive qu'après le constructeur : tts est en place
            tts.setOnUtteranceProgressListener(new Listener());
            ready = true;
            if (first != null) say(first);
        }
    }

    /** Confie la phrase à la synthèse ; la phrase en cours, s'il y en a une, est coupée */
    private void say(PluginCall call) {
        String lang = call.getString("lang", DEFAULT_LANG);
        int support = tts.setLanguage(Locale.forLanguageTag(lang));
        if (support == TextToSpeech.LANG_MISSING_DATA || support == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Jamais une phrase lue avec la voix d'une autre langue
            call.reject("langue absente de la synthèse vocale : " + lang);
            return;
        }
        tts.setSpeechRate(call.getFloat("rate", 1f));
        tts.setPitch(call.getFloat("pitch", 1f));
        Bundle params = new Bundle();
        float volume = Math.max(0f, Math.min(1f, call.getFloat("volume", 1f)));
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
        String id = "phrase-" + nextId.incrementAndGet();
        spoken.put(id, call);
        if (tts.speak(call.getString("text"), TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) {
            spoken.remove(id);
            call.reject("phrase refusée par la synthèse vocale");
        }
    }

    private void answerAllStopped() {
        List<String> ids = new ArrayList<>(spoken.keySet());
        for (String id : ids) answerStopped(spoken.remove(id));
    }

    private static void answerStopped(PluginCall call) {
        if (call == null) return;
        JSObject answer = new JSObject();
        answer.put("stopped", true);
        call.resolve(answer);
    }

    /** Fin de chaque phrase, signalée par la synthèse sur ses propres fils d'exécution */
    private final class Listener extends UtteranceProgressListener {

        @Override
        public void onStart(String id) {
            // Rien : le jeu borne lui-même la durée d'une phrase
        }

        @Override
        public void onDone(String id) {
            PluginCall call = spoken.remove(id);
            if (call == null) return;
            JSObject answer = new JSObject();
            answer.put("stopped", false);
            call.resolve(answer);
        }

        @Override
        public void onStop(String id, boolean interrupted) {
            answerStopped(spoken.remove(id));
        }

        @Override
        public void onError(String id, int errorCode) {
            PluginCall call = spoken.remove(id);
            if (call != null) call.reject("phrase non dite par la synthèse vocale (" + errorCode + ")");
        }

        @Override
        @Deprecated
        public void onError(String id) {
            PluginCall call = spoken.remove(id);
            if (call != null) call.reject("phrase non dite par la synthèse vocale");
        }
    }
}
