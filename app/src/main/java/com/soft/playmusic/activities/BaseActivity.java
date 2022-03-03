/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (C) 2014 The CyanogenMod Project
 * Copyright (C) 2015 Naman Dwivedi
 *
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.soft.playmusic.activities;

import static com.soft.playmusic.MusicPlayer.mService;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.afollestad.appthemeengine.ATE;
import com.afollestad.appthemeengine.ATEActivity;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.soft.playmusic.ITimberService;
import com.soft.playmusic.MusicPlayer;
import com.soft.playmusic.MusicService;
import com.soft.playmusic.R;
import com.soft.playmusic.cast.SimpleSessionManagerListener;
import com.soft.playmusic.cast.WebServer;
import com.soft.playmusic.listeners.MusicStateListener;
import com.soft.playmusic.slidinguppanel.SlidingUpPanelLayout;
import com.soft.playmusic.subfragments.QuickControlsFragment;
import com.soft.playmusic.utils.Helpers;
import com.soft.playmusic.utils.NavigationUtils;
import com.soft.playmusic.utils.TimberUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseActivity extends ATEActivity implements
        ServiceConnection, MusicStateListener {
    private FirebaseAuth mAuth;
    private final ArrayList<MusicStateListener> mMusicStateListener = new ArrayList<>();
    private MusicPlayer.ServiceToken mToken;
    private PlaybackStatus mPlaybackStatus;

    private CastSession mCastSession;
    private SessionManager mSessionManager;
    private final SessionManagerListener mSessionManagerListener =
            new SessionManagerListenerImpl();
    private WebServer castServer;

    public boolean playServicesAvailable = false;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            new ActivityResultCallback<FirebaseAuthUIAuthenticationResult>() {
                @Override
                public void onActivityResult(FirebaseAuthUIAuthenticationResult result) {
                    onSignInResult(result);
                }
            }
    );
    private String urlPerfil;

    public void createSignInIntent() {

        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build(),
                new AuthUI.IdpConfig.PhoneBuilder().build(),
                new AuthUI.IdpConfig.GoogleBuilder().build(),
                new AuthUI.IdpConfig.TwitterBuilder().build());

        // Create and launch sign-in intent
        Intent signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setLogo(R.drawable.logo)
                .setAvailableProviders(providers)
                .build();
        signInLauncher.launch(signInIntent);
        // [END auth_fui_create_intent]
    }

    private void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        IdpResponse response = result.getIdpResponse();
        if (result.getResultCode() == RESULT_OK) {
            // Successfully signed in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            // ...
        }
    }
    public void signOut() {
        // [START auth_fui_signout]
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        // ...
                    }
                });
        // [END auth_fui_signout]
    }
    public void checkCurrentUser() {
        // [START check_current_user]
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // User is signed in
            urlPerfil = user.getPhotoUrl().toString();
        } else {
            // No user is signed in
            createSignInIntent();
        }
        // [END check_current_user]
    }

    public String getUrlPerfil() {
        return urlPerfil;
    }

    public void getUserProfile() {
        // [START get_user_profile]
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Name, email address, and profile photo Url
            String name = user.getDisplayName();
            String email = user.getEmail();
            Uri photoUrl = user.getPhotoUrl();

            // Check if user's email is verified
            boolean emailVerified = user.isEmailVerified();

            // The user's ID, unique to the Firebase project. Do NOT use this value to
            // authenticate with your backend server, if you have one. Use
            // FirebaseUser.getIdToken() instead.
            String uid = user.getUid();
        }
        // [END get_user_profile]
    }



    private class SessionManagerListenerImpl extends SimpleSessionManagerListener {
        @Override
        public void onSessionStarting(Session session) {
            super.onSessionStarting(session);
            startCastServer();
        }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
            invalidateOptionsMenu();
            mCastSession = mSessionManager.getCurrentCastSession();
            showCastMiniController();
        }
        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) {
            invalidateOptionsMenu();
            mCastSession = mSessionManager.getCurrentCastSession();
        }
        @Override
        public void onSessionEnded(Session session, int error) {
            mCastSession = null;
            hideCastMiniController();
            stopCastServer();
        }

        @Override
        public void onSessionResuming(Session session, String s) {
            super.onSessionResuming(session, s);
            startCastServer();
        }

        @Override
        public void onSessionSuspended(Session session, int i) {
            super.onSessionSuspended(session, i);
            stopCastServer();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        mToken = MusicPlayer.bindToService(this, this);

        mPlaybackStatus = new PlaybackStatus(this);
        //make volume keys change multimedia volume even if music is not playing now
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        try {
            playServicesAvailable = GoogleApiAvailability
                    .getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;
        } catch (Exception ignored) {

        }

        if (playServicesAvailable)
            initCast();
    }

    @Override
    protected void onStart() {
        super.onStart();

        final IntentFilter filter = new IntentFilter();
        // Play and pause changes
        filter.addAction(MusicService.PLAYSTATE_CHANGED);
        // Track changes
        filter.addAction(MusicService.META_CHANGED);
        // Update a list, probably the playlist fragment's
        filter.addAction(MusicService.REFRESH);
        // If a playlist has changed, notify us
        filter.addAction(MusicService.PLAYLIST_CHANGED);
        // If there is an error playing a track
        filter.addAction(MusicService.TRACK_ERROR);

        registerReceiver(mPlaybackStatus, filter);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {

        if (playServicesAvailable) {
            mCastSession = mSessionManager.getCurrentCastSession();
            mSessionManager.addSessionManagerListener(mSessionManagerListener);
        }
        //For Android 8.0+: service may get destroyed if in background too long
        if(mService == null){
            mToken = MusicPlayer.bindToService(this, this);
        }
        onMetaChanged();
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playServicesAvailable) {
            mSessionManager.removeSessionManagerListener(mSessionManagerListener);
            mCastSession = null;
        }
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        mService = ITimberService.Stub.asInterface(service);
        onMetaChanged();
    }


    private void initCast() {
        CastContext castContext = CastContext.getSharedInstance(this);
        mSessionManager = castContext.getSessionManager();
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        mService = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mToken != null) {
            MusicPlayer.unbindFromService(mToken);
            mToken = null;
        }

        try {
            unregisterReceiver(mPlaybackStatus);
        } catch (final Throwable e) {
        }
        mMusicStateListener.clear();
    }

    @Override
    public void onMetaChanged() {
        // Let the listener know to the meta chnaged
        for (final MusicStateListener listener : mMusicStateListener) {
            if (listener != null) {
                listener.onMetaChanged();
            }
        }
    }

    @Override
    public void restartLoader() {
        // Let the listener know to update a list
        for (final MusicStateListener listener : mMusicStateListener) {
            if (listener != null) {
                listener.restartLoader();
            }
        }
    }

    @Override
    public void onPlaylistChanged() {
        // Let the listener know to update a list
        for (final MusicStateListener listener : mMusicStateListener) {
            if (listener != null) {
                listener.onPlaylistChanged();
            }
        }
    }

    public void setMusicStateListenerListener(final MusicStateListener status) {
        if (status == this) {
            throw new UnsupportedOperationException("Override the method, don't add a listener");
        }

        if (status != null) {
            mMusicStateListener.add(status);
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        getMenuInflater().inflate(R.menu.menu_cast, menu);

        if (playServicesAvailable) {
            CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                    menu,
                    R.id.media_route_menu_item);
        }

        if (!TimberUtils.hasEffectsPanel(BaseActivity.this)) {
            menu.removeItem(R.id.action_equalizer);
        }
        ATE.applyMenu(this, getATEKey(), menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
            case R.id.action_settings:
                NavigationUtils.navigateToSettings(this);
                return true;
            case R.id.action_shuffle:
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        MusicPlayer.shuffleAll(BaseActivity.this);
                    }
                }, 80);

                return true;
            case R.id.action_search:
                NavigationUtils.navigateToSearch(this);
                return true;
            case R.id.action_equalizer:
                NavigationUtils.navigateToEqualizer(this);
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public String getATEKey() {
        return Helpers.getATEKey(this);
    }

    public void setPanelSlideListeners(SlidingUpPanelLayout panelLayout) {
        panelLayout.setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {

            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                View nowPlayingCard = QuickControlsFragment.topContainer;
                if (nowPlayingCard != null)
                    nowPlayingCard.setAlpha(1 - slideOffset);
            }

            @Override
            public void onPanelCollapsed(View panel) {
                View nowPlayingCard = QuickControlsFragment.topContainer;
                if (nowPlayingCard != null)
                    nowPlayingCard.setAlpha(1);
            }

            @Override
            public void onPanelExpanded(View panel) {
                View nowPlayingCard = QuickControlsFragment.topContainer;
                if (nowPlayingCard != null)
                    nowPlayingCard.setAlpha(0);
            }

            @Override
            public void onPanelAnchored(View panel) {

            }

            @Override
            public void onPanelHidden(View panel) {

            }
        });
    }

    private final static class PlaybackStatus extends BroadcastReceiver {

        private final WeakReference<BaseActivity> mReference;


        public PlaybackStatus(final BaseActivity activity) {
            mReference = new WeakReference<BaseActivity>(activity);
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            BaseActivity baseActivity = mReference.get();
            if (baseActivity != null) {
                if (action.equals(MusicService.META_CHANGED)) {
                    baseActivity.onMetaChanged();
                } else if (action.equals(MusicService.PLAYSTATE_CHANGED)) {
//                    baseActivity.mPlayPauseProgressButton.getPlayPauseButton().updateState();
                } else if (action.equals(MusicService.REFRESH)) {
                    baseActivity.restartLoader();
                } else if (action.equals(MusicService.PLAYLIST_CHANGED)) {
                    baseActivity.onPlaylistChanged();
                } else if (action.equals(MusicService.TRACK_ERROR)) {
                   final String errorMsg = context.getString(R.string.error_playing_track,
                            intent.getStringExtra(MusicService.TrackErrorExtra.TRACK_NAME));
                    Toast.makeText(baseActivity, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public class initQuickControls extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            QuickControlsFragment fragment1 = new QuickControlsFragment();
            FragmentManager fragmentManager1 = getSupportFragmentManager();
            fragmentManager1.beginTransaction()
                    .replace(R.id.quickcontrols_container, fragment1).commitAllowingStateLoss();
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
        }

        @Override
        protected void onPreExecute() {
        }
    }

    public void showCastMiniController() {
        //implement by overriding in activities
    }

    public void hideCastMiniController() {
        //implement by overriding in activities
    }

    public CastSession getCastSession() {
        return mCastSession;
    }

    private void startCastServer() {
        castServer = new WebServer(this);
        try {
            castServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopCastServer() {
        if (castServer != null) {
            castServer.stop();
        }
    }
}
