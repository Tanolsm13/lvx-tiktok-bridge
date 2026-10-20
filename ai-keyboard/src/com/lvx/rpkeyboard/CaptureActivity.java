package com.lvx.rpkeyboard;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/**
 * Activite transparente : affiche la demande d'autorisation systeme de capture,
 * puis transmet le jeton au service. Lancee seulement sur appui du bouton du clavier.
 */
public class CaptureActivity extends Activity {

    private static final int REQ = 7001;

    /** Consigne ponctuelle et fil en cours, poses par le clavier avant le lancement. */
    public static String pendingOneShot = "";
    public static String pendingThread = "default";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        MediaProjectionManager mgr =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mgr == null) {
            Bus.error("Capture d'ecran indisponible sur cet appareil");
            finish();
            return;
        }
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ);
    }

    @Override
    protected void onActivityResult(int req, int resultCode, Intent data) {
        super.onActivityResult(req, resultCode, data);
        if (req != REQ) {
            finish();
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            Bus.error("Autorisation de capture refusee");
            finish();
            return;
        }

        Intent svc = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_INIT)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_RESULT_DATA, data)
                .putExtra(CaptureService.EXTRA_ONE_SHOT, pendingOneShot)
                .putExtra(CaptureService.EXTRA_THREAD, pendingThread);
        startForegroundService(svc);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
