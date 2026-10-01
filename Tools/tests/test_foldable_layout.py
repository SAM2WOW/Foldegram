#!/usr/bin/env python3
"""Headless regression checks for Foldegram's fold/window reconciliation.

Runs the production Java method bodies with small UI/resource test doubles using
only Python 3 and a JDK. This checks state transitions, not Android rendering or
framework callback delivery. Device/emulator rotation, fold and resize tests are
still required before a release.
"""

from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
LAUNCH = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java").read_text()
UTILITIES = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java").read_text()


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


HARNESS = r"""
import java.util.ArrayList;
import java.util.List;

public class FoldableLayoutRegressionTest {
    static class Configuration {
        static final int ORIENTATION_PORTRAIT = 1, ORIENTATION_LANDSCAPE = 2;
        int orientation = ORIENTATION_PORTRAIT;
    }
    static class Resources {
        boolean tablet;
        Configuration configuration = new Configuration();
        boolean getBoolean(int id) { return tablet; }
        Configuration getConfiguration() { return configuration; }
    }
    static class Context {
        Resources resources = new Resources();
        Resources getResources() { return resources; }
    }
    static class R { static class bool { static int isTablet; } }
    static class ApplicationLoader { static Context applicationContext = new Context(); }
    static class SharedConfig {
        static boolean forceDisableTabletMode;
        static void updateTabletConfig() {}
    }
    static class Build {
        static class VERSION { static int SDK_INT = 36; }
        static class VERSION_CODES { static int N = 24; }
    }
    static class View {
        static final int VISIBLE = 0, GONE = 8;
        int visibility = VISIBLE, requests;
        int getVisibility() { return visibility; }
        void setVisibility(int v) { visibility = v; }
        void requestLayout() { requests++; }
    }
    static class ViewCompat { static void requestApplyInsets(View v) {} }
    static class PasscodeDialog { View passcodeView = new View(); }
    static class BaseFragment {
        void onPause() {}
        void onFragmentDestroy() {}
        void setParentLayout(Object v) {}
    }
    static class DialogsActivity extends BaseFragment {
        boolean isMainDialogList() { return true; }
        boolean isArchive() { return false; }
        boolean isCommunity() { return false; }
        void setOpenedDialogId(long dialog, long topic) {}
    }
    static class MainTabsActivity extends BaseFragment {
        DialogsActivity getDialogsActivity() { return new DialogsActivity(); }
    }
    static class ChatActivity extends BaseFragment {
        void setIgnoreAttachOnPause(boolean ignored) {}
        boolean isInScheduleMode() { return false; }
        long getDialogId() { return 1; }
        long getTopicId() { return 0; }
    }
    static class INavigationLayout {
        static final int REBUILD_FLAG_REBUILD_LAST = 1;
        final List<BaseFragment> stack;
        final View view = new View();
        int rebuilds;
        INavigationLayout(List<BaseFragment> stack) { this.stack = stack; }
        List<BaseFragment> getFragmentStack() { return stack; }
        void addFragmentToStack(BaseFragment f) { stack.add(f); }
        void rebuildFragments(int flags) { rebuilds++; }
        View getView() { return view; }
    }
    static class AndroidUtilities {
        static Boolean isTablet, wasTablet;
        static boolean isInMultiwindow, smallTablet;
        static int leftBaseline, displayRefreshes;
        static boolean isSmallTablet() { return smallTablet; }
        static void checkDisplaySize(Context c, Configuration configuration) { displayRefreshes++; }
        // ACTUAL_UTILITIES_METHODS
    }
    static class LaunchActivity extends Context {
        boolean tabletLayout, tabletFullSize, multiWindow, destroyed, finishing;
        int setups;
        final ArrayList<BaseFragment> mainFragmentsStack = new ArrayList<>();
        final ArrayList<BaseFragment> rightFragmentsStack = new ArrayList<>();
        final ArrayList<BaseFragment> layerFragmentsStack = new ArrayList<>();
        INavigationLayout actionBarLayout = new INavigationLayout(mainFragmentsStack);
        INavigationLayout rightActionBarLayout = new INavigationLayout(rightFragmentsStack);
        INavigationLayout layersActionBarLayout = new INavigationLayout(layerFragmentsStack);
        View backgroundTablet = new View(), drawerLayoutContainer = new View();
        PasscodeDialog passcodeDialog;
        void setupActionBarLayout() { setups++; tabletLayout = AndroidUtilities.isTablet(); }
        void logWindowLayout(String reason) {}
        boolean isInMultiWindowMode() { return multiWindow; }
        boolean isDestroyed() { return destroyed; }
        boolean isFinishing() { return finishing; }
        // ACTUAL_LAUNCH_METHODS
    }

    static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
    static LaunchActivity freshPhone() {
        SharedConfig.forceDisableTabletMode = false;
        ApplicationLoader.applicationContext.resources.tablet = false;
        AndroidUtilities.isTablet = null;
        AndroidUtilities.wasTablet = null;
        AndroidUtilities.smallTablet = false;
        AndroidUtilities.isInMultiwindow = false;
        AndroidUtilities.displayRefreshes = 0;
        LaunchActivity a = new LaunchActivity();
        a.mainFragmentsStack.add(new MainTabsActivity());
        a.mainFragmentsStack.add(new ChatActivity());
        a.mainFragmentsStack.add(new BaseFragment());
        return a;
    }
    static void expectStackSizes(LaunchActivity a, int main, int right, int layers) {
        check(a.mainFragmentsStack.size() == main, "main stack size");
        check(a.rightFragmentsStack.size() == right, "right stack size");
        check(a.layerFragmentsStack.size() == layers, "layer stack size");
    }
    static void unfoldWithLostGlobalSnapshot() {
        LaunchActivity a = freshPhone();
        a.resources.tablet = true;
        AndroidUtilities.resetTabletFlag(a);
        // Another callback consumed the global snapshot before this activity saw it.
        AndroidUtilities.resetWasTabletFlag();
        a.invalidateTabletMode();
        check(a.tabletLayout && a.setups == 1, "lost snapshot must not suppress hierarchy migration");
        expectStackSizes(a, 1, 1, 1);
    }
    static void repeatedFoldUnfoldPreservesFragments() {
        LaunchActivity a = freshPhone();
        List<BaseFragment> original = new ArrayList<>(a.mainFragmentsStack);
        for (int i = 0; i < 20; i++) {
            a.resources.tablet = true;
            a.refreshWindowLayout("testUnfold");
            expectStackSizes(a, 1, 1, 1);
            int setups = a.setups;
            a.refreshWindowLayout("duplicateCallback");
            check(a.setups == setups, "same mode must not rebuild the hierarchy");
            a.resources.tablet = false;
            a.refreshWindowLayout("testFold");
            expectStackSizes(a, 3, 0, 0);
            check(a.mainFragmentsStack.equals(original), "fold/unfold must preserve fragment identity and ordering");
        }
    }
    static void activityResourcesBeatApplicationResources() {
        LaunchActivity a = freshPhone();
        ApplicationLoader.applicationContext.resources.tablet = true;
        a.resources.tablet = false;
        a.refreshWindowLayout("narrowWindow");
        check(!AndroidUtilities.isTablet(), "tablet classification must use the activity window");
        check(!a.tabletLayout && a.setups == 0, "application tablet size must not rebuild a narrow window");
        check(AndroidUtilities.leftBaseline == 72, "phone baseline");
    }
    static void forcedPhoneModeDoesNotRebuild() {
        LaunchActivity a = freshPhone();
        SharedConfig.forceDisableTabletMode = true;
        a.resources.tablet = true;
        a.refreshWindowLayout("forcedPhone");
        check(AndroidUtilities.isTabletInternal(), "physical resource remains tablet");
        check(!AndroidUtilities.isTablet() && !a.tabletLayout && a.setups == 0, "effective phone mode must remain attached");
        expectStackSizes(a, 3, 0, 0);
    }
    static void splitScreenMovesChatAndBack() {
        LaunchActivity a = freshPhone();
        a.resources.tablet = true;
        a.refreshWindowLayout("unfold");
        BaseFragment chat = a.rightFragmentsStack.get(0);
        a.multiWindow = true;
        a.refreshWindowLayout("splitScreen");
        check(a.tabletFullSize, "multi-window uses a single full-width pane");
        expectStackSizes(a, 2, 0, 1);
        check(a.mainFragmentsStack.get(1) == chat, "chat must move to the measured main pane");
        check(a.rightActionBarLayout.view.visibility == View.GONE, "unused right pane hidden");
        a.multiWindow = false;
        a.refreshWindowLayout("leaveSplitScreen");
        check(!a.tabletFullSize, "large window restores two panes");
        expectStackSizes(a, 1, 1, 1);
        check(a.rightFragmentsStack.get(0) == chat, "same chat returns to right pane");
        check(a.rightActionBarLayout.view.visibility == View.VISIBLE, "populated right pane visible");
    }
    static void smallTabletRotationMovesChatAndBack() {
        LaunchActivity a = freshPhone();
        a.resources.tablet = true;
        AndroidUtilities.smallTablet = true;
        a.refreshWindowLayout("portrait");
        check(a.tabletFullSize, "small portrait tablet uses full width");
        expectStackSizes(a, 2, 0, 1);
        a.resources.configuration.orientation = Configuration.ORIENTATION_LANDSCAPE;
        a.refreshWindowLayout("landscape");
        check(!a.tabletFullSize, "small landscape tablet uses split panes");
        expectStackSizes(a, 1, 1, 1);
    }
    static void resumeAndDestroyedGuard() {
        LaunchActivity a = freshPhone();
        a.resources.tablet = true;
        a.refreshWindowLayout("resume");
        check(a.tabletLayout && a.drawerLayoutContainer.requests == 1, "resume must reconcile and request layout");
        check(AndroidUtilities.displayRefreshes == 1, "resume refreshes display dimensions");
        a.destroyed = true;
        a.resources.tablet = false;
        a.refreshWindowLayout("latePostedResize");
        check(a.tabletLayout && a.drawerLayoutContainer.requests == 1, "destroyed activity must ignore posted resize");
    }
    public static void main(String[] args) {
        unfoldWithLostGlobalSnapshot();
        repeatedFoldUnfoldPreservesFragments();
        activityResourcesBeatApplicationResources();
        forcedPhoneModeDoesNotRebuild();
        splitScreenMovesChatAndBack();
        smallTabletRotationMovesChatAndBack();
        resumeAndDestroyedGuard();
        System.out.println("7 production-method state-transition scenarios passed");
    }
}
"""


