package fr.manu.carnetrando

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.AlertDialog
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.Calendar

/**
 * Carnet de rando (Android) : une fenêtre qui ouvre le carnet en ligne, comme dans Chrome,
 * mais installée pour de bon sur le téléphone. Elle apparaît dans « Partager » :
 * les photos et vidéos reçues sont remises au carnet, qui les range par date.
 * Les albums sont gardés dans l'appli (ils ne sont pas partagés avec Chrome) ; sauvegarde / restauration dans le carnet.
 */
class MainActivity : Activity() {

    companion object {
        const val BASE = "https://manuel-vigie.github.io/appli-manu/carnet-randonnee/"
        const val PAGE = BASE + "carnet-randonnee.html"
        const val REQ_FICHIER = 41
    }

    private lateinit var web: WebView
    private var chooserCb: ValueCallback<Array<Uri>>? = null

    // Fichiers reçus par « Partager », copiés dans l'appli en attendant que le carnet les prenne.
    private class Recu(val nom: String, val type: String, val modif: Long, val fichier: File)
    private val recus = ArrayList<Recu>()
    // Compte rendu de la dernière réception (affiché par le carnet si rien n'a pu être lu).
    @Volatile private var diag: String = ""
    private var pleinEcran = false

    /** Plein écran : cache la barre du haut et la barre de navigation du bas (un glissement les fait réapparaître un instant). */
    @Suppress("DEPRECATION")
    private fun appliquerPleinEcran(on: Boolean) {
        pleinEcran = on
        if (Build.VERSION.SDK_INT >= 30) {
            val c = window.insetsController ?: return
            if (on) {
                c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                c.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                c.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            window.decorView.systemUiVisibility = if (on)
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            else View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    private val sorties = HashMap<Int, Pair<OutputStream, Pair<String, Uri?>>>()
    private var prochainId = 1

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val racine = FrameLayout(this)
        racine.setBackgroundColor(Color.parseColor("#0D120D"))
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0D120D"))
        racine.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(racine)

        // Android 15 dessine sous les barres : on laisse la place au haut et au bas de l'écran (et au clavier).
        racine.setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                WindowInsets.CONSUMED
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
                @Suppress("DEPRECATION")
                ins.consumeSystemWindowInsets()
            }
        }

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        @Suppress("DEPRECATION")
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        web.addJavascriptInterface(Pont(), "CarnetAndroid")
        // Appui long sur une photo (mode « Retoucher ») : Android ne doit pas ouvrir son menu de sélection.
        web.setOnLongClickListener { true }
        web.isLongClickable = false
        web.isHapticFeedbackEnabled = false

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? =
                servir(request.url.toString())

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == "manuel-vigie.github.io") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(SCRIPT_TELECHARGEMENT, null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            // Fenêtres du carnet (« Remplacer la bibliothèque ? », « Supprimer ? », légendes…) : sans ceci, Android les refuse en silence.
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.confirm() }.show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Annuler") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaut: String?, result: JsPromptResult): Boolean {
                val champ = EditText(this@MainActivity)
                champ.setText(defaut ?: "")
                AlertDialog.Builder(this@MainActivity).setMessage(message).setView(champ)
                    .setPositiveButton("OK") { _, _ -> result.confirm(champ.text.toString()) }
                    .setNegativeButton("Annuler") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onShowFileChooser(w: WebView?, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                chooserCb?.onReceiveValue(null)
                chooserCb = cb
                return try {
                    startActivityForResult(p.createIntent(), REQ_FICHIER)
                    true
                } catch (e: Exception) {
                    chooserCb = null
                    cb.onReceiveValue(null)
                    false
                }
            }
        }
        ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                servir(request.url.toString())
        })

        if (estPartage(intent)) recevoir(intent) else web.loadUrl(PAGE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (estPartage(intent)) recevoir(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FICHIER) {
            val r = if (resultCode == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            chooserCb?.onReceiveValue(r)
            chooserCb = null
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    // Quand on quitte l'appli, le son s'arrête (la musique du carnet ne continue pas en fond) ; il repart au retour.
    override fun onPause() {
        super.onPause()
        web.evaluateJavascript("if(window.__sonPause)window.__sonPause();document.querySelectorAll('video').forEach(function(e){try{e.pause()}catch(x){}})", null)
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        if (pleinEcran) appliquerPleinEcran(true)
        web.evaluateJavascript("if(window.__sonReprise)window.__sonReprise()", null)
    }

    override fun onDestroy() {
        // Fermeture complète : la fenêtre web est détruite, donc plus aucun son.
        web.loadUrl("about:blank")
        web.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Le carnet ferme d'abord ce qui est ouvert (plein écran, réglages, album…) ; sinon Android décide.
        web.evaluateJavascript("(function(){try{return !!(window.__retour&&window.__retour())}catch(e){return false}})()") { r ->
            if (r != "true") {
                if (web.canGoBack()) web.goBack() else {
                    @Suppress("DEPRECATION")
                    super.onBackPressed()
                }
            }
        }
    }

    private fun estPartage(i: Intent?): Boolean =
        i != null && (i.action == Intent.ACTION_SEND || i.action == Intent.ACTION_SEND_MULTIPLE)

    /** Copie les fichiers partagés dans l'appli, puis ouvre le carnet qui propose de les ranger. */
    @Suppress("DEPRECATION")
    private fun recevoir(i: Intent) {
        val uris = ArrayList<Uri>()
        try {
            if (i.action == Intent.ACTION_SEND_MULTIPLE) {
                i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            } else {
                (i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uris.add(it) }
            }
            val cd = i.clipData
            if (cd != null) for (k in 0 until cd.itemCount) {
                val u = cd.getItemAt(k).uri
                if (u != null && u !in uris) uris.add(u)
            }
        } catch (_: Exception) {}
        if (uris.isEmpty()) { web.loadUrl(PAGE); return }
        // Le carnet s'affiche tout de suite (plus d'écran noir pendant la copie) ; il se recharge quand les fichiers sont prêts.
        if (web.url == null) web.loadUrl(PAGE)
        Toast.makeText(this, "Réception de " + uris.size + " fichier(s)… patientez.", Toast.LENGTH_LONG).show()
        Thread {
            val dossier = File(filesDir, "recu")
            dossier.deleteRecursively()
            dossier.mkdirs()
            val liste = ArrayList<Recu>()
            val erreurs = ArrayList<String>()
            for ((k, u) in uris.withIndex()) {
                try {
                    var nom = "souvenir_$k"
                    contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0)?.let { if (it.isNotBlank()) nom = it }
                    }
                    val type = contentResolver.getType(u) ?: ""
                    val dest = File(dossier, "$k.bin")
                    val flux = contentResolver.openInputStream(u)
                    if (flux == null) { erreurs.add("fichier illisible (aucun flux)"); continue }
                    flux.use { ins -> dest.outputStream().use { out -> ins.copyTo(out, 262144) } }
                    liste.add(Recu(nom, type, dateDuNom(nom), dest))
                    if (uris.size > 20 && (k + 1) % 25 == 0) {
                        val fait = k + 1
                        runOnUiThread { Toast.makeText(this, "Réception… $fait sur ${uris.size}", Toast.LENGTH_SHORT).show() }
                    }
                } catch (e: Throwable) {
                    erreurs.add(e.javaClass.simpleName + ": " + (e.message ?: "").take(80))
                }
            }
            diag = "${uris.size} annoncé(s), ${liste.size} copié(s)" +
                (if (erreurs.isNotEmpty()) " ; erreurs : " + erreurs.distinct().take(2).joinToString(" / ") else "")
            runOnUiThread {
                synchronized(recus) { recus.clear(); recus.addAll(liste) }
                if (liste.isEmpty()) {
                    Toast.makeText(this, "Impossible de lire ces fichiers. " + diag, Toast.LENGTH_LONG).show()
                    if (web.url == null) web.loadUrl(PAGE)
                } else {
                    web.loadUrl(PAGE + "?partage=" + liste.size)
                }
            }
        }.start()
    }

    /** Date et heure écrites dans le nom (IMG_20260512_101530.jpg…), sinon 0 : le carnet lit alors la date dans la photo. */
    private fun dateDuNom(nom: String): Long {
        val m = Regex("(20\\d\\d|19\\d\\d)[-_.]?(\\d\\d)[-_.]?(\\d\\d)[-_. T]?(\\d\\d)[-_.:]?(\\d\\d)[-_.:]?(\\d\\d)").find(nom) ?: return 0L
        return try {
            val g = m.groupValues.drop(1).map { it.toInt() }
            val c = Calendar.getInstance()
            c.clear()
            c.set(g[0], g[1] - 1, g[2], g[3], g[4], g[5])
            c.timeInMillis
        } catch (_: Exception) { 0L }
    }

    /** Sert au carnet les fichiers reçus : adresse « …/__recu/<numéro>/<nom> ». */
    private fun servir(url: String): WebResourceResponse? {
        val p = url.indexOf("/__recu/")
        if (p < 0) return null
        val n = url.substring(p + 8).substringBefore('/').toIntOrNull() ?: return null
        val r = synchronized(recus) { recus.getOrNull(n) } ?: return null
        return try {
            val type = if (r.type.isNotEmpty()) r.type else "application/octet-stream"
            WebResourceResponse(
                type, null, 200, "OK",
                mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"),
                FileInputStream(r.fichier)
            )
        } catch (_: Exception) { null }
    }

    /** Ponts appelés par le carnet (JavaScript). */
    inner class Pont {
        @JavascriptInterface
        fun liste(): String {
            val a = JSONArray()
            synchronized(recus) {
                for (r in recus) a.put(JSONObject().put("nom", r.nom).put("type", r.type).put("modif", r.modif))
            }
            return a.toString()
        }

        /** Bouton plein écran du carnet (⛶) : cache ou rend les barres d'Android. */
        @JavascriptInterface
        fun plein(on: Boolean) {
            runOnUiThread { appliquerPleinEcran(on) }
        }

        /** Compte rendu de la dernière réception de photos (pour comprendre un échec). */
        @JavascriptInterface
        fun diag(): String = diag

        /** Bouton « Quitter l'application » du carnet : ferme tout, la musique s'arrête. */
        @JavascriptInterface
        fun quitter() {
            runOnUiThread { finishAndRemoveTask() }
        }

        @JavascriptInterface
        fun vider() {
            synchronized(recus) { recus.clear() }
            File(filesDir, "recu").deleteRecursively()
        }

        /** Début d'un enregistrement dans « Téléchargements » (sauvegardes, albums). */
        @JavascriptInterface
        fun debut(nomBrut: String): Int {
            val nom = nomBrut.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "carnet" }
            return try {
                val id = synchronized(sorties) { prochainId++ }
                if (Build.VERSION.SDK_INT >= 29) {
                    val v = ContentValues()
                    v.put(MediaStore.Downloads.DISPLAY_NAME, nom)
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    v.put(MediaStore.Downloads.IS_PENDING, 1)
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
                    synchronized(sorties) { sorties[id] = Pair(contentResolver.openOutputStream(uri)!!, Pair(nom, uri)) }
                } else {
                    val d = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                    d.mkdirs()
                    synchronized(sorties) { sorties[id] = Pair(File(d, nom).outputStream(), Pair(nom, null)) }
                }
                id
            } catch (e: Exception) { -1 }
        }

        @JavascriptInterface
        fun morceau(id: Int, base64: String) {
            val o = synchronized(sorties) { sorties[id] } ?: return
            try { o.first.write(Base64.decode(base64, Base64.DEFAULT)) } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun fin(id: Int) {
            val o = synchronized(sorties) { sorties.remove(id) } ?: return
            try { o.first.close() } catch (_: Exception) {}
            val uri = o.second.second
            if (uri != null && Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues()
                v.put(MediaStore.Downloads.IS_PENDING, 0)
                try { contentResolver.update(uri, v, null, null) } catch (_: Exception) {}
            }
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Enregistré dans Téléchargements : " + o.second.first, Toast.LENGTH_LONG).show()
            }
        }

        @JavascriptInterface
        fun erreur() {
            runOnUiThread { Toast.makeText(this@MainActivity, "Impossible d'enregistrer ce fichier.", Toast.LENGTH_LONG).show() }
        }
    }

    private val SCRIPT_TELECHARGEMENT = """
(function(){
  if (window.__carnetHook) return; window.__carnetHook = true;
  function envoyer(url, nom){
    fetch(url).then(function(r){ return r.blob(); }).then(function(b){
      var id = CarnetAndroid.debut(nom);
      if (id < 0) { CarnetAndroid.erreur(); return; }
      var T = 3*1024*1024, pos = 0;
      (function suite(){
        if (pos >= b.size) { CarnetAndroid.fin(id); return; }
        var fr = new FileReader();
        fr.onload = function(){ var s = fr.result; CarnetAndroid.morceau(id, s.substring(s.indexOf(',')+1)); pos += T; suite(); };
        fr.onerror = function(){ CarnetAndroid.erreur(); };
        fr.readAsDataURL(b.slice(pos, pos+T));
      })();
    }).catch(function(){ CarnetAndroid.erreur(); });
  }
  document.addEventListener('click', function(e){
    var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (!a || !/^blob:/.test(a.href)) return;
    e.preventDefault(); e.stopPropagation();
    envoyer(a.href, a.download || 'carnet');
  }, true);
})();
"""
}
