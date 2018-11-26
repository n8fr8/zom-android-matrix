package info.guardianproject.keanuapp.ui.contacts;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;

import info.guardianproject.keanuapp.R;
import info.guardianproject.keanu.core.model.Contact;
import info.guardianproject.keanu.core.model.ImErrorInfo;
import info.guardianproject.keanu.core.plugin.xmpp.XmppAddress;
import info.guardianproject.keanu.core.provider.Imps;
import info.guardianproject.keanu.core.service.IChatSession;
import info.guardianproject.keanu.core.service.IChatSessionManager;
import info.guardianproject.keanu.core.service.IContactListManager;
import info.guardianproject.keanu.core.service.IImConnection;
import info.guardianproject.keanu.core.service.RemoteImService;
import info.guardianproject.keanu.core.tasks.ChatSessionInitTask;
import info.guardianproject.keanu.core.util.DatabaseUtils;
import info.guardianproject.keanuapp.ImApp;
import info.guardianproject.keanuapp.MainActivity;
import info.guardianproject.keanuapp.tasks.AddContactAsyncTask;
import info.guardianproject.keanuapp.ui.BaseActivity;
import info.guardianproject.keanuapp.ui.conversation.ConversationDetailActivity;
import info.guardianproject.keanuapp.ui.gallery.GalleryListFragment;
import info.guardianproject.keanuapp.ui.onboarding.OnboardingManager;
import info.guardianproject.keanuapp.ui.qr.QrDisplayActivity;
import info.guardianproject.keanuapp.ui.qr.QrShareAsyncTask;

import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_HEIGHT;
import static info.guardianproject.keanu.core.KeanuConstants.DEFAULT_AVATAR_WIDTH;
import static info.guardianproject.keanu.core.KeanuConstants.LOG_TAG;


public class ContactDisplayActivity extends BaseActivity {

    private long mContactId = -1;
    private String mNickname = null;
    private String mUsername = null;
    private long mProviderId = -1;
    private long mAccountId = -1;
    private IImConnection mConn;

