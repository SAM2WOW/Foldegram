/*
 * Foldegram's deliberately narrow drag transport. A drop never sends a message.
 * Message bodies stay in this process; external image grants live only while copying.
 */
package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentResolver;
import android.content.pm.ProviderInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class FoldegramChatDragDrop {
    private static final String MESSAGE_MIME = "application/vnd.foldegram.message-token";
    private static final int MAX_TEXT_LENGTH = 16384;
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 32L * 1024 * 1024;
    private static final long ARM_TIMEOUT_MS = 30000;
    private static final long TOKEN_TIMEOUT_MS = 120000;
    // Android permits one drag at a time. Never serialize MessageObject or put its text in ClipData.
    private static MessageDrag activeMessageDrag;

    private final ChatActivity owner;
    private long armedDialog;
    private int armedMessage;
    private long armedUntil;
    private ImageImport imageImport;
    private boolean destroyed;

    private static final class MessageDrag {
        final String token = UUID.randomUUID().toString();
        final WeakReference<ChatActivity> source;
        final MessageObject message;
        final long expires = SystemClock.elapsedRealtime() + TOKEN_TIMEOUT_MS;

        MessageDrag(ChatActivity source, MessageObject message) {
            this.source = new WeakReference<>(source);
            this.message = message;
        }
    }

    FoldegramChatDragDrop(ChatActivity owner) {
        this.owner = owner;
    }

    void arm(MessageObject message) {
        if (!owner.canDragFoldegramMessage(message)) {
            return;
        }
        armedDialog = message.getDialogId();
        armedMessage = message.getId();
        armedUntil = SystemClock.elapsedRealtime() + ARM_TIMEOUT_MS;
        owner.showFoldegramDropNotice("Touch and hold this message again, then drag it to the other chat");
    }

    boolean tryStart(View view) {
        if (Build.VERSION.SDK_INT < 24 || destroyed || !(view instanceof ChatMessageCell)) {
            return false;
        }
        MessageObject message = ((ChatMessageCell) view).getMessageObject();
        boolean armed = message != null && message.getId() == armedMessage
                && message.getDialogId() == armedDialog && SystemClock.elapsedRealtime() < armedUntil;
        armedUntil = 0;
        if (!armed || !owner.canDragFoldegramMessage(message)) {
            return false;
        }
        MessageDrag drag = new MessageDrag(owner, message);
        activeMessageDrag = drag;
        ClipData data = new ClipData("Foldegram message", new String[]{MESSAGE_MIME}, new ClipData.Item(drag.token));
        boolean started;
        try {
            started = view.startDragAndDrop(data, new View.DragShadowBuilder(view), null, View.DRAG_FLAG_GLOBAL);
        } catch (RuntimeException e) {
            FileLog.e(e);
            started = false;
        }
        if (!started) {
            activeMessageDrag = null;
            owner.showFoldegramDropNotice("Drag could not start. Try touching and holding the message again");
        }
        return started;
    }

    /** Intercept before EditText's automatic drop insertion, including for restricted chats. */
    boolean dispatch(DragEvent event) {
        if (Build.VERSION.SDK_INT < 24 || destroyed) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: {
                ClipDescription description = event.getClipDescription();
                if (description == null) return false;
                if (description.hasMimeType(MESSAGE_MIME)) {
                    return owner.canReceiveFoldegramDrag(false)
                            || activeMessageDrag != null && activeMessageDrag.source.get() == owner;
                }
                return imageImport == null && owner.canReceiveFoldegramDrag(false)
                        && (description.hasMimeType("text/plain")
                        || description.hasMimeType("text/uri-list")
                        || description.hasMimeType("image/*"));
            }
            case DragEvent.ACTION_DROP:
                return drop(event);
            case DragEvent.ACTION_DRAG_ENDED:
                if (activeMessageDrag != null && activeMessageDrag.source.get() == owner) {
                    activeMessageDrag = null;
                }
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
            case DragEvent.ACTION_DRAG_EXITED:
                return true;
            default:
                return false;
        }
    }

    private boolean drop(DragEvent event) {
        try {
            ClipData data = event.getClipData();
            if (data == null || !isSingleItem(data.getItemCount()) || imageImport != null) {
                owner.showFoldegramDropNotice("Drop one text item or image at a time");
                return false;
            }
            if (!owner.activateFoldegramDropTarget()) return false;
            ClipData.Item item = data.getItemAt(0);
            if (data.getDescription().hasMimeType(MESSAGE_MIME)) {
                MessageDrag drag = activeMessageDrag;
                if (drag == null || item.getText() == null || !drag.token.contentEquals(item.getText())
                        || SystemClock.elapsedRealtime() >= drag.expires) return false;
                ChatActivity source = drag.source.get();
                if (source == null || source == owner || !source.canDragFoldegramMessage(drag.message)) return false;
                activeMessageDrag = null; // A capability is single-use, even when the destination declines it.
                return owner.stageFoldegramForward(drag.message);
            }
            if (item.getIntent() != null || !owner.canAcceptFoldegramDrop(false)) return false;
            Uri uri = item.getUri();
            if (uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                if (!owner.canAcceptFoldegramDrop(true) || !owner.canStageFoldegramMedia()) return false;
                // Never allow another app to cause us to export our own private content provider.
                ProviderInfo provider = owner.getParentActivity().getPackageManager().resolveContentProvider(uri.getAuthority(), 0);
                if (provider == null || provider.applicationInfo == null || provider.applicationInfo.uid == Process.myUid()) return false;
                DragAndDropPermissions permissions = owner.getParentActivity().requestDragAndDropPermissions(event);
                if (permissions == null) {
                    owner.showFoldegramDropNotice("This app did not grant access to the image");
                    return false;
                }
                imageImport = new ImageImport(uri, permissions);
                try {
                    imageImport.start();
                } catch (RuntimeException e) {
                    cancelImport();
                    throw e;
                }
                return true;
            }
            // No URI coercion, HTML, Intent execution, remote downloads, or file:// access.
            CharSequence text = item.getText();
            if (!isSupportedTextItem(data.getDescription().hasMimeType("text/plain"), text, uri == null ? null : uri.getScheme())) {
                owner.showFoldegramDropNotice("Supported drops: plain text or a JPEG, PNG, or WebP image");
                return false;
            }
            return owner.stageFoldegramText(text.toString(), MAX_TEXT_LENGTH);
        } catch (RuntimeException e) {
            FileLog.e(e);
            owner.showFoldegramDropNotice("This item could not be opened. Your draft is unchanged");
            return false;
        }
    }

    void cancelImport() {
        if (imageImport != null) {
            imageImport.cancel();
            imageImport = null;
        }
    }

    void destroy() {
        destroyed = true;
        armedUntil = 0;
        if (activeMessageDrag != null && activeMessageDrag.source.get() == owner) activeMessageDrag = null;
        cancelImport();
    }

    private final class ImageImport {
        private final Uri uri;
        private final DragAndDropPermissions permissions;
        private final CancellationSignal cancellation = new CancellationSignal();
        private final AtomicBoolean released = new AtomicBoolean();
        private volatile ParcelFileDescriptor descriptor;
        private volatile boolean cancelled;
        private final Runnable timeout = () -> {
            cancel();
            if (imageImport == this) {
                imageImport = null;
                owner.showFoldegramDropNotice("Image import timed out. Your draft is unchanged");
            }
        };

        ImageImport(Uri uri, DragAndDropPermissions permissions) {
            this.uri = uri;
            this.permissions = permissions;
        }

        void start() {
            AndroidUtilities.runOnUIThread(timeout, 30000);
            Thread thread = new Thread(() -> {
                File result = null;
                try {
                    result = copyImage();
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    AndroidUtilities.cancelRunOnUIThread(timeout);
                    release(); // Preview uses only the private copy, never the external URI grant.
                }
                final File copy = result;
                AndroidUtilities.runOnUIThread(() -> {
                    if (imageImport == this) imageImport = null;
                    if (cancelled || destroyed || !owner.canAcceptFoldegramDrop(true)) {
                        delete(copy);
                    } else if (copy == null) {
                        owner.showFoldegramDropNotice("Could not open the image. Use JPEG, PNG, or WebP up to 20 MB");
                    } else {
                        owner.stageFoldegramImage(copy);
                    }
                });
            }, "FoldegramImageDrop");
            thread.start();
        }

        private File copyImage() throws IOException {
            ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
            String mime = resolver.getType(uri);
            if (!isSupportedImage(mime)) throw new IOException("Unsupported image MIME type");
            File directory = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create image cache");
            File copy = File.createTempFile("foldegram_drop_", ".image", directory);
            boolean success = false;
            try {
                descriptor = resolver.openFileDescriptor(uri, "r", cancellation);
                if (descriptor == null || cancelled || AndroidUtilities.isInternalUri(descriptor.getFd())) throw new IOException("Image access refused");
                try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor); FileOutputStream out = new FileOutputStream(copy)) {
                    byte[] buffer = new byte[32768];
                    long total = 0;
                    long started = SystemClock.elapsedRealtime();
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        total += count;
                        if (!isWithinImageCopyLimit(total, SystemClock.elapsedRealtime() - started, cancelled)) throw new IOException("Image import limit exceeded");
                        out.write(buffer, 0, count);
                    }
                    if (total == 0) throw new IOException("Empty image");
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(copy.getAbsolutePath(), bounds);
                if (!isSupportedImage(bounds.outMimeType) || !isSupportedImageDimensions(bounds.outWidth, bounds.outHeight)) throw new IOException("Invalid image dimensions");
                String suffix = "image/png".equals(bounds.outMimeType) ? ".png" : "image/webp".equals(bounds.outMimeType) ? ".webp" : ".jpg";
                File typedCopy = new File(directory, copy.getName() + suffix);
                if (!copy.renameTo(typedCopy)) throw new IOException("Cannot finalize image copy");
                copy = typedCopy;
                if (cancelled) throw new IOException("Image import cancelled");
                success = true;
                return copy;
            } finally {
                closeDescriptor();
                if (!success) delete(copy);
            }
        }

        void cancel() {
            AndroidUtilities.cancelRunOnUIThread(timeout);
            cancelled = true;
            try {
                cancellation.cancel();
            } finally {
                closeDescriptor();
                release();
            }
        }

        private void closeDescriptor() {
            ParcelFileDescriptor current = descriptor;
            descriptor = null;
            if (current != null) {
                try { current.close(); } catch (IOException e) { FileLog.e(e); }
            }
        }

        private void release() {
            if (released.compareAndSet(false, true)) {
                try { permissions.release(); } catch (RuntimeException e) { FileLog.e(e); }
            }
        }
    }

    private static boolean isWithinImageCopyLimit(long bytes, long elapsed, boolean cancelled) {
        return !cancelled && bytes >= 0 && bytes <= MAX_IMAGE_BYTES && elapsed <= 30000;
    }

    private static boolean isSingleItem(int count) {
        return count == 1;
    }

    private static boolean isSupportedTextItem(boolean plainText, CharSequence text, String uriScheme) {
        return plainText && text != null && text.length() > 0 && text.length() <= MAX_TEXT_LENGTH
                && (uriScheme == null || "https".equals(uriScheme) || "http".equals(uriScheme));
    }

    private static boolean isSupportedImageDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= 16384 && height <= 16384
                && (long) width * height <= MAX_IMAGE_PIXELS;
    }

    private static boolean isSupportedImage(String mime) {
        return "image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime);
    }

    static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) FileLog.d("Could not remove cancelled drop image");
    }
}
