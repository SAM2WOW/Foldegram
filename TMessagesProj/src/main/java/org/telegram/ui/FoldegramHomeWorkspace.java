/* Foldegram continuous home workspace. GNU GPL v2 or later. */
package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.view.ViewConfiguration;
import android.widget.TextView;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterView;
import java.util.ArrayList;
import java.util.UUID;

/** Owns geometry, focus and the additional stack, never copies/recreates the home list or first chat. */
final class FoldegramHomeWorkspace {
    private static final String MIME = "application/vnd.foldegram.home-conversation";
    private final LaunchActivity host;
    private final ViewGroup parent;
    final INavigationLayout list, first, second;
    private final LinearLayout rail;
    private final TextView[] buttons = new TextView[4];
    private final ImageView crane;
    private final View drop;
    private float downX, downY;
    private boolean edgeSwipe;
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean two, expanded, choosing, destroyed, listFocused;
    private Bundle pendingRestore;
    private int active, hovered = -1;
    private ValueAnimator animator;
    private float progress = 1;
    private int[] previous = new int[6];
    private FoldegramHomeGeometry geometry;
    private Drag drag;
    private DialogsActivity boundDialogs;

    private static final class Drag {
        final String token = UUID.randomUUID().toString();
        final long dialog, user, start = SystemClock.elapsedRealtime();
        final int account;
        final boolean expanded;
        boolean used;
        Drag(long dialog, int account, boolean expanded) {
            this.dialog = dialog; this.account = account; this.expanded = expanded;
            user = UserConfig.getInstance(account).getClientUserId();
        }
    }