class FoldableLayoutTests(unittest.TestCase):
    def test_production_state_transitions(self):
        utilities_methods = [
            "public static boolean isTabletForce()",
            "public static boolean isTabletInternal()",
            "public static void resetTabletFlag()",
            "public static void resetTabletFlag(Context context)",
            "public static void resetWasTabletFlag()",
            "public static Boolean getWasTablet()",
            "public static boolean isTablet()",
        ]
        launch_methods = [
            "private void invalidateTabletMode()",
            "private void checkLayout()",
            "private void refreshWindowLayout(String reason)",
            "private void updateWindowConfiguration(Configuration configuration)",
            "private void reconcileWindowLayout(String reason)",
        ]
        source = HARNESS.replace("// ACTUAL_UTILITIES_METHODS", "\n".join(method(UTILITIES, s) for s in utilities_methods))
        source = source.replace("// ACTUAL_LAUNCH_METHODS", "\n".join(method(LAUNCH, s) for s in launch_methods))
        with tempfile.TemporaryDirectory(prefix="foldegram-layout-test-") as tmp:
            java_file = Path(tmp) / "FoldableLayoutRegressionTest.java"
            java_file.write_text(source)
            subprocess.run(["javac", str(java_file)], check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", tmp, "FoldableLayoutRegressionTest"], check=True, capture_output=True, text=True)
            self.assertIn("7 production-method state-transition scenarios passed", result.stdout)

    def test_lifecycle_calls_reconcile(self):
        self.assertIn('refreshWindowLayout("resume")', method(LAUNCH, "protected void onResume()"))
        self.assertIn('refreshWindowLayout("multiWindowChanged")', method(LAUNCH, "public void onMultiWindowModeChanged(boolean isInMultiWindowMode)"))
        config = method(LAUNCH, "public void onConfigurationChanged(Configuration newConfig)")
        self.assertLess(config.index("updateWindowConfiguration(newConfig)"), config.index('reconcileWindowLayout("configurationChanged")'))
        self.assertIn("v.post(refreshWindowLayoutRunnable)", LAUNCH)
        self.assertIn("removeCallbacks(refreshWindowLayoutRunnable)", method(LAUNCH, "protected void onDestroy()"))

    def test_measurement_does_not_migrate_or_change_pane_mode(self):
        setup = method(LAUNCH, "private void setupActionBarLayout()")
        measure = method(setup, "protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)")
        layout = method(setup, "protected void onLayout(boolean changed, int l, int t, int r, int b)")
        self.assertIn("if (!tabletFullSize)", measure)
        self.assertIn("if (!tabletFullSize)", layout)
        self.assertNotIn("tabletFullSize =", measure)
        self.assertNotIn("invalidateTabletMode()", measure)
        self.assertNotIn("checkLayout()", measure.replace("// checkLayout()", "//"))
        self.assertIn("Math.min(width, AndroidUtilities.getTabletLeftFragmentSize", measure)
        self.assertIn("Math.max(0, height - insets.top - insets.bottom - dp(16))", measure)

    def test_derived_sizes_are_invalidated(self):
        display = method(UTILITIES, "public static void checkDisplaySize(Context context, Configuration newConfiguration)")
        self.assertIn("oldWidth != displaySize.x || oldHeight != displaySize.y", display)
        self.assertLess(display.index("roundMessageSize = 0"), display.index("if (roundMessageSize == 0)"))

    def test_diagnostics_are_gated_and_content_free(self):
        log = method(LAUNCH, "private void logWindowLayout(String reason)")
        self.assertIn("if (!BuildVars.LOGS_ENABLED)", log)
        for forbidden in ("getDialogId(", "getTopicId(", "getCurrentUser(", "getIntent(", "messageText", "currentAccount"):
            self.assertNotIn(forbidden, log)


if __name__ == "__main__":
    unittest.main(verbosity=2)