    private String mRemoteOtrFingerprint;
    private List<String> mRemoteOmemoFingerprints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.awesome_activity_contact);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mContactId = getIntent().getLongExtra("contactId", -1);

        mNickname = getIntent().getStringExtra("nickname");
        mUsername = getIntent().getStringExtra("address");
        mProviderId = getIntent().getLongExtra("provider", -1);
        mAccountId = getIntent().getLongExtra("account", -1);

        String remoteFingerprint = getIntent().getStringExtra("fingerprint");

        mConn = RemoteImService.getConnection(mProviderId, mAccountId);

        setTitle("");

        TextView tv = (TextView) findViewById(R.id.tvNickname);
        tv = (TextView) findViewById(R.id.tvNickname);
        tv.setText(mNickname);

        tv = (TextView) findViewById(R.id.tvUsername);
        tv.setText(mUsername);

        View btn = findViewById(R.id.btnStartChat);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startChat();

            }
        });

        boolean showAddFriends = true;

        if (!TextUtils.isEmpty(mUsername)) {
            try {
                Drawable avatar = DatabaseUtils.getAvatarFromAddress(getContentResolver(), mUsername, DEFAULT_AVATAR_WIDTH, DEFAULT_AVATAR_HEIGHT, false);
                if (avatar != null) {
                    ImageView iv = (ImageView) findViewById(R.id.imageAvatar);
                    iv.setImageDrawable(avatar);
                    iv.setVisibility(View.VISIBLE);
                    findViewById(R.id.imageSpacer).setVisibility(View.GONE);
                }
            } catch (Exception e) {
            }

            Cursor c = getContentResolver().query(Imps.Contacts.CONTENT_URI, new String[]{Imps.Contacts.SUBSCRIPTION_TYPE}, Imps.Contacts.USERNAME + "=?", new String[]{mUsername}, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    int subscriptionType = c.getInt(c.getColumnIndex(Imps.Contacts.SUBSCRIPTION_TYPE));
                    if (subscriptionType != Imps.Contacts.SUBSCRIPTION_TYPE_NONE && subscriptionType != Imps.Contacts.SUBSCRIPTION_TYPE_FROM) {
                        // It is "to", or "both" or some other value with special meaning.
                        showAddFriends = false;
                    }
                }
                c.close();
            }
        }

        if (showAddFriends) {
            Button btnAddAsFriend = findViewById(R.id.btnAddAsFriend);
            btnAddAsFriend.setText(getString(R.string.add_x_as_friend, mNickname));
            btnAddAsFriend.setVisibility(View.VISIBLE);
        }

        if (mConn != null) {
            new AsyncTask<String, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(String... strings) {

                    mRemoteOtrFingerprint = strings[0];

                    if (mRemoteOtrFingerprint == null) {
                        mRemoteOtrFingerprint = "";//OtrChatManager.getInstance().getRemoteKeyFingerprint(mUsername);
                    }


                    try {
                        mRemoteOmemoFingerprints = mConn.getFingerprints(mUsername);

                    } catch (RemoteException re) {

                    }

                    return true;
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    super.onPostExecute(success);

                    if (mRemoteOmemoFingerprints == null ||
                            mRemoteOmemoFingerprints.size() == 0)
                        displayFingerprint(mRemoteOtrFingerprint);
                    else
                        displayFingerprint(mRemoteOmemoFingerprints.get(mRemoteOmemoFingerprints.size() - 1));
                }
            }.execute(remoteFingerprint);
        }

    }

    private void displayFingerprint (final String remoteFingerprint)
    {

        try {

            ImageView btnQrShare = (ImageView) findViewById(R.id.qrshare);
            ImageView iv = (ImageView)findViewById(R.id.qrcode);
            TextView tv = (TextView)findViewById(R.id.tvFingerprint);

           // ArrayList<String> fingerprints = OtrChatManager.getInstance().getRemoteKeyFingerprints(mUsername);

            if (!TextUtils.isEmpty(remoteFingerprint)) {

                findViewById(R.id.listEncryptionKey).setVisibility(View.VISIBLE);

                tv.setText(prettyPrintFingerprint(remoteFingerprint));

                iv.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        String inviteString;
                        try {
                            inviteString = OnboardingManager.generateInviteLink(ContactDisplayActivity.this, mUsername, remoteFingerprint, mNickname);

                            Intent intent = new Intent(ContactDisplayActivity.this, QrDisplayActivity.class);
                            intent.putExtra(Intent.EXTRA_TEXT, inviteString);
                            intent.setType("text/plain");
                            startActivity(intent);

                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                    }

                });

                btnQrShare.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        try {
                            String inviteLink = OnboardingManager.generateInviteLink(ContactDisplayActivity.this, mUsername, remoteFingerprint, mNickname);
                            new QrShareAsyncTask(ContactDisplayActivity.this).execute(inviteLink, mNickname);
                        } catch (IOException ioe) {
                            Log.e(LOG_TAG, "couldn't generate QR code", ioe);
                        }
                    }
                });


                //if (!OtrChatManager.getInstance().isRemoteKeyVerified(mUsername, remoteFingerprint))
                 //   btnVerify.setVisibility(View.VISIBLE);

            }

        }
        catch (Exception e)
        {
            Log.e(LOG_TAG,"error displaying contact",e);
        }


    }


    public void verifyClicked (View view)
    {
        verifyRemoteFingerprint();
        findViewById(R.id.btnVerify).setVisibility(View.GONE);
    }

    public void addFriendClicked (final View view)
    {
        LayoutInflater factory = LayoutInflater.from(this);
        final View dialogAddFriend = factory.inflate(R.layout.alert_dialog_add_friend, null);
        TextView title = dialogAddFriend.findViewById(R.id.alertTitle);
        title.setText(getString(R.string.add_x_as_friend_question, mNickname));

        ImageView avatarView = dialogAddFriend.findViewById(R.id.imageAvatar);
        avatarView.setVisibility(View.GONE);
        try {
            Drawable avatar = DatabaseUtils.getAvatarFromAddress(getContentResolver(), mUsername, DEFAULT_AVATAR_WIDTH, DEFAULT_AVATAR_HEIGHT, true);
            if (avatar != null) {
                avatarView.setImageDrawable(avatar);
                avatarView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
        }


        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogAddFriend)
                .create();
        dialog.show();

        View btnAdd = dialogAddFriend.findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImApp app = (ImApp)getApplication();
                new AddContactAsyncTask(mProviderId, mAccountId).execute(mUsername, null, null);
                dialog.dismiss();
                view.setVisibility(View.GONE);
                showFriendAddedView();
            }
        });

        TextView btnCancel = dialogAddFriend.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        /**
        Typeface typeface;
        if ((typeface = CustomTypefaceManager.getCurrentTypeface(this))!=null) {
            title.setTypeface(typeface);
            btnCancel.setTypeface(typeface);
        }**/
    }

    private void showGallery (int contactId)
    {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        GalleryListFragment fragment = new GalleryListFragment();
        Bundle args = new Bundle();
        args.putInt("contactId", contactId);
        fragment.setArguments(args);
        fragmentTransaction.add(R.id.fragment_container, fragment, "MyActivity");
        fragmentTransaction.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

       getMenuInflater().inflate(R.menu.menu_contact_detail, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_verify_or_view:
                verifyRemoteFingerprint();
                return true;
            /**
            case R.id.menu_verify_question:
                initSmpUI();
                return true;**/
            case R.id.menu_remove_contact:
                deleteContact();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String prettyPrintFingerprint(String fingerprint) {
        StringBuffer spacedFingerprint = new StringBuffer();

        for (int i = 0; i + 8 <= fingerprint.length(); i += 8) {
            spacedFingerprint.append(fingerprint.subSequence(i, i + 8));
            spacedFingerprint.append(' ');
        }

        return spacedFingerprint.toString();
    }

    void deleteContact ()
    {
        new android.support.v7.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.menu_remove_contact))
                .setMessage(getString(R.string.confirm_delete_contact, mNickname))
                .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        doDeleteContact();
                        finish();
                        startActivity(new Intent(ContactDisplayActivity.this, MainActivity.class));
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();

    }

    void doDeleteContact ()
    {
        try {

            IImConnection mConn;
            mConn = RemoteImService.getConnection(mProviderId, mAccountId);

            IContactListManager manager = mConn.getContactListManager();

            int res = manager.removeContact(mUsername);
            if (res != ImErrorInfo.NO_ERROR) {
                //mHandler.showAlert(R.string.error,
                //      ErrorResUtils.getErrorRes(getResources(), res, address));
            }

        }
        catch (RemoteException re)
        {

        }
    }

    public void startChat ()
    {
        if (mConn == null)
            return;

        try {
            IChatSessionManager manager = mConn.getChatSessionManager();
            if (manager != null) {
                IChatSession session = manager.getChatSession(mUsername);
                if (session == null) {
                    new ChatSessionInitTask(mProviderId, mAccountId, Imps.Contacts.TYPE_NORMAL, false)
                    {
                        @Override
                        protected void onPostExecute(Long chatId) {
                            super.onPostExecute(chatId);

                            if (mContactId == -1) {
                                Intent intent = new Intent(ContactDisplayActivity.this, ConversationDetailActivity.class);
                                intent.putExtra("id", chatId);

                                startActivity(intent);
                                finish();
                            }
                        }
                    }.executeOnExecutor(ImApp.sThreadPoolExecutor,new Contact(new XmppAddress(mUsername)));
                 //   Toast.makeText(this, getString(R.string.message_waiting_for_friend), Toast.LENGTH_LONG).show();
                }
                else
                {
                    Intent intent = new Intent(ContactDisplayActivity.this, ConversationDetailActivity.class);
                    intent.putExtra("id", session.getId());
                    startActivity(intent);
                    finish();

                }
            }
        }
        catch (RemoteException re){}

        if (mContactId != -1) {
            Intent intent = new Intent(ContactDisplayActivity.this, ConversationDetailActivity.class);
            intent.putExtra("id", mContactId);

            startActivity(intent);
            finish();
        }

    }



    private void verifyRemoteFingerprint() {

        new AsyncTask<String, Void, Boolean>()
        {
            @Override
            protected Boolean doInBackground(String... strings) {


                try {
                    if (mConn != null) {
                        IContactListManager listManager = mConn.getContactListManager();

                        if (listManager != null)
                            listManager.approveSubscription(new Contact(new XmppAddress(mUsername), mNickname, Imps.Contacts.TYPE_NORMAL));

                        IChatSessionManager manager = mConn.getChatSessionManager();

                        if (manager != null) {
                            IChatSession session = manager.getChatSession(mUsername);

                            if (session != null) {

                                /**
                                IOtrChatSession otrChatSession = session.getDefaultOtrChatSession();

                                if (otrChatSession != null) {
                                    otrChatSession.verifyKey(otrChatSession.getRemoteUserId());
                                    Snackbar.make(findViewById(R.id.main_content), getString(R.string.action_verified), Snackbar.LENGTH_LONG).show();
                                }**/
                            }
                        }

                    }

                } catch (RemoteException e) {
                    Log.e(LOG_TAG, "error init otr", e);

                }

                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                super.onPostExecute(success);


            }
        }.execute();



    }


    private void showFriendAddedView() {
        final ViewGroup mainView = findViewById(R.id.main_content);
        final View friendAddedView = LayoutInflater.from(this).inflate(R.layout.friend_added, mainView, false);
        mainView.addView(friendAddedView);

        Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        final Animation fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mainView.removeView(friendAddedView);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        friendAddedView.bringToFront();
        friendAddedView.startAnimation(fadeIn);
        friendAddedView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                friendAddedView.startAnimation(fadeOut);
            }
        });
        friendAddedView.postDelayed(new Runnable() {
            @Override
            public void run() {
                friendAddedView.startAnimation(fadeOut);
            }
        }, 3000);
    }


}
