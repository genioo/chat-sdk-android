/*
 * Created by Itzik Braun on 12/3/2015.
 * Copyright (c) 2015 deluge. All rights reserved.
 *
 * Last Modification at: 3/12/15 4:27 PM
 */

package co.chatsdk.ui.login;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import co.chatsdk.core.InterfaceManager;
import co.chatsdk.core.NM;

import co.chatsdk.core.types.AccountDetails;
import co.chatsdk.core.utils.AppContext;
import co.chatsdk.ui.BaseInterfaceAdapter;
import co.chatsdk.ui.R;
import co.chatsdk.ui.activities.BaseActivity;
import io.reactivex.CompletableObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

import org.apache.commons.lang3.StringUtils;

import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import timber.log.Timber;

import static android.support.v4.content.PermissionChecker.PERMISSION_DENIED;

/**
 * Created by itzik on 6/8/2014.
 */
public class LoginActivity extends BaseActivity implements View.OnClickListener {

    private static int FILE_PERMISSION_REQUEST = 100;

    private boolean exitOnBackPressed = false;

    protected EditText etEmail, etPass;

    /** Passed to the activity in the intent extras, Indicates that the activity was called after the user press the logout button,
     * That means the activity wont try to authenticate in inResume. */

    private Button btnLogin, btnReg, btnAnon, btnTwitter, btnGoogle, btnFacebook;
    private ImageView appIconImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chat_sdk_activty_login);

        setExitOnBackPressed(true);

        View view = findViewById(R.id.chat_sdk_root_view);

        setupTouchUIToDismissKeyboard(view);

        initViews();

    }

    protected void initViews () {

        btnLogin = (Button) findViewById(R.id.chat_sdk_btn_login);
        btnAnon = (Button) findViewById(R.id.chat_sdk_btn_anon_login);
        btnTwitter = (Button) findViewById(R.id.chat_sdk_btn_twitter_login);
        btnReg = (Button) findViewById(R.id.chat_sdk_btn_register);
        etEmail = (EditText) findViewById(R.id.chat_sdk_et_mail);
        etPass = (EditText) findViewById(R.id.chat_sdk_et_password);
        btnGoogle = (Button) findViewById(R.id.chat_sdk_btn_google_login);
        btnFacebook = (Button) findViewById(R.id.chat_sdk_btn_facebook_login);

        ConstraintSet set = new ConstraintSet();

        if(!NM.auth().accountTypeEnabled(AccountDetails.Type.Facebook)) {
            set.constrainWidth(R.id.chat_sdk_btn_facebook_login, 0);
        }
        if(!NM.auth().accountTypeEnabled(AccountDetails.Type.Twitter)) {
            set.constrainWidth(R.id.chat_sdk_btn_twitter_login, 0);
        }
        if(!NM.auth().accountTypeEnabled(AccountDetails.Type.Google)) {
            set.constrainWidth(R.id.chat_sdk_btn_google_login, 0);
        }
        if(!NM.auth().accountTypeEnabled(AccountDetails.Type.Anonymous)) {
            set.constrainHeight(R.id.chat_sdk_btn_anon_login, 0);
        }

        set.applyTo((ConstraintLayout) findViewById(R.id.chat_sdk_root_view));

        // TODO: Remove this
//        etEmail.setText("ben");
//        etPass.setText("123456");

        appIconImage = (ImageView) findViewById(R.id.app_icon);

        appIconImage.post(new Runnable() {
            @Override
            public void run() {
                appIconImage.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(NM.socialLogin() != null) {
            NM.socialLogin().onActivityResult(requestCode, resultCode, data);
        }
    }

    private void initListeners(){

        btnLogin.setOnClickListener(this);
        btnReg.setOnClickListener(this);
        btnAnon.setOnClickListener(this);
        btnTwitter.setOnClickListener(this);
        btnFacebook.setOnClickListener(this);
        btnGoogle.setOnClickListener(this);

        etPass.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE){
                    btnLogin.callOnClick();
                }
                return false;
            }
        });
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();

        Action completion = new Action() {
            @Override
            public void run() throws Exception {
                afterLogin();
            }
        };

        final Consumer<Throwable> error = new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) throws Exception {
                throwable.printStackTrace();
                Toast.makeText(LoginActivity.this, throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        };

        showProgressDialog(getString(R.string.authenticating));

        if (i == R.id.chat_sdk_btn_login) {
            passwordLogin();
        }
        else if (i == R.id.chat_sdk_btn_anon_login) {
            anonymousLogin();
        }
        else if (i == R.id.chat_sdk_btn_register)
        {
            register();
        }
        else if (i == R.id.chat_sdk_btn_twitter_login){
            if(NM.socialLogin() != null) {
                NM.socialLogin().loginWithTwitter(this).doOnError(error)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(completion, error);
            }
        }
        else if (i == R.id.chat_sdk_btn_facebook_login) {
            if(NM.socialLogin() != null) {
                NM.socialLogin().loginWithFacebook(this).doOnError(error)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(completion, error);
            }
        }
        else if (i == R.id.chat_sdk_btn_google_login) {
            if(NM.socialLogin() != null) {
                NM.socialLogin().loginWithGoogle(this).doOnError(error)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(completion, error);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        initListeners();

        // If the logged out flag isn't set...
        if (getIntent() == null || getIntent().getExtras() == null || (boolean) getIntent().getExtras().get(BaseInterfaceAdapter.ATTEMPT_CACHED_LOGIN)) {

            showProgressDialog(getString(R.string.authenticating));

            NM.auth().authenticateWithCachedToken()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new CompletableObserver() {
                @Override
                public void onSubscribe(@NonNull Disposable d) {}

                @Override
                public void onComplete() {
                    afterLogin();
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    dismissProgressDialog();
                }
            });
        }

//        int permissionCheck = ContextCompat.checkSelfPermission(AppContext.shared().context(),
//                Manifest.permission.WRITE_EXTERNAL_STORAGE);
//
//        if(permissionCheck == PERMISSION_DENIED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
//                    FILE_PERMISSION_REQUEST);
//        }

    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           String permissions[], int[] grantResults) {
//        if(requestCode == FILE_PERMISSION_REQUEST) {
//            if (grantResults.length > 0
//                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//
//                // permission was granted, yay! Do the
//                // contacts-related task you need to do.
//
//            } else {
//
//                // permission denied, boo! Disable the
//                // functionality that depends on this permission.
//            }
//        }
//    }

    /* Dismiss dialog and open main activity.*/
    protected void afterLogin() {

        dismissProgressDialog();
        InterfaceManager.shared().a.startMainActivity();
    }

    public void passwordLogin() {
        if (!checkFields())
            return;

        showProgressDialog(getString(R.string.connecting));

        AccountDetails details = new AccountDetails();
        details.type = AccountDetails.Type.Username;
        details.username = etEmail.getText().toString();
        details.password = etPass.getText().toString();

        NM.auth().authenticate(details)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onComplete() {
                afterLogin();
            }

            @Override
            public void onError(Throwable e) {
                toastErrorMessage(e, false);
                e.printStackTrace();
                dismissProgressDialog();
            }
        });
    }

    public void register() {
        if (!checkFields()) {
            return;
        }

        showProgressDialog(getString(R.string.registering));

        AccountDetails details = new AccountDetails();
        details.type = AccountDetails.Type.Register;
        details.username = etEmail.getText().toString();
        details.password = etPass.getText().toString();

        NM.auth().authenticate(details)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onComplete() {
                afterLogin();
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                toastErrorMessage(e, false);
                dismissProgressDialog();
            }
        });
    }

    public void anonymousLogin () {
        showProgressDialog(getString(R.string.connecting));

        AccountDetails details = new AccountDetails();
        details.type = AccountDetails.Type.Anonymous;

        NM.auth().authenticate(details)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onComplete() {
                afterLogin();
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                toastErrorMessage(e, false);
                dismissProgressDialog();
            }
        });

    }

    /* Exit Stuff*/
    @Override
    public void onBackPressed() {
        if (exitOnBackPressed)
        {
            // Exit the app.
            // If logged out from the main activity pressing back in the LoginActivity will get me back to the Main so this have to be done.
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        else super.onBackPressed();

    }

    public void toastErrorMessage(Throwable error, boolean login){
        String errorMessage = "";

        if (StringUtils.isNotBlank(error.getMessage())) {
            errorMessage = error.getMessage();
        }
        else if (login) {
            errorMessage = getString(R.string.login_activity_failed_to_login_toast);
        }
        else {
            errorMessage = getString(R.string.login_activity_failed_to_register_toast);
        }

        showToast(errorMessage);
    }

    protected boolean checkFields(){
        if (etEmail.getText().toString().isEmpty()) {
            showToast(getString(R.string.login_activity_no_mail_toast));
            return false;
        }

        if (etPass.getText().toString().isEmpty()) {
            showToast( getString(R.string.login_activity_no_password_toast) );
            return false;
        }

        return true;
    }

//    public void onSessionStateChange(Session session, SessionState state, Exception exception){
//
//        if (!NM.auth().accountTypeEnabled(AccountType.Facebook))
//        {
//            return;
//        }
//
//        if (exception != null)
//        {
//            exception.printStackTrace();
//            if (exception instanceof FacebookOperationCanceledException)
//            {
//                return;
//            }
//        }else showOrUpdateProgressDialog(getString(R.string.authenticating));
//
//        FacebookManager.onSessionStateChange(session, state, exception).subscribe(new CompletableObserver() {
//            @Override
//            public void onSubscribe(Disposable d) {
//            }
//
//            @Override
//            public void onComplete() {
//                if (DEBUG) Timber.i("Connected to facebook");
//                afterLogin();
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                if (DEBUG) Timber.i(TAG, "Error connecting to Facebook");
//                showToast( getString(R.string.login_activity_facebook_connection_fail_toast) );
//                FacebookManager.logout(LoginActivity.this);
//                dismissProgressDialog();
//            }
//        });
//    }

    protected void setExitOnBackPressed(boolean exitOnBackPressed) {
        this.exitOnBackPressed = exitOnBackPressed;
    }
}