    FoldegramHomeWorkspace(LaunchActivity host, ViewGroup parent, DrawerLayoutContainer drawer,
                          INavigationLayout list, INavigationLayout first, int insertion) {
        this.host = host; this.parent = parent; this.list = list; this.first = first;
        second = INavigationLayout.newLayout(host, false);
        second.setFragmentStack(new ArrayList<>());
        second.setDelegate(host);
        second.setDrawerLayoutContainer(drawer);
        first.setFragmentStackChangedListener(() -> {
            listFocused = false;
            parent.requestLayout();
        });
        second.setFragmentStackChangedListener(parent::requestLayout);
        // Keep native non-bubble status-bar participation. The existing home draws edge to edge.
        parent.addView(second.getView(), insertion);
        rail = new LinearLayout(host);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        parent.addView(rail, insertion + 1);
        for (int i = 0; i < buttons.length; i++) {
            final int index = i;
            TextView button = buttons[i] = new TextView(host);
            button.setGravity(Gravity.CENTER); button.setTextSize(18);
            button.setBackground(Theme.AdaptiveRipple.circle());
            rail.addView(button, new LinearLayout.LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48)));
            button.setOnClickListener(v -> {
                if (!allowed()) return;
                if (index == 0) toggleList();
                else if (index == 3) closeSecond();
                else { focus(index - 1); if (expanded) toggleList(); }
            });
        }
        // A 48dp target centered in the 56dp rail; optical correction only, artwork unchanged.
        buttons[0].setVisibility(View.GONE);
        crane = new ImageView(host);
        crane.setImageResource(R.drawable.foldegram_crane_mark);
        crane.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crane.setPadding(AndroidUtilities.dp(11), AndroidUtilities.dp(11), AndroidUtilities.dp(11), AndroidUtilities.dp(11));
        crane.setTranslationX(AndroidUtilities.dp(26) * (32f - 29.7975f) / 64f);
        crane.setTranslationY(AndroidUtilities.dp(26) * (32f - 30.866f) / 64f);
        crane.setContentDescription(host.getString(R.string.FoldegramConversations));
        crane.setBackground(Theme.AdaptiveRipple.circle());
        crane.setOnClickListener(v -> { if (allowed()) toggleList(); });
        rail.addView(crane, 0, new LinearLayout.LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48)));
        buttons[3].setText("×"); buttons[3].setContentDescription(host.getString(R.string.FoldegramCloseTwoChats));
        drop = new View(host) {
            @Override protected void onDraw(Canvas canvas) {
                if (hovered < 0) return;
                View target = pane(hovered).getView();
                glow.setStyle(Paint.Style.STROKE);
                for (int pass = 2; pass >= 0; pass--) {
                    glow.setStrokeWidth(AndroidUtilities.dp(pass == 0 ? 2 : 4 + pass * 2));
                    glow.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), pass == 0 ? .85f : .1f));
                    float pad = AndroidUtilities.dp(4);
                    canvas.drawRoundRect(target.getLeft() + pad, target.getTop() + pad,
                            target.getRight() - pad, target.getBottom() - pad,
                            AndroidUtilities.dp(12), AndroidUtilities.dp(12), glow);
                }
            }
        };
        drop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        drop.setOnDragListener((v, e) -> onDrag(e));
        drop.setVisibility(View.GONE);
        parent.addView(drop, insertion + 2);
        for (INavigationLayout layout : new INavigationLayout[]{first, second}) {
            layout.getView().setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    // Rounded corners begin below the status area; its background remains unbroken.
                    outline.setRoundRect(0, -AndroidUtilities.dp(12), view.getWidth(), view.getHeight(), AndroidUtilities.dp(12));
                }
            });
            layout.getView().setClipToOutline(true);
        }
    }

    boolean isTwo() { return two; }
    boolean owns(ChatActivity chat) { return chat != null && (chat.getParentLayout() == first || chat.getParentLayout() == second); }
    private INavigationLayout pane(int i) { return i == 0 ? first : second; }
    INavigationLayout current() {
        if (listFocused || choosing || first.getFragmentStack().isEmpty()) return list;
        return pane(two ? active : 0);
    }
    private boolean allowed() {
        return !destroyed && LaunchActivity.isResumed && parent.getWindowVisibility() == View.VISIBLE
                && !host.isFinishing() && !SharedConfig.appLocked
                && !SharedConfig.isWaitingForPasscodeEnter && !host.foldegramHasModal();
    }
    private boolean compositionSafe(INavigationLayout layout) {
        BaseFragment top = layout.getLastFragment();
        if (!(top instanceof ChatActivity)) return false;
        ChatActivity chat = (ChatActivity) top;
        ChatActivityEnterView input = chat.getChatActivityEnterView();
        return input != null && !chat.isEditingMessageMedia() && !BaseFragment.hasSheets(chat)
                && !input.isEditingMessage() && !input.isRecordingAudioVideo() && !input.hasAudioToSend()
                && !chat.hasFoldegramPendingForward();
    }
    void open(ChatActivity source) {
        if (!allowed() || !owns(source) || !compositionSafe(source.getParentLayout())) return;
        if (two) { focus(source.getParentLayout() == second ? 1 : 0); toggleList(); return; }
        choosing = true; expanded = true; listFocused = true;
        AndroidUtilities.hideKeyboard(host.getCurrentFocus());
        animateLayout();
    }
    private void toggleList() {
        expanded = !expanded;
        listFocused = expanded;
        if (!expanded) choosing = false;
        AndroidUtilities.hideKeyboard(host.getCurrentFocus());
        animateLayout();
    }
    private boolean sameDialog(ChatActivity a, INavigationLayout other) {
        BaseFragment b = other.getLastFragment();
        return b instanceof ChatActivity && a.getDialogId() == ((ChatActivity) b).getDialogId();
    }
    /** Called before LaunchActivity's tablet routing can clear an existing stack. null = normal routing. */
    Boolean present(INavigationLayout origin, INavigationLayout.NavigationParams params) {
        if (!(params.fragment instanceof ChatActivity)) {
            if (origin == second) return true;
            return null;
        }
        if (!two && !choosing && origin != second) return null;
        if (!allowed()) return false;
        ChatActivity chat = (ChatActivity) params.fragment;
        int target = choosing ? 1 : origin == second ? 1 : origin == first ? 0 : active;
        INavigationLayout destination = pane(target);
        if (chat.getCurrentAccount() != UserConfig.selectedAccount || sameDialog(chat, pane(1 - target))) {
            Toast.makeText(host, R.string.FoldegramChooseDifferentChat, Toast.LENGTH_SHORT).show(); return false;
        }
        if (!destination.getFragmentStack().isEmpty() && !compositionSafe(destination)) {
            Toast.makeText(host, R.string.FoldegramFinishComposition, Toast.LENGTH_LONG).show(); return false;
        }
        boolean opened = destination.presentFragment(params.setCheckPresentFromDelegate(false));
        if (opened) {
            two = true; choosing = false; expanded = false; listFocused = false; active = target;
            animateLayout(); syncFocus();
        }
        return false;
    }
    Boolean close(INavigationLayout layout) {
        if (layout != first && layout != second) return null;
        if (layout.getFragmentStack().size() > 1) return true;
        if (two) { closeSecond(); return false; }
        return null;
    }
    private void closeSecond() {
        if (!two || !allowed()) return;
        if (!compositionSafe(second)) {
            Toast.makeText(host, R.string.FoldegramFinishComposition, Toast.LENGTH_LONG).show(); return;
        }
        saveDraft(second);
        // Explicitly closing the extra pane ends only its stack. The home and first pane stay mounted.
        second.removeAllFragments(); two = false; active = 0; expanded = false; choosing = false; listFocused = false;
        cancelDrag(); animateLayout(); syncFocus();
    }
    boolean back(boolean invoked) {
        if (!two && !choosing && !expanded) return false;
        if (!invoked) return true;
        if (drag != null) { cancelDrag(); return true; }
        if (expanded || choosing) { expanded = choosing = listFocused = false; animateLayout(); return true; }
        pane(active).onBackPressed();
        return true;
    }
    boolean intercept(MotionEvent event) {
        if (!two || expanded || !allowed()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY();
            androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(parent);
            int edge = insets == null ? AndroidUtilities.dp(24)
                    : insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemGestures()).left;
            edgeSwipe = downX >= edge + AndroidUtilities.dp(4) && downX < rail.getRight();
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && edgeSwipe) {
            float dx = event.getX() - downX;
            if (dx > ViewConfiguration.get(host).getScaledTouchSlop() && dx > Math.abs(event.getY() - downY) * 1.5f) {
                edgeSwipe = false; toggleList(); return true;
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) edgeSwipe = false;
        return false;
    }
    void touch(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN || !allowed()) return;
        View listView = list.getView();
        if (listView.isShown() && event.getX() >= listView.getLeft() && event.getX() < listView.getRight()) {
            listFocused = true; syncFocus(); return;
        }
        for (int i = 0; i < (two ? 2 : 1); i++) {
            View v = pane(i).getView();
            if (v.isShown() && v.getWidth() > 0 && event.getX() >= v.getLeft() && event.getX() < v.getRight()) {
                if (active != i || listFocused) focus(i);
            }
        }
    }
    private void focus(int target) {
        if (target == 1 && !two) return;
        if (active != target) {
            saveDraft(pane(active));
            AndroidUtilities.hideKeyboard(host.getCurrentFocus());
            if (host.getCurrentFocus() != null) host.getCurrentFocus().clearFocus();
        }
        active = target; listFocused = false; syncFocus(); parent.requestLayout();
        host.checkSystemBarColors(true, true, true);
    }
    private void saveDraft(INavigationLayout layout) {
        if (layout.getLastFragment() instanceof ChatActivity) ((ChatActivity) layout.getLastFragment()).saveDraft();
    }
    void syncFocus() {
        if (pendingRestore != null && allowed()) {
            Bundle state = pendingRestore; pendingRestore = null;
            restoreNow(state);
        }
        for (int i = 0; i < 2; i++) {
            INavigationLayout layout = pane(i);
            BaseFragment top = layout.getLastFragment();
            if (top == null) continue;
            boolean resume = allowed() && i == active && !listFocused
                    && layout.getView().isShown() && layout.getView().getWidth() > 0;
            if (resume && top.isPaused()) layout.onResume();
            else if (!resume && !top.isPaused()) layout.onPause();
        }
    }
    boolean activate(ChatActivity chat) {
        if (!allowed() || !owns(chat) || !chat.getFragmentView().isShown()) return false;
        focus(chat.getParentLayout() == second ? 1 : 0); return !chat.isPaused();
    }
    boolean canDragMessage(ChatActivity chat) {
        return allowed() && two && !expanded && owns(chat) && geometry != null
                && geometry.firstWidth > 0 && geometry.secondWidth > 0;
    }
    private void bindList() {
        BaseFragment f = list.getLastFragment();
        DialogsActivity dialogs = f instanceof MainTabsActivity ? ((MainTabsActivity) f).getDialogsActivity()
                : f instanceof DialogsActivity ? (DialogsActivity) f : null;
        if (dialogs == boundDialogs) return;
        if (boundDialogs != null) boundDialogs.setFoldegramConversationDragListener(null);
        boundDialogs = dialogs;
        if (dialogs != null) dialogs.setFoldegramConversationDragListener(this::startDrag);
    }
    private boolean startDrag(View row, long dialog) {
        if (!two || !allowed() || drag != null) return false;
        int account = boundDialogs.getCurrentAccount();
        if (DialogObject.isChatDialog(dialog) && ChatObject.isForum(MessagesController.getInstance(account).getChat(-dialog))) {
            Toast.makeText(host, R.string.FoldegramDragForumHint, Toast.LENGTH_SHORT).show(); return true;
        }
        drag = new Drag(dialog, account, expanded);
        drop.setVisibility(View.VISIBLE);
        boolean started;
        try { started = row.startDragAndDrop(new ClipData("Foldegram conversation", new String[]{MIME},
                new ClipData.Item(drag.token)), new View.DragShadowBuilder(row), drag, 0); }
        catch (RuntimeException e) { started = false; }
        if (!started) { cancelDrag(); return true; }
        expanded = false; animateLayout(); return true;
    }
    private boolean onDrag(DragEvent event) {
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) { cancelDrag(); return true; }
        if (!allowed() || drag == null || drag.used || event.getLocalState() != drag
                || drag.account != UserConfig.selectedAccount || !UserConfig.isValidAccount(drag.account)
                || drag.user != UserConfig.getInstance(drag.account).getClientUserId()
                || SystemClock.elapsedRealtime() - drag.start > 120000) return false;
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
            return event.getClipDescription() != null && event.getClipDescription().hasMimeType(MIME);
        int target = -1;
        for (int i = 0; i < 2; i++) {
            View v = pane(i).getView();
            if (v.isShown() && v.getWidth() > 0 && event.getX() >= v.getLeft() && event.getX() < v.getRight()) target = i;
        }
        hovered = event.getAction() == DragEvent.ACTION_DRAG_EXITED ? -1 : target; drop.invalidate();
        if (event.getAction() != DragEvent.ACTION_DROP) return true;
        ClipData clip = event.getClipData();
        if (target < 0 || clip == null || clip.getItemCount() != 1 || clip.getItemAt(0).getText() == null
                || !drag.token.contentEquals(clip.getItemAt(0).getText()) || !compositionSafe(pane(target))) return false;
        Bundle args = FoldegramChatWindowActivity.argumentsForDialog(drag.dialog, 0);
        if (!MessagesController.getInstance(drag.account).checkCanOpenChat(args, boundDialogs)) return false;
        ChatActivity chat = new ChatActivity(args); chat.setCurrentAccount(drag.account);
        if (sameDialog(chat, pane(1 - target))) return false;
        boolean opened = pane(target).presentFragment(new INavigationLayout.NavigationParams(chat).setCheckPresentFromDelegate(false));
        if (opened) { drag.used = true; expanded = false; focus(target); }
        return opened;
    }
    private void cancelDrag() {
        if (drag != null && !drag.used) { expanded = drag.expanded; listFocused = expanded; }
        drag = null; hovered = -1; drop.setVisibility(View.GONE); parent.requestLayout();
    }
    void save(Bundle out) {
        if (!two) return;
        BaseFragment f = second.getLastFragment();
        if (!(f instanceof ChatActivity)) return;
        ChatActivity chat = (ChatActivity) f;
        saveDraft(second);
        Bundle state = new Bundle();
        state.putInt("account", chat.getCurrentAccount());
        state.putLong("user", UserConfig.getInstance(chat.getCurrentAccount()).getClientUserId());
        state.putBundle("args", new Bundle(chat.getArguments()));
        state.putLong("topic", chat.isTopic ? chat.getTopicId() : 0);
        Bundle self = new Bundle(); chat.saveSelfArgs(self); state.putBundle("self", self);
        state.putInt("active", active); state.putBoolean("expanded", expanded);
        out.putBundle("foldegram.home", state);
    }
    void restore(Bundle state) { pendingRestore = state == null ? null : state.getBundle("foldegram.home"); }
    private void restoreNow(Bundle state) {
        int account = state.getInt("account", -1);
        if (two || account != UserConfig.selectedAccount || !UserConfig.isValidAccount(account)
                || UserConfig.getInstance(account).getClientUserId() != state.getLong("user")
                || state.getBundle("args") == null || first.getFragmentStack().isEmpty()) return;
        ChatActivity chat = new ChatActivity(new Bundle(state.getBundle("args")));
        chat.setCurrentAccount(account);
        long topic = state.getLong("topic");
        if (topic != 0) {
            long dialog = -chat.getArguments().getLong("chat_id");
            if (MessagesController.getInstance(account).getTopicsController().findTopic(-dialog, topic) == null) return;
            org.telegram.ui.Components.Forum.ForumUtilities.applyTopic(chat,
                    org.telegram.messenger.MessagesStorage.TopicKey.of(dialog, topic));
        }
        if (sameDialog(chat, first) || !MessagesController.getInstance(account).checkCanOpenChat(chat.getArguments(), first.getLastFragment())) return;
        if (!second.addFragmentToStack(chat)) return;
        chat.restoreSelfArgs(state.getBundle("self"));
        second.showLastFragment();
        two = true; active = state.getInt("active", 0) == 1 ? 1 : 0; expanded = state.getBoolean("expanded"); listFocused = expanded;
        parent.requestLayout();
    }
    void pause() { cancelDrag(); saveDraft(first); saveDraft(second); second.onPause(); }
    void reset() {
        cancelDrag(); if (animator != null) animator.cancel(); second.removeAllFragments();
        two = expanded = choosing = listFocused = false; pendingRestore = null; active = 0; parent.requestLayout();
    }
    void destroy() { pause(); reset(); destroyed = true; if (boundDialogs != null) boundDialogs.setFoldegramConversationDragListener(null); }
    private void animateLayout() {
        if (animator != null) animator.cancel();
        previous = new int[]{list.getView().getWidth(), rail.getWidth(), first.getView().getLeft(), first.getView().getWidth(),
                second.getView().getLeft(), second.getView().getWidth()};
        if (two && previous[5] == 0) previous[4] = parent.getWidth();
        progress = SharedConfig.animationsEnabled() ? 0 : 1;
        if (progress == 0) {
            animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(220);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); parent.requestLayout(); });
            animator.start();
        }
        parent.requestLayout();
    }
    private int lerp(int index, int end) { return Math.round(previous[index] + (end - previous[index]) * progress); }
    void measure(int width, int height, int topInset, int bottomInset) {
        bindList();
        parent.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        geometry = new FoldegramHomeGeometry(width, AndroidUtilities.dp(600), AndroidUtilities.dp(56),
                AndroidUtilities.dp(320), AndroidUtilities.dp(8), two, expanded || choosing, !first.getFragmentStack().isEmpty(), active);
        measure(list.getView(), lerp(0, geometry.list), height);
        measure(rail, lerp(1, geometry.rail), height);
        measure(first.getView(), lerp(3, geometry.firstWidth), height);
        measure(second.getView(), lerp(5, geometry.secondWidth), height);
        measure(drop, width, height);
        rail.setPadding(0, topInset, 0, bottomInset);
        rail.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        crane.setColorFilter(Theme.getColor(Theme.key_telegram_color_dialogsLogo));
        for (TextView b : buttons) b.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        for (int i = 0; i < 2; i++) {
            BaseFragment f = pane(i).getLastFragment();
            String name = f instanceof ChatActivity ? DialogObject.getName(f.getCurrentAccount(), ((ChatActivity) f).getDialogId()) : "";
            buttons[i + 1].setText(name == null || name.isEmpty() ? "·" : name.substring(0, name.offsetByCodePoints(0, 1)));
            buttons[i + 1].setContentDescription(host.getString(i == 0 ? R.string.FoldegramLeftPane : R.string.FoldegramRightPane, name));
            buttons[i + 1].setSelected(i == active);
        }
    }
    private void measure(View v, int w, int h) {
        if (v != drop) {
            // INVISIBLE preserves attached views, scroll, search and draft state; no fragment reconstruction.
            v.setVisibility(w > 0 ? View.VISIBLE : View.INVISIBLE);
            v.setImportantForAccessibility(w > 0 ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        v.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, w), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    }
    void layout(int width, int height) {
        if (geometry == null) return;
        list.getView().layout(rail.getMeasuredWidth(), 0, rail.getMeasuredWidth() + list.getView().getMeasuredWidth(), height);
        rail.layout(0, 0, rail.getMeasuredWidth(), height);
        int x = lerp(2, geometry.firstX);
        first.getView().layout(x, 0, x + first.getView().getMeasuredWidth(), height);
        x = lerp(4, geometry.secondX);
        second.getView().layout(x, 0, x + second.getView().getMeasuredWidth(), height);
        drop.layout(0, 0, width, height);
        parent.post(this::syncFocus);
    }
}
