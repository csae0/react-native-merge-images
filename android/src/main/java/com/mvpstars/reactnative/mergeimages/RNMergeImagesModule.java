package com.mvpstars.reactnative.mergeimages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
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
        final ArrayList<Integer> maxColumnWidths = new ArrayList<>();
        int columnWitdhSum = 0, rowHeightSum = 0, tempMaxRowHeight = 0;

        // Get image metaData, ignore images without metadata
        for (int i = 0, n = images.size(); i < n; i++) {
          BitmapMetadata bitmapMetadata = BitmapMetadata.load(getFilePath(images.getString(i)));
          if (bitmapMetadata != null) {
            bitmapsMetadata.add(bitmapMetadata);
          }
        }

        // Calc grid dimensions
        int columns = 2; // TODO: Works with all kinds of columns, could be added as feature to set column parameter

        int rows = (int) Math.ceil((float) bitmapsMetadata.size() / columns);

        // Calculate canvas dimensions
        for (int i = 0, n = bitmapsMetadata.size(); i < n; i++) {
          BitmapMetadata metaData = bitmapsMetadata.get(i);
          try {
            if (maxColumnWidths.get(i % columns) < metaData.width) {
              maxColumnWidths.set(i % columns, metaData.width);
            }
          } catch(IndexOutOfBoundsException e) {
            // If no value is set
            maxColumnWidths.add(i % columns, metaData.width);
          }
          if (tempMaxRowHeight < metaData.height) {
            tempMaxRowHeight = metaData.height;
          }
          if ((i + 1) % columns == 0 || i + 1 == bitmapsMetadata.size()) {
            rowHeightSum += tempMaxRowHeight;
            maxRowHeights.add(tempMaxRowHeight);
            tempMaxRowHeight = 0;
          }
        }
        // sum up maxColumnWidths
        for(int maxColumnWidth: maxColumnWidths) {
          columnWitdhSum += maxColumnWidth;
        }
        // Create bitmap with collage dimensions
        int spacing = 20;
        final Bitmap mergedBitmap = Bitmap.createBitmap(columnWitdhSum + ((columns + 1) * spacing), rowHeightSum + ((rows + 1) * spacing), Bitmap.Config.ARGB_8888);
        // mergedBitmap.eraseColor(Color.GREEN);
        final Canvas canvas = new Canvas(mergedBitmap);
        // Merge images
        int left = spacing, top = spacing, centerPaddingXSum = 0;
        for (int i = 0, n = bitmapsMetadata.size(); i < n; i++) {
          BitmapMetadata metaData = bitmapsMetadata.get(i);
          Bitmap bitmap = BitmapFactory.decodeFile(metaData.fileName);
          // position correctly
          if (i > 0 && columns > 0 && i % columns == 0) {
            left = spacing;
            centerPaddingXSum = 0;
            top += spacing + maxRowHeights.get((i - 1) / columns);
          }
          int centerPaddingX = (maxColumnWidths.get(i % columns) - metaData.width) / 2;
          int centerPaddingY = (maxRowHeights.get(i / columns) - metaData.height) / 2;
          int canvasLeft = left + centerPaddingXSum + centerPaddingX;
          int canvasTop = top + centerPaddingY;
          int canvasRight = left + centerPaddingXSum + centerPaddingX + metaData.width;
          int canvasBottom = top + centerPaddingY + metaData.height;

          canvas.drawBitmap(bitmap, null, new RectF(canvasLeft, canvasTop, canvasRight, canvasBottom), null);
          centerPaddingXSum += 2 * centerPaddingX;
          left += spacing + metaData.width;
          bitmap.recycle();
        }

        return mergedBitmap;
      } catch (Exception e) {
        Log.i("TEST123", "EXCEPTION: " + e.getClass().getName() + " " + Log.getStackTraceString(e));
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
