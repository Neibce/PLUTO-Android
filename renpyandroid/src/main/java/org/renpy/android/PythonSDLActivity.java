package org.renpy.android;

import org.libsdl.app.SDLActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.AuthenticationResult;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.PlayGamesSdk;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardVariantEntity;
import com.google.android.gms.tasks.Task;
import com.teamcodong.pluto.renpyandroid.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

//import org.renpy.iap.Store;

public class PythonSDLActivity extends SDLActivity {

    /**
     * This exists so python code can access this activity.
     */
    public static PythonSDLActivity mActivity = null;

    /**
     * The layout that contains the SDL view. VideoPlayer uses this to add
     * its own view on on top of the SDL view.
     */
    public FrameLayout mFrameLayout;


    /**
     * A layout that contains mLayout. This is a 3x3 grid, with the layout
     * in the center. The idea is that if someone wants to show an ad, they
     * can stick it in one of the other cells..
     */
    public LinearLayout mVbox;

    ResourceManager resourceManager;


    protected String[] getLibraries() {
        return new String[] {
            "png16",
            "SDL2",
            "SDL2_image",
            "SDL2_ttf",
            "SDL2_gfx",
            "SDL2_mixer",
            "python2.7",
            "pymodules",
            "main",
        };
    }


    // GUI code. /////////////////////////////////////////////////////////////


