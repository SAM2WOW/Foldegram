/*
 * Foldegram's two-conversation workspace.
 * Licensed under the GNU GPL v. 2 or later, like the Telegram client it hosts.
 */
package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FlagSecureReason;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ActivityWindowEmptyBackgroundDrawable;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PasscodeView;
import org.telegram.ui.Components.ThemeEditorView;

import java.util.ArrayList;

/**
 * Both conversations are actual ChatActivity instances, each with its own navigation stack.
 * Neither stack is shared with LaunchActivity, nor are global close-chats broadcasts emitted
 * when opening or replacing a pane. Fold/unfold only changes view geometry, never fragments.
 */
public class FoldegramChatWindowActivity extends BasePermissionsActivity implements
        INavigationLayout.INavigationLayoutDelegate, NotificationCenter.NotificationCenterDelegate {
    private static final String EXTRA_ACCOUNT = "foldegram.account";
    private static final String EXTRA_USER = "foldegram.accountUser";
    private static final String EXTRA_FIRST = "foldegram.firstChat";
    private static final String EXTRA_SECOND = "foldegram.secondChat";
    private static final String EXTRA_TOPIC = "foldegram.topic";
    private static final String STATE_ACTIVE = "foldegram.activePane";
    // The opened Fold's logical width is often below the tablet 720dp breakpoint.
    static final int DUAL_PANE_MIN_WIDTH_DP = 600;
    private static FoldegramChatWindowActivity foregroundInstance;

    private final ArrayList<BaseFragment> firstStack = new ArrayList<>();
    private final ArrayList<BaseFragment> secondStack = new ArrayList<>();
    private final INavigationLayout[] paneLayouts = new INavigationLayout[2];
    private final TextView[] paneButtons = new TextView[2];
    private final Bundle[] rootArguments = new Bundle[2];
    private final SparseIntArray resultPanes = new SparseIntArray();
    private DrawerLayoutContainer root;
    private LinearLayout toolbar;
    private PaneContainer panes;
    private PasscodeView passcodeView;
    private FlagSecureReason passcodeSecureReason;
    private int activePane = 1;
    private long accountUserId;
    private boolean resumed;
    private boolean paneResumed;
    private boolean connectionResumed;
    private boolean destroyed;
    private boolean chatsCreated;
    private boolean themeRebuildPending;
    private Runnable lockRunnable;
    private Bundle pendingState;

    public static boolean ownsForeground() {
        FoldegramChatWindowActivity activity = foregroundInstance;
        return activity != null && activity.resumed && !activity.destroyed && !activity.isFinishing();
    }

    /** Used by process-wide viewers/bulletins while this workspace owns the foreground. */
    public static BaseFragment getActiveFragment() {
        FoldegramChatWindowActivity activity = foregroundInstance;
        if (activity == null || !activity.resumed || activity.destroyed || activity.isFinishing()
                || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter
                || activity.passcodeView == null || activity.passcodeView.getVisibility() == View.VISIBLE) {
            return null;
        }
        INavigationLayout layout = activity.paneLayouts[activity.activePane];
        return layout == null ? null : layout.getSafeLastFragment();
    }

    /** Drop listeners must call this only for the actual destination, not DRAG_STARTED. */
    public boolean activateForDrop(ChatActivity owner) {
        if (!resumed || destroyed || isFinishing() || !chatsCreated || SharedConfig.appLocked
                || SharedConfig.isWaitingForPasscodeEnter || passcodeView.getVisibility() == View.VISIBLE
                || owner == null || owner.getParentActivity() != this || panes.getVisibility() != View.VISIBLE) {
            return false;
        }
        for (int i = 0; i < paneLayouts.length; i++) {
            View pane = paneLayouts[i].getView();
            if (paneLayouts[i].getLastFragment() == owner && pane.isShown()
                    && pane.getWidth() > 0 && pane.getHeight() > 0 && pane.getWindowToken() != null) {
                setActivePane(i, false);
                return paneResumed && paneLayouts[activePane].getLastFragment() == owner;
            }
        }
        return false;
    }

    /** Avoid silently turning scheduled messages, comment threads or saved subdialogs into a main chat. */
    public static boolean canOpenFrom(ChatActivity source) {
        if (source == null) {
            return false;
        }
        return source.getParentActivity() != null
                && !(source.getParentActivity() instanceof FoldegramChatWindowActivity)
                && source.getChatMode() == ChatActivity.MODE_DEFAULT
                && (!source.isThreadChat() || source.isTopic);
    }

    private static boolean canTransferComposition(ChatActivity source) {
        ChatActivityEnterView composer = source.getChatActivityEnterView();
        return !source.isEditingMessageMedia() && !BaseFragment.hasSheets(source)
                && composer != null && !composer.isEditingMessage() && !composer.isRecordingAudioVideo()
                && !composer.hasAudioToSend() && !source.hasFoldegramPendingForward();
    }

    public static void showChatPicker(BaseFragment source) {
        if (!(source instanceof ChatActivity) || !canOpenFrom((ChatActivity) source)) {
            return;
        }
        ChatActivity chat = (ChatActivity) source;
        if (!canTransferComposition(chat)) {
            Toast.makeText(source.getParentActivity(), R.string.FoldegramFinishComposition, Toast.LENGTH_LONG).show();
            return;
        }
        final Bundle first = argumentsForChat(chat);
        final int account = source.getCurrentAccount();
        final long firstDialog = chat.getDialogId();
        DialogsActivity picker = createPicker(account);
        picker.setDelegate((fragment, selected, message, param, notify, scheduleDate, repeat, topics) -> {
            if (selected.isEmpty() || source.getParentActivity() == null) {
                return false;
            }
            if (!canTransferComposition(chat)) {
                Toast.makeText(source.getParentActivity(), R.string.FoldegramFinishComposition, Toast.LENGTH_LONG).show();
                return false;
            }
            MessagesStorage.TopicKey target = selected.get(0);
            if (target.dialogId == firstDialog) {
                Toast.makeText(source.getParentActivity(), R.string.FoldegramChooseDifferentChat, Toast.LENGTH_SHORT).show();
                return false;
            }
            Bundle second = argumentsForDialog(target.dialogId, target.topicId);
            if (!MessagesController.getInstance(account).checkCanOpenChat(second, fragment)) {
                return false;
            }
            Activity activity = source.getParentActivity();
            Intent intent = new Intent(activity, FoldegramChatWindowActivity.class);
            intent.putExtra(EXTRA_ACCOUNT, account);
            intent.putExtra(EXTRA_USER, UserConfig.getInstance(account).getClientUserId());
            intent.putExtra(EXTRA_FIRST, first);
            intent.putExtra(EXTRA_SECOND, second);
            // Save before transferring ownership. The original chat must not remain behind with
            // a stale nonempty composer that could later overwrite the workspace's newer draft.
            chat.saveDraft();
            if (topics != null) {
                topics.finishFragment(false);
            }
            fragment.finishFragment(false);
            activity.startActivity(intent);
            // Successful launch transfers the source conversation into this workspace. Closing
            // it returns to the underlying chat list rather than a second copy of the source.
            if (chat.getParentLayout() != null && chat.getParentLayout().getLastFragment() == chat) {
                chat.finishFragment(false);
            } else {
                // A nested community picker may still occupy the top slot; remove the exact source.
                chat.removeSelfFromStack(true);
            }
            return true;
        });
        source.presentFragment(picker);
    }

    private static DialogsActivity createPicker(int account) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("allowSwitchAccount", false);
        args.putBoolean("canSelectTopics", true);
        // DEFAULT includes read-only channels; FORWARD would incorrectly filter viewing choices.
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setCurrentAccount(account);
        return picker;
    }

    private static Bundle argumentsForDialog(long dialogId, long topicId) {
        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        args.putLong(EXTRA_TOPIC, topicId);
        args.putBoolean("need_remove_previous_same_chat_activity", false);
        return args;
    }

    private static Bundle argumentsForChat(ChatActivity chat) {
        return argumentsForDialog(chat.getDialogId(), chat.isTopic ? chat.getTopicId() : 0);
    }

    private static long dialogId(Bundle args) {
        int encrypted = args.getInt("enc_id", 0);
        if (encrypted != 0) {
            return DialogObject.makeEncryptedDialogId(encrypted);
        }
        long user = args.getLong("user_id", 0);
        return user != 0 ? user : -args.getLong("chat_id", 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ApplicationLoader.postInitApplication();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        getWindow().setBackgroundDrawable(new ActivityWindowEmptyBackgroundDrawable());
        super.onCreate(savedInstanceState);
        // Lifecycle-aware AndroidX dispatch bridges platform/predictive Back on recent Android.
        // Call the pane handler directly; it must never recurse into the dispatcher.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FoldegramChatWindowActivity.this.onBackPressed();
            }
        });
        updateSecureFlag();
        AndroidUtilities.fillStatusBarHeight(this, false);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        Bundle input = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (!readArguments(input)) {
            finish();
            return;
        }
        pendingState = savedInstanceState;
        if (savedInstanceState != null) {
            int[] codes = savedInstanceState.getIntArray("result_codes");
            int[] destinations = savedInstanceState.getIntArray("result_panes");
            if (codes != null && destinations != null && codes.length == destinations.length) {
                for (int i = 0; i < codes.length; i++) {
                    resultPanes.put(codes[i], destinations[i] == 0 ? 0 : 1);
                }
            }
        }
        buildViews();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appDidLogout);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.passcodeDismissed);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.screenStateChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        if (shouldLock()) {
            showPasscode();
        } else {
            createChats();
        }
    }

    private boolean readArguments(Bundle input) {
        if (input == null) {
            return false;
        }
        currentAccount = input.getInt(EXTRA_ACCOUNT, -1);
        if (!UserConfig.isValidAccount(currentAccount)) {
            currentAccount = -1;
            return false;
        }
        accountUserId = input.getLong(EXTRA_USER, 0);
        if (accountUserId == 0 || accountUserId != UserConfig.getInstance(currentAccount).getClientUserId()) {
            return false;
        }
        rootArguments[0] = input.getBundle(EXTRA_FIRST);
        rootArguments[1] = input.getBundle(EXTRA_SECOND);
        activePane = input.getInt(STATE_ACTIVE, 1) == 0 ? 0 : 1;
        if (rootArguments[0] == null || rootArguments[1] == null) {
            return false;
        }
        return dialogId(rootArguments[0]) != 0 && dialogId(rootArguments[1]) != 0
                && dialogId(rootArguments[0]) != dialogId(rootArguments[1]);
    }

    private void buildViews() {
        root = new DrawerLayoutContainer(this);
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        // DrawerLayoutContainer draws edge-to-edge. Own the full workspace's insets once;
        // an IME reduces both pane viewports without applying its padding twice in each chat.
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets insets = AndroidUtilities.getDefaultWindowInsets(windowInsets, true);
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48)));
        ImageView close = toolbarIcon(R.drawable.ic_ab_back, R.string.FoldegramCloseTwoChats);
        close.setOnClickListener(view -> finish());
        for (int i = 0; i < 2; i++) {
            final int pane = i;
            TextView button = paneButtons[i] = new TextView(this);
            button.setGravity(Gravity.CENTER);
            button.setTextSize(14);
            button.setSingleLine();
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
            button.setOnClickListener(view -> setActivePane(pane, true));
            button.setOnLongClickListener(view -> {
                setActivePane(pane, true);
                showReplacementPicker();
                return true;
            });
            toolbar.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        ImageView replace = toolbarIcon(R.drawable.msg_replace, R.string.FoldegramChangeActiveChat);
        replace.setOnClickListener(view -> showReplacementPicker());
        panes = new PaneContainer(this);
        content.addView(panes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        for (int i = 0; i < 2; i++) {
            INavigationLayout layout = paneLayouts[i] = INavigationLayout.newLayout(this, false);
            // Reuse Telegram's embedded-window geometry, without BubbleActivity or its singleton/global close behavior.
            layout.setInBubbleMode(true);
            layout.setRemoveActionBarExtraHeight(true);
            layout.setFragmentStack(i == 0 ? firstStack : secondStack);
            layout.setDrawerLayoutContainer(root);
            layout.setDelegate(this);
            layout.setFragmentStackChangedListener(() -> {
                if (chatsCreated) {
                    updateToolbar();
                }
            });
            panes.addView(layout.getView());
        }
        root.setParentActionBarLayout(paneLayouts[activePane]);
        passcodeView = new PasscodeView(this) {
            @Override
            protected void onHidden() {
                if (destroyed || isFinishing()) {
                    return;
                }
                if (SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) {
                    showPasscode();
                    return;
                }
                revealUnlockedContent();
            }
        };
        root.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        passcodeView.setDelegate(view -> {
            SharedConfig.isWaitingForPasscodeEnter = false;
            // The overlay remains VISIBLE during its exit animation. Resume only from onHidden.
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.passcodeDismissed, view);
        });
        updateToolbar();
    }

    private ImageView toolbarIcon(int icon, int description) {
        ImageView view = new ImageView(this);
        view.setImageResource(icon);
        view.setColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon));
        view.setScaleType(ImageView.ScaleType.CENTER);
        // Custom strings use Android resources: Telegram's packed locale build scans only strings.xml.
        view.setContentDescription(getString(description));
        view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarDefaultSelector)));
        toolbar.addView(view, new LinearLayout.LayoutParams(AndroidUtilities.dp(48), ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    private void createChats() {
        if (chatsCreated || destroyed || isFinishing()) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            ChatActivity chat = createChat(rootArguments[i]);
            if (chat == null || !paneLayouts[i].addFragmentToStack(chat)) {
                Toast.makeText(this, R.string.FoldegramReopenChats, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (pendingState != null) {
                Bundle self = pendingState.getBundle("pane_self_" + i);
                if (self != null) {
                    chat.restoreSelfArgs(self);
                }
            }
            paneLayouts[i].showLastFragment();
            // showLastFragment resumes as part of mounting. Only the focused pane owns input/read state.
            paneLayouts[i].onPause();
        }
        pendingState = null;
        chatsCreated = true;
        applyPendingTheme();
        resumeActivePane();
        updateToolbar();
    }

    private ChatActivity createChat(Bundle args) {
        ChatActivity chat = new ChatActivity(new Bundle(args));
        chat.setCurrentAccount(currentAccount);
        long topic = args.getLong(EXTRA_TOPIC, 0);
        if (topic != 0) {
            // Never silently open the main forum when a selected topic cannot be restored.
            if (MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-dialogId(args), topic) == null) {
                return null;
            }
            ForumUtilities.applyTopic(chat, MessagesStorage.TopicKey.of(dialogId(args), topic));
        }
        return chat;
    }

    private void showReplacementPicker() {
        if (!chatsCreated || passcodeView.getVisibility() == View.VISIBLE) {
            return;
        }
        final int targetPane = activePane;
        DialogsActivity picker = createPicker(currentAccount);
        picker.setDelegate((fragment, selected, message, param, notify, scheduleDate, repeat, topics) -> {
            if (selected.isEmpty()) {
                return false;
            }
            MessagesStorage.TopicKey selection = selected.get(0);
            ChatActivity other = lastChat(1 - targetPane);
            if (other != null && selection.dialogId == other.getDialogId()) {
                Toast.makeText(this, R.string.FoldegramChooseDifferentChat, Toast.LENGTH_SHORT).show();
                return false;
            }
            Bundle args = argumentsForDialog(selection.dialogId, selection.topicId);
            if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args, fragment)) {
                return false;
            }
            ChatActivity chat = createChat(args);
            if (chat == null) {
                Toast.makeText(this, R.string.FoldegramReopenChats, Toast.LENGTH_LONG).show();
                return false;
            }
            savePaneDraft(targetPane);
            paneLayouts[targetPane].removeAllFragments();
            paneResumed = false;
            rootArguments[targetPane] = args;
            if (!paneLayouts[targetPane].addFragmentToStack(chat)) {
                finish();
                return true;
            }
            paneLayouts[targetPane].showLastFragment();
            paneResumed = resumed;
            if (!resumed) {
                paneLayouts[targetPane].onPause();
            }
            updateToolbar();
            return true;
        });
        paneLayouts[targetPane].presentFragment(picker);
    }

    private ChatActivity lastChat(int pane) {
        ArrayList<BaseFragment> stack = pane == 0 ? firstStack : secondStack;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i) instanceof ChatActivity) {
                return (ChatActivity) stack.get(i);
            }
        }
        return null;
    }

    private void savePaneDraft(int pane) {
        ChatActivity chat = lastChat(pane);
        if (chat != null && chat.getFragmentView() != null) {
            chat.saveDraft();
        }
    }

    private void setActivePane(int pane, boolean hideKeyboard) {
        if (pane == activePane || !chatsCreated || isFinishing()) {
            return;
        }
        if (paneResumed) {
            paneLayouts[activePane].onPause();
            paneResumed = false;
        }
        View focus = getCurrentFocus();
        if (focus != null) {
            if (hideKeyboard) {
                AndroidUtilities.hideKeyboard(focus);
            }
            focus.clearFocus();
        }
        activePane = pane;
        root.setParentActionBarLayout(paneLayouts[activePane]);
        resumeActivePane();
        panes.requestLayout();
        updateToolbar();
    }

    private void resumeActivePane() {
        if (resumed && chatsCreated && !paneResumed && passcodeView.getVisibility() != View.VISIBLE && !isFinishing()) {
            paneLayouts[activePane].onResume();
            paneResumed = true;
        }
    }

    private void revealUnlockedContent() {
        if (!resumed || destroyed || isFinishing() || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter
                || passcodeView == null || passcodeView.getVisibility() == View.VISIBLE) {
            return;
        }
        panes.setVisibility(View.VISIBLE);
        toolbar.setVisibility(View.VISIBLE);
        createChats();
        applyPendingTheme();
        resumeActivePane();
    }

    private void updateToolbar() {
        if (toolbar == null) {
            return;
        }
        toolbar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        for (int i = 0; i < 2; i++) {
            ChatActivity chat = lastChat(i);
            long did = chat != null ? chat.getDialogId() : dialogId(rootArguments[i]);
            String name = DialogObject.getName(currentAccount, did);
            if (TextUtils.isEmpty(name)) {
                name = LocaleController.getString(R.string.SelectChat);
            }
            paneButtons[i].setText((i + 1) + " · " + name);
            paneButtons[i].setSelected(i == activePane);
            paneButtons[i].setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
            paneButtons[i].setAlpha(i == activePane ? 1f : 0.65f);
            paneButtons[i].setBackgroundColor(i == activePane ? Theme.getColor(Theme.key_actionBarDefaultSelector) : android.graphics.Color.TRANSPARENT);
            paneButtons[i].setContentDescription(getString(R.string.FoldegramFocusChat, i + 1, name));
        }
    }

    private void applyPendingTheme() {
        if (!themeRebuildPending || !resumed || !chatsCreated || destroyed || isFinishing()
                || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter
                || passcodeView.getVisibility() == View.VISIBLE) {
            return;
        }
        themeRebuildPending = false;
        paneResumed = false;
        for (INavigationLayout layout : paneLayouts) {
            layout.onPause();
            // A Telegram fragment rebuild mounts/resumes its top fragment internally.
            layout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
            layout.onPause();
        }
        updateToolbar();
        resumeActivePane();
    }

    private boolean shouldLock() {
        return !SharedConfig.passcodeHash.isEmpty() && (SharedConfig.appLocked
                || SharedConfig.isWaitingForPasscodeEnter || AndroidUtilities.needShowPasscode(true));
    }

    private void showPasscode() {
        if (passcodeView == null || destroyed) {
            return;
        }
        if (paneResumed) {
            paneLayouts[activePane].onPause();
            paneResumed = false;
        }
        if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(false, true);
        }
        if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(false, false);
        }
        if (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible()) {
            ArticleViewer.getInstance().close(false, true);
        }
        panes.setVisibility(View.INVISIBLE);
        toolbar.setVisibility(View.INVISIBLE);
        for (INavigationLayout layout : paneLayouts) {
            layout.dismissDialogs();
        }
        SharedConfig.appLocked = true;
        SharedConfig.isWaitingForPasscodeEnter = true;
        passcodeView.onShow(true, false);
    }

    private void updateSecureFlag() {
        if (passcodeSecureReason == null) {
            passcodeSecureReason = new FlagSecureReason(getWindow(),
                    () -> !SharedConfig.passcodeHash.isEmpty() && !SharedConfig.allowScreenCapture);
            passcodeSecureReason.attach();
        } else {
            // Compose with the two ChatActivity security reasons; never clear their window flag.
            passcodeSecureReason.invalidate();
        }
    }

    private void cancelLockRunnable() {
        if (lockRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(lockRunnable);
            lockRunnable = null;
        }
    }

    private void scheduleLock() {
        cancelLockRunnable();
        if (SharedConfig.passcodeHash.isEmpty()) {
            return;
        }
        SharedConfig.lastPauseTime = (int) (SystemClock.elapsedRealtime() / 1000);
        SharedConfig.saveConfig();
        if (SharedConfig.appLocked || SharedConfig.autoLockIn != 0) {
            lockRunnable = () -> {
                lockRunnable = null;
                if (!resumed && shouldLock()) {
                    showPasscode();
                }
            };
            AndroidUtilities.runOnUIThread(lockRunnable, SharedConfig.appLocked ? 1000 : SharedConfig.autoLockIn * 1000L + 1000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root == null || !UserConfig.isValidAccount(currentAccount)
                || UserConfig.getInstance(currentAccount).getClientUserId() != accountUserId) {
            finish();
            return;
        }
        resumed = true;
        foregroundInstance = this;
        ApplicationLoader.externalInterfacePaused = false;
        ApplicationLoader.mainInterfacePaused = false;
        Utilities.stageQueue.postRunnable(() -> ApplicationLoader.mainInterfacePausedStageQueue = false);
        if (!connectionResumed) {
            AccountInstance.getInstance(currentAccount).getConnectionsManager().setAppPaused(false, false);
            connectionResumed = true;
        }
        cancelLockRunnable();
        if (shouldLock()) {
            showPasscode();
        } else {
            revealUnlockedContent();
        }
        if (passcodeView.getVisibility() == View.VISIBLE) {
            passcodeView.onResume();
        }
        SharedConfig.lastPauseTime = 0;
        SharedConfig.saveConfig();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        if (foregroundInstance == this) {
            foregroundInstance = null;
        }
        if (paneResumed) {
            paneLayouts[activePane].onPause();
            paneResumed = false;
        }
        if (passcodeView != null) {
            passcodeView.onPause();
        }
        ApplicationLoader.externalInterfacePaused = true;
        ApplicationLoader.mainInterfacePaused = !LaunchActivity.isResumed;
        Utilities.stageQueue.postRunnable(() -> ApplicationLoader.mainInterfacePausedStageQueue = !LaunchActivity.isResumed);
        if (connectionResumed) {
            AccountInstance.getInstance(currentAccount).getConnectionsManager().setAppPaused(true, false);
            connectionResumed = false;
        }
        if (root != null) {
            scheduleLock();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        if (root != null) {
            out.putInt(EXTRA_ACCOUNT, currentAccount);
            out.putLong(EXTRA_USER, accountUserId);
            out.putInt(STATE_ACTIVE, activePane);
            int[] codes = new int[resultPanes.size()];
            int[] destinations = new int[resultPanes.size()];
            for (int i = 0; i < resultPanes.size(); i++) {
                codes[i] = resultPanes.keyAt(i);
                destinations[i] = resultPanes.valueAt(i);
            }
            out.putIntArray("result_codes", codes);
            out.putIntArray("result_panes", destinations);
            for (int i = 0; i < 2; i++) {
                savePaneDraft(i);
                ChatActivity chat = lastChat(i);
                Bundle args = chat != null && chat.getChatMode() == ChatActivity.MODE_DEFAULT
                        && (!chat.isThreadChat() || chat.isTopic) ? argumentsForChat(chat) : rootArguments[i];
                out.putBundle(i == 0 ? EXTRA_FIRST : EXTRA_SECOND, new Bundle(args));
                if (chat != null) {
                    Bundle self = new Bundle();
                    chat.saveSelfArgs(self);
                    out.putBundle("pane_self_" + i, self);
                }
            }
        }
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (foregroundInstance == this) {
            foregroundInstance = null;
        }
        cancelLockRunnable();
        if (currentAccount >= 0) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
            if (connectionResumed) {
                AccountInstance.getInstance(currentAccount).getConnectionsManager().setAppPaused(true, false);
                connectionResumed = false;
            }
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.passcodeDismissed);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.screenStateChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        for (INavigationLayout layout : paneLayouts) {
            if (layout != null) {
                layout.removeAllFragments();
            }
        }
        if (passcodeSecureReason != null) {
            passcodeSecureReason.detach();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration config) {
        super.onConfigurationChanged(config);
        // Child widths come from this Activity's measured content, not process-wide tablet/display caches.
        AndroidUtilities.setPreferredMaxRefreshRate(getWindow());
        if (panes != null) {
            panes.requestLayout();
        }
    }

    @Override
    public void onBackPressed() {
        if (root == null || passcodeView.getVisibility() == View.VISIBLE) {
            finish();
        } else if (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()) {
            SecretMediaViewer.getInstance().closePhoto(true, false);
        } else if (PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            PhotoViewer.getInstance().closePhoto(true, false);
        } else {
            paneLayouts[activePane].onBackPressed();
        }
    }

    @Override
    public boolean needCloseLastFragment(INavigationLayout layout) {
        if (layout.getFragmentStack().size() <= 1) {
            finish();
            return false;
        }
        return true;
    }

    @Override
    public boolean needPresentFragment(INavigationLayout layout, INavigationLayout.NavigationParams params) {
        setActivePane(layout == paneLayouts[0] ? 0 : 1, false);
        return true;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode >= 0) {
            resultPanes.put(requestCode, activePane);
        }
        super.startActivityForResult(intent, requestCode, options);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ThemeEditorView editor = ThemeEditorView.getInstance();
        if (editor != null) {
            editor.onActivityResult(requestCode, resultCode, data);
        }
        int pane = resultPanes.get(requestCode, activePane);
        resultPanes.delete(requestCode);
        if (paneLayouts[pane] != null && paneLayouts[pane].getLastFragment() != null) {
            paneLayouts[pane].getLastFragment().onActivityResultFragment(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (!checkPermissionsResult(requestCode, permissions, grants)) {
            return;
        }
        if (paneLayouts[activePane] != null && paneLayouts[activePane].getLastFragment() != null) {
            paneLayouts[activePane].getLastFragment().onRequestPermissionsResultFragment(requestCode, permissions, grants);
        }
        VoIPFragment.onRequestPermissionsResult(requestCode, permissions, grants);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.activityPermissionsGranted, requestCode, permissions, grants);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (paneLayouts[activePane] != null) {
            paneLayouts[activePane].onUserLeaveHint();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        for (INavigationLayout layout : paneLayouts) {
            if (layout != null) {
                layout.onLowMemory();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appDidLogout) {
            finish();
        } else if (id == NotificationCenter.didSetPasscode) {
            // PasscodeView emits this before clearing isWaitingForPasscodeEnter on successful
            // authentication. Treat it only as a security-setting change, never as a lock request.
            updateSecureFlag();
        } else if (id == NotificationCenter.passcodeDismissed) {
            // Other windows dismiss PasscodeView without its animated onHidden callback.
            // Post until all notification observers have updated their overlay visibility.
            root.post(() -> {
                if (resumed) {
                    revealUnlockedContent();
                }
            });
        } else if (id == NotificationCenter.screenStateChanged) {
            if (!ApplicationLoader.isScreenOn) {
                scheduleLock();
            } else if (resumed && shouldLock()) {
                showPasscode();
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            themeRebuildPending = true;
            applyPendingTheme();
        }
    }

    private final class PaneContainer extends ViewGroup {
        private boolean dualPane;

        PaneContainer(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_divider));
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            dualPane = FoldegramPaneGeometry.isDualPane(width, AndroidUtilities.dp(DUAL_PANE_MIN_WIDTH_DP));
            setMeasuredDimension(width, height);
            int divider = dualPane ? AndroidUtilities.dp(1) : 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.setVisibility(dualPane || i == activePane ? VISIBLE : GONE);
                int childWidth = FoldegramPaneGeometry.width(width, AndroidUtilities.dp(DUAL_PANE_MIN_WIDTH_DP), divider, i);
                child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    int position = FoldegramPaneGeometry.left(getMeasuredWidth(), AndroidUtilities.dp(DUAL_PANE_MIN_WIDTH_DP), AndroidUtilities.dp(1), i);
                    child.layout(position, 0, position + child.getMeasuredWidth(), getMeasuredHeight());
                }
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dualPane) {
                setActivePane(event.getX() < getChildAt(0).getRight() ? 0 : 1, false);
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
