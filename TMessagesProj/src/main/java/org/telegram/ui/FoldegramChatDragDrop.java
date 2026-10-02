/*
 * Foldegram's bounded in-process and external drag transport. A drop never sends a message.
 * Message bodies stay in this process; external file grants live only while copying.
 */
package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import java.io.InputStream;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import org.telegram.ui.ActionBar.Theme;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

final class FoldegramChatDragDrop {
    private static final String MESSAGE_MIME = "application/vnd.foldegram.message-token";
    private static final int MAX_TEXT_LENGTH = 16384;
    private static final long MAX_IMAGE_BYTES = 100L * 1024 * 1024;
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
        final ArrayList<MessageObject> messages;
        WeakReference<View> sourceView;
        int heldMessageId;
        boolean releasedInSource;
        final long expires = SystemClock.elapsedRealtime() + TOKEN_TIMEOUT_MS;

        MessageDrag(ChatActivity source, MessageObject message) {
            this.source = new WeakReference<>(source);
            this.messages = source.getFoldegramDragMessages(message);
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
        if (Build.VERSION.SDK_INT < 24 || destroyed || !(view instanceof ChatMessageCell)
                || owner.getActionBar() != null && owner.getActionBar().isActionModeShowed()) {
            return false;
        }
        MessageObject message = ((ChatMessageCell) view).getMessageObject();
        boolean armed = message != null && message.getId() == armedMessage
                && message.getDialogId() == armedDialog && SystemClock.elapsedRealtime() < armedUntil;
        armedUntil = 0;
        boolean direct = owner.getParentActivity() instanceof FoldegramChatWindowActivity
                && ((FoldegramChatWindowActivity) owner.getParentActivity()).canStartMessageDrag(owner);
        if ((!armed && !direct) || !owner.canDragFoldegramMessage(message)) {
            return false;
        }
        MessageDrag drag = new MessageDrag(owner, message);
        if (drag.messages.isEmpty()) return false;
        drag.sourceView = new WeakReference<>(view);
        drag.heldMessageId = message.getId();
        activeMessageDrag = drag;
        ClipData data = new ClipData("Foldegram message", new String[]{MESSAGE_MIME}, new ClipData.Item(drag.token));
        boolean started;
        try {
            started = view.startDragAndDrop(data, new View.DragShadowBuilder(view), null, 0);
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
                owner.showFoldegramDropHighlight(false);
                ClipDescription description = event.getClipDescription();
                if (description == null) return false;
                if (description.hasMimeType(MESSAGE_MIME)) {
                    return owner.canReceiveFoldegramDrag(false)
                            || activeMessageDrag != null && activeMessageDrag.source.get() == owner;
                }
                return imageImport == null && owner.canReceiveFoldegramDrag(false)
                        && (description.hasMimeType("text/plain")
                        || description.hasMimeType("text/uri-list")
                        || description.hasMimeType("*/*"));
            }
            case DragEvent.ACTION_DROP:
                owner.showFoldegramDropHighlight(false);
                return drop(event);
            case DragEvent.ACTION_DRAG_ENDED:
                owner.showFoldegramDropHighlight(false);
                if (activeMessageDrag != null && activeMessageDrag.source.get() == owner) {
                    MessageDrag completed = activeMessageDrag;
                    activeMessageDrag = null;
                    if (completed.releasedInSource) AndroidUtilities.runOnUIThread(() ->
                            owner.showFoldegramMessageMenu(completed.sourceView.get(), completed.heldMessageId));
                }
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
                owner.showFoldegramDropHighlight(activeMessageDrag == null || activeMessageDrag.source.get() != owner);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                owner.showFoldegramDropHighlight(false);
                return true;
            default:
                return false;
        }
    }

    private boolean drop(DragEvent event) {
        try {
            ClipData data = event.getClipData();
            if (data == null || !isSupportedItemCount(data.getItemCount()) || imageImport != null) {
                owner.showFoldegramDropNotice("Drop up to 10 files or text items at a time");
                return false;
            }
            if (!owner.activateFoldegramDropTarget()) {
                owner.showFoldegramDropNotice("Open an unlocked, writable chat to receive this drop");
                return false;
            }
            ClipData.Item item = data.getItemAt(0);
            if (data.getDescription().hasMimeType(MESSAGE_MIME)) {
                if (!isSingleItem(data.getItemCount())) return false;
                MessageDrag drag = activeMessageDrag;
                if (drag == null || item.getText() == null || !drag.token.contentEquals(item.getText())
                        || SystemClock.elapsedRealtime() >= drag.expires) return false;
                ChatActivity source = drag.source.get();
                if (source == owner) { drag.releasedInSource = true; return false; }
                if (source == null) return false;
                for (MessageObject message : drag.messages) if (!source.canDragFoldegramMessage(message)) return false;
                activeMessageDrag = null; // A capability is single-use, even when the destination declines it.
                return owner.stageFoldegramForwards(drag.messages);
            }
            if (item.getIntent() != null || !owner.canAcceptFoldegramDrop(false)) return false;
            Uri uri = item.getUri();
            if (uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                if (!owner.canStageFoldegramMedia()) return false;
                ArrayList<Uri> uris = new ArrayList<>();
                for (int i = 0; i < data.getItemCount(); i++) {
                    ClipData.Item entry = data.getItemAt(i);
                    Uri content = entry.getUri();
                    if (entry.getIntent() != null || content == null || !ContentResolver.SCHEME_CONTENT.equals(content.getScheme())) return false;
                    uris.add(content);
                }
                DragAndDropPermissions permissions = owner.getParentActivity().requestDragAndDropPermissions(event);
                // A null transient-grant token is possible when this exact URI is already readable.
                // The provider still enforces read access; never request broader/persistent access.
                boolean importOwnsGrant = false;
                try {
                    // Taking the URI grant first makes its provider visible to package queries.
                    // Resolving before taking it can silently reject Photos under package visibility.
                    for (Uri content : uris) {
                        ProviderInfo provider = owner.getParentActivity().getPackageManager().resolveContentProvider(content.getAuthority(), 0);
                        if (provider == null || provider.applicationInfo == null || provider.applicationInfo.uid == Process.myUid()) {
                            owner.showFoldegramDropNotice("This provider cannot share that file with Foldegram");
                            return false;
                        }
                    }
                    imageImport = new ImageImport(uris, permissions);
                    importOwnsGrant = true;
                    try {
                        imageImport.start();
                    } catch (RuntimeException e) {
                        cancelImport();
                        throw e;
                    }
                    return true;
                } finally {
                    if (!importOwnsGrant && permissions != null) permissions.release();
                }
            }
            // No URI coercion, HTML, Intent execution, remote downloads, or file:// access.
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < data.getItemCount(); i++) {
                ClipData.Item entry = data.getItemAt(i);
                Uri link = entry.getUri();
                if (entry.getIntent() != null) return false;
                CharSequence plain = entry.getText();
                if (plain == null && link != null && ("https".equals(link.getScheme()) || "http".equals(link.getScheme()))) plain = link.toString();
                if (!isSupportedTextItem(data.getDescription().hasMimeType("text/plain") || data.getDescription().hasMimeType("text/uri-list"), plain, link == null ? null : link.getScheme())) {
                    owner.showFoldegramDropNotice("Drop text, a link, or files shared by the source app");
                    return false;
                }
                if (text.length() != 0) text.append("\n");
                text.append(plain);
                if (text.length() > MAX_TEXT_LENGTH) return false;
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
        private final ArrayList<Uri> uris;
        private long copiedBytes;
        private String mime;
        private String displayName;
        private final DragAndDropPermissions permissions;
        private final CancellationSignal cancellation = new CancellationSignal();
        private final AtomicBoolean released = new AtomicBoolean();
        private volatile ParcelFileDescriptor descriptor;
        private volatile boolean cancelled;
        private final Runnable timeout = () -> {
            cancel();
            if (imageImport == this) {
                imageImport = null;
                owner.showFoldegramDropNotice("File import timed out. Your draft is unchanged");
            }
        };

        ImageImport(ArrayList<Uri> uris, DragAndDropPermissions permissions) {
            this.uris = uris;
            this.permissions = permissions;
        }

        void start() {
            AndroidUtilities.runOnUIThread(timeout, 30000);
            Thread thread = new Thread(() -> {
                ArrayList<File> result = new ArrayList<>();
                try {
                    for (Uri uri : uris) result.add(copyImage(uri));
                } catch (Exception e) {
                    for (File file : result) delete(file);
                    result.clear();
                    FileLog.e(e);
                } finally {
                    AndroidUtilities.cancelRunOnUIThread(timeout);
                    release(); // Preview uses only the private copy, never the external URI grant.
                }
                final ArrayList<File> copies = result;
                AndroidUtilities.runOnUIThread(() -> {
                    if (imageImport == this) imageImport = null;
                    if (cancelled || destroyed || !owner.canAcceptFoldegramDrop(false)) {
                        for (File file : copies) delete(file);
                    } else if (copies.isEmpty()) {
                        owner.showFoldegramDropNotice("Could not open this file. The source must grant access; the limit is 100 MB");
                    } else {
                        if (copies.size() == 1 && isSupportedImage(mime)) owner.chooseFoldegramImageMode(copies.get(0));
                        else owner.stageFoldegramFiles(copies);
                    }
                });
            }, "FoldegramImageDrop");
            thread.start();
        }

        private File copyImage(Uri uri) throws IOException {
            ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
            mime = resolver.getType(uri);
            displayName = "Shared file";
            try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null, cancellation)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) displayName = safeDisplayName(cursor.getString(0));
            }
            String extension = mime == null ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            File directory = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create image cache");
            File privateFolder = File.createTempFile("foldegram_dropdir_", "", directory);
            if (!privateFolder.delete() || !privateFolder.mkdir()) throw new IOException("Cannot create private import directory");
            if ("Shared file".equals(displayName) && extension != null) displayName += "." + extension;
            File copy = new File(privateFolder, displayName);
            boolean success = false;
            try {
                // Photos/cloud providers may expose a virtual/typed asset or a file subsection.
                // AssetFileDescriptor streams preserve the offset/length; raw FileDescriptor streams do not.
                AssetFileDescriptor asset;
                try {
                    asset = resolver.openTypedAssetFileDescriptor(uri,
                            mime != null ? mime : "*/*", null, cancellation);
                } catch (java.io.FileNotFoundException unsupportedTypedStream) {
                    asset = resolver.openAssetFileDescriptor(uri, "r", cancellation);
                }
                if (asset == null) throw new IOException("File access refused");
                descriptor = asset.getParcelFileDescriptor();
                try (AssetFileDescriptor grantedAsset = asset) {
                if (cancelled || AndroidUtilities.isInternalUri(descriptor.getFd())) throw new IOException("Image access refused");
                try (InputStream in = grantedAsset.createInputStream(); FileOutputStream out = new FileOutputStream(copy)) {
                    byte[] buffer = new byte[32768];
                    long total = 0;
                    long started = SystemClock.elapsedRealtime();
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        total += count;
                        if (!isWithinImageCopyLimit(total + copiedBytes, SystemClock.elapsedRealtime() - started, cancelled)) throw new IOException("Image import limit exceeded");
                        out.write(buffer, 0, count);
                    }
                    if (total == 0) throw new IOException("Empty file");
                    copiedBytes += total;
                }
                }
                if (mime == null || mime.startsWith("image/") || "application/octet-stream".equals(mime)) {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(copy.getAbsolutePath(), bounds);
                    if (isSupportedImage(bounds.outMimeType)) {
                        if (!isSupportedImageDimensions(bounds.outWidth, bounds.outHeight)) throw new IOException("Invalid image dimensions");
                        mime = bounds.outMimeType;
                    } else if (mime != null && mime.startsWith("image/") && !"image/gif".equals(mime)) {
                        throw new IOException("Unsupported image format");
                    }
                }
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
                try { if (permissions != null) permissions.release(); } catch (RuntimeException e) { FileLog.e(e); }
            }
        }
    }

    private static String safeDisplayName(String name) {
        if (name == null) return "Shared file";
        String safe = name.replaceAll("[\\p{Cntrl}\\p{Cf}\\/\\\\]", "_").trim();
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) return "Shared file";
        return safe.substring(0, Math.min(120, safe.length()));
    }

    private static boolean isWithinImageCopyLimit(long bytes, long elapsed, boolean cancelled) {
        return !cancelled && bytes >= 0 && bytes <= MAX_IMAGE_BYTES && elapsed <= 30000;
    }

    private static boolean isSupportedItemCount(int count) { return count > 0 && count <= 10; }

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
        return "image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime)
                || "image/heic".equals(mime) || "image/heif".equals(mime) || "image/avif".equals(mime);
    }

    static final class DropHighlight extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            paint.setColor(accent);
            paint.setAlpha(25);
            paint.setStyle(Paint.Style.FILL);
            float radius = AndroidUtilities.dp(12);
            canvas.drawRoundRect(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            for (int layer = 3; layer >= 1; layer--) {
                paint.setAlpha(layer == 1 ? 255 : 25);
                paint.setStrokeWidth(AndroidUtilities.dp(layer == 1 ? 2 : layer * 3));
                float inset = AndroidUtilities.dp(5);
                canvas.drawRoundRect(inset, inset, getBounds().right - inset, getBounds().bottom - inset, radius, radius, paint);
            }
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(AndroidUtilities.dp(16));
            String label = "Drop to preview";
            float width = paint.measureText(label) + AndroidUtilities.dp(32);
            float x = (getBounds().width() - width) / 2f;
            float y = Math.max(AndroidUtilities.dp(64), getBounds().height() / 2f);
            canvas.drawRoundRect(x, y - AndroidUtilities.dp(26), x + width, y + AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            canvas.drawText(label, x + AndroidUtilities.dp(16), y + AndroidUtilities.dp(2), paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static void delete(File file) {
        if (file == null) return;
        if (file.exists() && !file.delete()) FileLog.d("Could not remove cancelled drop file");
        File parent = file.getParentFile();
        if (parent != null && parent.getName().startsWith("foldegram_dropdir_")) parent.delete();
    }
}
