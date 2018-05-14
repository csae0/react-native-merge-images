package com.mvpstars.reactnative.mergeimages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class RNMergeImagesModule extends ReactContextBaseJavaModule {

  private static final String TAG = "RNMergeImages";

  public static final int RN_MERGE_SIZE_SMALLEST = 1;
  public static final int RN_MERGE_SIZE_LARGEST = 2;

  public static final int RN_MERGE_TARGET_TEMP = 1;
  public static final int RN_MERGE_TARGET_DISK = 2;

  public static final int DEFAULT_JPEG_QUALITY = 80;

  public static final int RN_MERGE_TYPE_MERGE = 1;
  public static final int RN_MERGE_TYPE_COLLAGE = 2;

  private final ReactApplicationContext reactContext;

  public RNMergeImagesModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Nullable
  @Override
  public Map<String, Object> getConstants() {
    return Collections.unmodifiableMap(new HashMap<String, Object>() {
      {
        put("size", getSizeConstants());
        put("target", getTargetConstants());
        put("mergeType", getMergeTypeConstants());
      }

      private Map<String, Object> getSizeConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("SMALLEST", RN_MERGE_SIZE_SMALLEST);
            put("LARGEST", RN_MERGE_SIZE_LARGEST);
          }
        });
      }

      private Map<String, Object> getTargetConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("TEMP", RN_MERGE_TARGET_TEMP);
            put("DISK", RN_MERGE_TARGET_DISK);
          }
        });
      }

      private Map<String, Object> getMergeTypeConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("MERGE", RN_MERGE_TYPE_MERGE);
            put("COLLAGE", RN_MERGE_TYPE_COLLAGE);
          }
        });
      }
    });
  }

  @ReactMethod
  public void merge(final ReadableArray images, final ReadableMap options, final Promise promise) {
    new MergeAsyncTask(images, options, promise).execute();
  }

  private class MergeAsyncTask extends AsyncTask<Void, Void, Void> {
    private final ReadableArray images;
    private final ReadableMap options;
    private final Promise promise;

    public MergeAsyncTask(final ReadableArray images, final ReadableMap options, final Promise promise) {
      this.images = images;
      this.options = options;
      this.promise = promise;
    }

    @Override
    protected Void doInBackground(Void... voids) {
      final int size = options.hasKey("size") ? options.getInt("size") : RN_MERGE_SIZE_SMALLEST;
      final int target = options.hasKey("target") ? options.getInt("target") : RN_MERGE_TARGET_TEMP;
      final int jpegQuality = options.hasKey("jpegQuality") ? options.getInt("jpegQuality") : DEFAULT_JPEG_QUALITY;
      final int mergeType = options.hasKey("mergeType") ? options.getInt("mergeType") : RN_MERGE_TYPE_MERGE;

      Bitmap resultBitmap = null;

      switch (mergeType) {
        case RN_MERGE_TYPE_MERGE: {
          resultBitmap = merge(size);
          break;
        }
        case RN_MERGE_TYPE_COLLAGE: {
          resultBitmap = collage();
          break;
        }
        default:
          resultBitmap = merge(size);
      }

      saveBitmap(resultBitmap, target, jpegQuality, promise);
      return null;
    }

    private Bitmap merge(final int size) {
      final ArrayList<BitmapMetadata> bitmaps = new ArrayList<>(images.size());
      int targetWidth, targetHeight;

      switch (size) {
        case RN_MERGE_SIZE_SMALLEST:
          targetWidth = Integer.MAX_VALUE;
          targetHeight = Integer.MAX_VALUE;
          break;
        default:
          targetWidth = 0;
          targetHeight = 0;
      }

      for (int i = 0, n = images.size(); i < n; i++) {
        BitmapMetadata bitmapMetadata = BitmapMetadata.load(getFilePath(images.getString(i)));
        if (bitmapMetadata != null) {
          bitmaps.add(bitmapMetadata);
          if (size == RN_MERGE_SIZE_LARGEST && (bitmapMetadata.width > targetWidth || bitmapMetadata.height > targetHeight)) {
            targetWidth = bitmapMetadata.width;
            targetHeight = bitmapMetadata.height;
          } else if (size == RN_MERGE_SIZE_SMALLEST && (bitmapMetadata.width < targetWidth || bitmapMetadata.height < targetHeight)) {
            targetWidth = bitmapMetadata.width;
            targetHeight = bitmapMetadata.height;
          }
        }
      }

      final Bitmap mergedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
      final Canvas canvas = new Canvas(mergedBitmap);

      for (BitmapMetadata bitmapMetadata: bitmaps) {
        Bitmap bitmap = BitmapFactory.decodeFile(bitmapMetadata.fileName);
        Matrix matrix = bitmapMetadata.getMatrix(targetWidth, targetHeight);
        if (matrix == null) {
          canvas.drawBitmap(bitmap, null, new RectF(0, 0, targetWidth, targetHeight), null);
        } else {
          canvas.drawBitmap(bitmap, matrix, null);
        }
        bitmap.recycle();
      }
      return mergedBitmap;
    }

    // Create collage from images
    private Bitmap collage () {
      try {
        final ArrayList<BitmapMetadata> bitmapsMetadata = new ArrayList<>();
        final ArrayList<Integer> maxRowHeights = new ArrayList<>();
        int maxRowWidth = 0, rowHeightSum = 0, tempRowWidth = 0, tempMaxRowHeight = 0;

        // Get image metaData, ignore images without metadata
        for (int i = 0, n = images.size(); i < n; i++) {
          BitmapMetadata bitmapMetadata = BitmapMetadata.load(getFilePath(images.getString(i)));
          if (bitmapMetadata != null) {
            bitmapsMetadata.add(bitmapMetadata);
          }
        }

        // Calc grid dimensions
        int columns = 2;
        int rows = (int) Math.ceil((float) bitmapsMetadata.size() / columns);
        Log.i("TEST123", "ROWS x COLUMNS: " + rows + " x " + columns);

        // Calculate canvas dimensions
        for (int i = 0, n = bitmapsMetadata.size(); i < n; i++) {
          BitmapMetadata metaData = bitmapsMetadata.get(i);
          Log.i("TEST123", i + " WIDTH: " + metaData.width + " HEIGHT: " + metaData.height);
          tempRowWidth += metaData.width;
          if (tempMaxRowHeight < metaData.height) {
            tempMaxRowHeight = metaData.height;
          }
          if (columns > 1 && ((i + 1) % columns == 0 || i + 1 == bitmapsMetadata.size())) {
            // Height
            rowHeightSum += tempMaxRowHeight;
            maxRowHeights.add(tempMaxRowHeight);
            tempMaxRowHeight = 0;
            // Width
            if (maxRowWidth < tempRowWidth) {
              maxRowWidth = tempRowWidth;
            }
            tempRowWidth = 0;
          }
        }

        // Only 1 column set rowHeightSum
        if (rowHeightSum == 0) {
          rowHeightSum = tempMaxRowHeight;
        }
        // last row not filled
        if (maxRowWidth == 0) {
          maxRowWidth = tempRowWidth;
        }

        // Create bitmap with collage dimensions
        Log.i("TEST123", "maxWidth - maxHeight: " + maxRowWidth + " - " + rowHeightSum);
        final Bitmap mergedBitmap = Bitmap.createBitmap(maxRowWidth, rowHeightSum, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(mergedBitmap);

        // Merge images
        int left = 0, top = 0;
        for (int i = 0, n = bitmapsMetadata.size(); i < n; i++) {
          BitmapMetadata metaData = bitmapsMetadata.get(i);
          Bitmap bitmap = BitmapFactory.decodeFile(metaData.fileName);

          // position correctly
          if (i > 0 && columns > 0 && i % columns == 0) {
            left = 0;
            top += maxRowHeights.get(i / columns);
          }
          // TODO: center image if dimensions differ (if image is smaller than allocated space) => rowMaxHeights.get(i / columns) - metaData.height) / 2
          // TODO: add padding around images
          // TODO: dynamic columns?
          Log.i("TEST123", "ROW: " + i / columns + " - ROW_HEIGHT: " + maxRowHeights.get(i / columns));
          canvas.drawBitmap(bitmap, null, new RectF(left, top, left + metaData.width, top + metaData.height), null);

          left += metaData.width;
          bitmap.recycle();
        }

        return mergedBitmap;
      } catch (Exception e) {
        Log.i("TEST123", "EXCEPTION: " + e.getClass().getName() + " " + e.getStackTrace().toString());
      }
      return null;
    }
  }

  private static String getFilePath(String file) {
    try {
      final String uriPath = Uri.parse(file).getPath();
      return (uriPath != null ? uriPath : file);
    } catch (RuntimeException e) {
      return file;
    }
  }

  private void saveBitmap(Bitmap bitmap, int target, int jpegQuality, Promise promise) {
    try {
      File file;
      switch (target) {
        case RN_MERGE_TARGET_DISK:
          file = getDiskFile();
          break;
        default:
          file = getTempFile();
      }
      final FileOutputStream out = new FileOutputStream(file);
      bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
      WritableMap response = new WritableNativeMap();
      response.putString("path", Uri.fromFile(file).toString());
      response.putInt("width", bitmap.getWidth());
      response.putInt("height", bitmap.getHeight());
      promise.resolve(response);
      out.flush();
      out.close();
    } catch (Exception e) {
      promise.reject("Failed to save image file", e);
    }
  }

  private File getDiskFile() throws IOException {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    File outputDir = reactContext.getFilesDir();
    outputDir.mkdirs();
    return new File(outputDir, "IMG_" + timeStamp + ".jpg");
  }

  private File getTempFile() throws IOException {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    File outputDir = reactContext.getCacheDir();
    File outputFile = File.createTempFile("IMG_" + timeStamp, ".jpg", outputDir);
    return outputFile;
  }
}
