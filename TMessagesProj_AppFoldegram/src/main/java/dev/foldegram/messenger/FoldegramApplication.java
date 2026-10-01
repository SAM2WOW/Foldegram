package dev.foldegram.messenger;

import org.telegram.messenger.ApplicationLoader;

/** Separate Android identity with no upstream updater, SMS jobs, or vendor configuration. */
public final class FoldegramApplication extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    protected boolean isStandalone() {
        // Uses Telegram's direct-distribution behavior without its standalone app module.
        return true;
    }
}