    public void addView(View view, int index) {
        mVbox.addView(view, index, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, (float) 0.0));
    }

    public void removeView(View view) {
        mVbox.removeView(view);
    }

    @Override
    public void setContentView(View view) {
        mFrameLayout = new FrameLayout(this);
        mFrameLayout.addView(view);

        mVbox = new LinearLayout(this);
        mVbox.setOrientation(LinearLayout.VERTICAL);
        mVbox.addView(mFrameLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, (float) 1.0));

        super.setContentView(mVbox);
    }


    private void setupMainWindowDisplayMode() {
        View decorView = setSystemUiVisibilityMode();
        decorView.setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                setSystemUiVisibilityMode(); // Needed to avoid exiting immersive_sticky when keyboard is displayed
            }
        });
    }

    private View setSystemUiVisibilityMode() {
        View decorView = getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            int options;
            options =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            decorView.setSystemUiVisibility(options);

        }

        return decorView;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            setupMainWindowDisplayMode();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setupMainWindowDisplayMode();
    }

    // Code to unpack python and get things running ///////////////////////////

    public void recursiveDelete(File f) {
        if (f.isDirectory()) {
            for (File r : f.listFiles()) {
                recursiveDelete(r);
            }
        }
        f.delete();
    }

    /**
     * This determines if unpacking one the zip files included in
     * the .apk is necessary. If it is, the zip file is unpacked.
     */
    public void unpackData(final String resource, File target) {

        /**
         * Delete main.pyo unconditionally. This fixes a problem where we have
         * a main.py newer than main.pyo, but start.c won't run it.
         */
        new File(target, "main.pyo").delete();

        boolean shouldUnpack = false;

        // The version of data in memory and on disk.
        String data_version = resourceManager.getString(resource + "_version");
        String disk_version = null;

        String filesDir = target.getAbsolutePath();
        String disk_version_fn = filesDir + "/" + resource + ".version";

        // If no version, no unpacking is necessary.
        if (data_version != null) {

            try {
                byte buf[] = new byte[64];
                InputStream is = new FileInputStream(disk_version_fn);
                int len = is.read(buf);
                disk_version = new String(buf, 0, len);
                is.close();
            } catch (Exception e) {
                disk_version = "";
            }

            if (! data_version.equals(disk_version)) {
                shouldUnpack = true;
            }
        }


        // If the disk data is out of date, extract it and write the
        // version file.
        if (shouldUnpack) {
            Log.v("python", "Extracting " + resource + " assets.");

            // Delete old libraries & renpy files.
            recursiveDelete(new File(target, "lib"));
            recursiveDelete(new File(target, "renpy"));

            target.mkdirs();

            AssetExtract ae = new AssetExtract(this);
            if (!ae.extractTar(resource + ".mp3", target.getAbsolutePath())) {
                toastError("Could not extract " + resource + " data.");
            }

            try {
                // Write .nomedia.
                new File(target, ".nomedia").createNewFile();

                // Write version file.
                FileOutputStream os = new FileOutputStream(disk_version_fn);
                os.write(data_version.getBytes());
                os.close();
            } catch (Exception e) {
                Log.w("python", e);
            }
        }

    }

    /**
     * Show an error using a toast. (Only makes sense from non-UI
     * threads.)
     */
    public void toastError(final String msg) {

        final Activity thisActivity = this;

        runOnUiThread(new Runnable () {
            public void run() {
                Toast.makeText(thisActivity, msg, Toast.LENGTH_LONG).show();
            }
        });

        // Wait to show the error.
        synchronized (this) {
            try {
                this.wait(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    public native void nativeSetEnv(String variable, String value);

    public void preparePython() {
        Log.v("python", "Starting preparePython.");

        mActivity = this;

        resourceManager = new ResourceManager(this);

        File oldExternalStorage = new File(Environment.getExternalStorageDirectory(), getPackageName());
        File externalStorage = getExternalFilesDir(null);
        File path;

        if (externalStorage == null) {
            externalStorage = oldExternalStorage;
        }

        if (resourceManager.getString("public_version") != null) {
            path = externalStorage;
        } else {
            path = getFilesDir();
        }

        unpackData("private", getFilesDir());
        // unpackData("public", externalStorage);

        nativeSetEnv("ANDROID_ARGUMENT", path.getAbsolutePath());
        nativeSetEnv("ANDROID_PRIVATE", getFilesDir().getAbsolutePath());
        nativeSetEnv("ANDROID_PUBLIC",  externalStorage.getAbsolutePath());
        nativeSetEnv("ANDROID_OLD_PUBLIC", oldExternalStorage.getAbsolutePath());

        // Figure out the APK path.
        String apkFilePath;
        ApplicationInfo appInfo;
        PackageManager packMgmr = getApplication().getPackageManager();

        try {
            appInfo = packMgmr.getApplicationInfo(getPackageName(), 0);
            apkFilePath = appInfo.sourceDir;
        } catch (NameNotFoundException e) {
            apkFilePath = "";
        }

        nativeSetEnv("ANDROID_APK", apkFilePath);

        String expansionFile = getIntent().getStringExtra("expansionFile");

        if (expansionFile != null) {
            nativeSetEnv("ANDROID_EXPANSION", expansionFile);
        }

        nativeSetEnv("PYTHONOPTIMIZE", "2");
        nativeSetEnv("PYTHONHOME", getFilesDir().getAbsolutePath());
        nativeSetEnv("PYTHONPATH", path.getAbsolutePath() + ":" + getFilesDir().getAbsolutePath() + "/lib");

        Log.v("python", "Finished preparePython.");

    };

    private static int RC_SIGN_IN = 3762;
    private GamesSignInClient mGamesSignInClient;
    private boolean mIsSignedIn = false;

    private int mViewIdFrameLayout;

    // Code to support devicePurchase. /////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Store.create(this);

        PlayGamesSdk.initialize(this);
        signInSilently();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Store.getStore().destroy();
    }



    private void signInSilently() {
        mGamesSignInClient = PlayGames.getGamesSignInClient(this);
        Task<AuthenticationResult> task = mGamesSignInClient.isAuthenticated();
        task.addOnCompleteListener((resultTask)->{
            if(!resultTask.getResult().isAuthenticated())
                mGamesSignInClient.signIn().addOnCompleteListener(this, (resultTask1)->mIsSignedIn = resultTask1.getResult().isAuthenticated());
        });
    }



    private static final int RC_ACHIEVEMENT_UI = 3261;

    private void showAchievements() {
        runOnUiThread(() -> {
            if (!mIsSignedIn) {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(PythonSDLActivity.this);
                alertDialog.setTitle("로그인");
                alertDialog.setMessage("업적을 보려면 Play 게임즈에 로그인이 필요합니다.");
                alertDialog.setPositiveButton("확인",(v, which)->{signInSilently();});
                alertDialog.setNegativeButton("취소", (v, which)->{});
                alertDialog.create().show();
            } else {
                PlayGames.getAchievementsClient(PythonSDLActivity.this)
                        .getAchievementsIntent()
                        .addOnSuccessListener(intent -> startActivityForResult(intent, RC_ACHIEVEMENT_UI));
            }
        });
    }

    private void addLeaderBoardScore() {
        signInSilently();
        PlayGames.getLeaderboardsClient(this)
                .loadCurrentPlayerLeaderboardScore(getString(R.string.leaderboard), LeaderboardVariantEntity.TIME_SPAN_ALL_TIME, LeaderboardVariantEntity.COLLECTION_PUBLIC)
                .addOnCompleteListener(this,
                        task -> {
                            if(task.isSuccessful()){
                                AnnotatedData<LeaderboardScore> lbs = task.getResult();

                                long score;
                                try {
                                    score = lbs.get().getRawScore() + 1L;
                                }catch (NullPointerException e){
                                    score = 1L;
                                }

                                PlayGames.getLeaderboardsClient(PythonSDLActivity.this)
                                        .submitScore(getString(R.string.leaderboard), score);
                            }
                        });

    }

    // Code to support public APIs. ////////////////////////////////////////////

    public void openUrl(String url) {
        Log.i("python", "Opening URL: " + url);

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    public void vibrate(double s) {
        if(s >= 1000.0d){
            //GoogleSignInAccount signedInAccount = GoogleSignIn.getLastSignedInAccount(this);

            //if(signedInAccount != null) {
                int achievementID = 0;
                int code = (int) Math.round(s - 1000.0d);
                switch (code) {
                    case 1:
                        achievementID = R.string.achievement_playbutton;
                        break;
                    case 2:
                        achievementID = R.string.achievement_chapter_1;
                        break;
                    case 3:
                        achievementID = R.string.achievement_chapter_2;
                        break;
                    case 4:
                        achievementID = R.string.achievement_chapter_3;
                        addLeaderBoardScore();
                        break;
                    case 5:
                        achievementID = R.string.achievement_bad_1;
                        addLeaderBoardScore();
                        break;
                    case 6:
                        achievementID = R.string.achievement_bad_2;
                        addLeaderBoardScore();
                        break;
                    case 7:
                        achievementID = R.string.achievement_bad_3;
                        addLeaderBoardScore();
                        break;
                    case 8:
                        achievementID = R.string.achievement_bad_clear;
                        break;
                    case 9:
                        achievementID = R.string.achievement_ending_credit;
                        break;
                    case 101:
                        showAchievements();
                        break;
                    case 102:
                        showLeaderboard();
                        break;
                }

                if(code != 101 && code != 102) {
                    PlayGames.getAchievementsClient(PythonSDLActivity.this)
                            .unlock(getString(achievementID));
                }
           // }

        }else {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate((int) (1000 * s));
            }
        }
    }

    private static final int RC_LEADERBOARD_UI = 5934;

    private void showLeaderboard() {
        runOnUiThread(() -> {
            if(!mIsSignedIn) {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(PythonSDLActivity.this);
                alertDialog.setTitle("로그인");
                alertDialog.setMessage("리더보드를 보려면 Play 게임즈에 로그인이 필요합니다.");
                alertDialog.setPositiveButton("확인",(v, which)->{signInSilently();});
                alertDialog.setNegativeButton("취소", (v, which)->{});
                alertDialog.create().show();
            }else{
                PlayGames.getLeaderboardsClient(PythonSDLActivity.this)
                        .getLeaderboardIntent(getString(R.string.leaderboard))
                        .addOnSuccessListener(intent -> startActivityForResult(intent, RC_LEADERBOARD_UI));
            }
        });
    }

    public int getDPI() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.densityDpi;
    }

    public PowerManager.WakeLock wakeLock = null;

    public void setWakeLock(boolean active) {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Pluto: Screen On");
            wakeLock.setReferenceCounted(false);
        }

        if (active) {
            wakeLock.acquire();
        } else {
            wakeLock.release();
        }
    }

}
