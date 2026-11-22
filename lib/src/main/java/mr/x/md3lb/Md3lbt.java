
package mr.x.md3lb;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.MaskableFrameLayout;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MD3 JSON 轮播图库
 *
 * 支持 JSON 加载、网络图片、多线程下载、
 * 内存缓存、圆角遮罩和自动轮播。
 */
public class Md3lbt {

    private static final String TAG = "Md3lbt";

    private final Activity activity;

    private String jsonData;

    private RecyclerView recyclerView;

    private CarouselLayoutManager layoutManager;

    private CarouselSnapHelper snapHelper;

    private CarouselAdapter adapter;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private Runnable autoPlayRunnable;

    private RecyclerView.OnScrollListener scrollListener;

    private int currentPosition = 0;

    private boolean initialized = false;

    private static final int AUTO_PLAY_TIME = 3000;

    private static final int ITEM_WIDTH_DP = 200;

    private static final int CORNER_RADIUS_DP = 16;

    private static final int ITEM_MARGIN_DP = 8;

    public Md3lbt(Activity activity) {
        this.activity = activity;
    }

    /**
     * 设置 JSON 数据
     */
    public Md3lbt jsonsj(String json) {
        this.jsonData = json;
        return this;
    }

    /**
     * 绑定 RecyclerView
     */
    public Md3lbt loadInto(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        return this;
    }

    /**
     * 设置轮播样式并启动
     */
    public Md3lbt setStyle(
            CarouselLayoutManager layoutManager) {

        this.layoutManager = layoutManager;

        initialize();

        return this;
    }

    /**
     * 初始化
     */
    private void initialize() {

        if (activity == null ||
                recyclerView == null ||
                layoutManager == null ||
                jsonData == null) {

            throw new IllegalStateException(
                    "请先设置 JSON、RecyclerView 和 LayoutManager"
            );
        }

        stop();

        List<String> imageUrls = parseJson(jsonData);

        recyclerView.setLayoutManager(layoutManager);

        recyclerView.setClipChildren(false);
        recyclerView.setClipToPadding(false);

        snapHelper = new CarouselSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);

        adapter = new CarouselAdapter(
                activity,
                imageUrls
        );

        recyclerView.setAdapter(adapter);

        currentPosition = 0;
        initialized = true;

        setupScrollListener();

        startAutoPlay();
    }

    /**
     * JSON 解析
     */
    private List<String> parseJson(String json) {

        List<String> result = new ArrayList<>();

        try {

            String trimmed = json.trim();

            JSONArray array;

            if (trimmed.startsWith("[")) {

                array = new JSONArray(trimmed);

            } else {

                JSONObject object =
                        new JSONObject(trimmed);

                if (object.has("images")) {
                    array = object.getJSONArray("images");

                } else if (object.has("data")) {
                    array = object.getJSONArray("data");

                } else if (object.has("list")) {
                    array = object.getJSONArray("list");

                } else {
                    throw new IllegalArgumentException(
                            "没有找到 images、data 或 list 数组"
                    );
                }
            }

            for (int i = 0; i < array.length(); i++) {

                Object item = array.get(i);

                String imageUrl = null;

                if (item instanceof String) {

                    imageUrl = (String) item;

                } else if (item instanceof JSONObject) {

                    JSONObject obj = (JSONObject) item;

                    imageUrl = obj.optString("url", "");

                    if (imageUrl.isEmpty()) {
                        imageUrl = obj.optString("image", "");
                    }

                    if (imageUrl.isEmpty()) {
                        imageUrl = obj.optString("imageUrl", "");
                    }

                    if (imageUrl.isEmpty()) {
                        imageUrl = obj.optString("src", "");
                    }
                }

                if (imageUrl != null &&
                        !imageUrl.trim().isEmpty()) {

                    result.add(imageUrl.trim());
                }
            }

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "JSON 解析失败：" + e.getMessage(),
                    e
            );
        }

        return result;
    }

    /**
     * 滚动监听
     */
    private void setupScrollListener() {

        scrollListener =
                new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(
                    RecyclerView recyclerView,
                    int newState) {

                if (newState ==
                        RecyclerView.SCROLL_STATE_DRAGGING) {

                    stopAutoPlay();

                } else if (newState ==
                        RecyclerView.SCROLL_STATE_IDLE) {

                    updateCurrentPosition();

                    startAutoPlay();
                }
            }
        };

        recyclerView.addOnScrollListener(scrollListener);
    }

    /**
     * 更新当前居中位置
     */
    private void updateCurrentPosition() {

        if (recyclerView == null ||
                snapHelper == null ||
                layoutManager == null) {
            return;
        }

        View view = snapHelper.findSnapView(layoutManager);

        if (view != null) {

            int position =
                    recyclerView.getChildAdapterPosition(view);

            if (position != RecyclerView.NO_POSITION) {
                currentPosition = position;
            }
        }
    }

    /**
     * 自动播放
     */
    private void startAutoPlay() {

        stopAutoPlay();

        if (!initialized ||
                recyclerView == null ||
                adapter == null ||
                adapter.getItemCount() <= 1) {
            return;
        }

        autoPlayRunnable = new Runnable() {

            @Override
            public void run() {

                if (!initialized ||
                        recyclerView == null ||
                        adapter == null) {
                    return;
                }

                int count = adapter.getItemCount();

                if (count <= 1) {
                    return;
                }

                updateCurrentPosition();

                currentPosition =
                        (currentPosition + 1) % count;

                recyclerView.smoothScrollToPosition(
                        currentPosition
                );

                handler.postDelayed(
                        this,
                        AUTO_PLAY_TIME
                );
            }
        };

        handler.postDelayed(
                autoPlayRunnable,
                AUTO_PLAY_TIME
        );
    }

    /**
     * 停止自动播放
     */
    private void stopAutoPlay() {

        if (autoPlayRunnable != null) {

            handler.removeCallbacks(autoPlayRunnable);

            autoPlayRunnable = null;
        }
    }

    /**
     * 停止轮播
     */
    public void stop() {

        stopAutoPlay();

        initialized = false;

        if (recyclerView != null &&
                scrollListener != null) {

            recyclerView.removeOnScrollListener(
                    scrollListener
            );

            scrollListener = null;
        }

        if (adapter != null) {

            adapter.release();

            adapter = null;
        }
    }

    /**
     * 获取适配器
     */
    public RecyclerView.Adapter<?> getAdapter() {
        return adapter;
    }

    /**
     * 获取图片数量
     */
    public int getItemCount() {
        return adapter == null
                ? 0
                : adapter.getItemCount();
    }

    /**
     * RecyclerView 适配器
     */
    private static class CarouselAdapter
            extends RecyclerView.Adapter<CarouselAdapter.ViewHolder> {

        private final Activity activity;

        private final List<String> imageUrls;

        private final ImageLoader imageLoader;

        CarouselAdapter(
                Activity activity,
                List<String> imageUrls) {

            this.activity = activity;

            this.imageUrls = imageUrls;

            this.imageLoader = new ImageLoader();
        }

        @Override
        public ViewHolder onCreateViewHolder(
                ViewGroup parent,
                int viewType) {

            MaskableFrameLayout frame =
                    new MaskableFrameLayout(activity);

            int itemWidth = dpToPx(ITEM_WIDTH_DP);

            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(
                            itemWidth,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );

            int margin = dpToPx(ITEM_MARGIN_DP);

            params.setMargins(
                    margin,
                    0,
                    margin,
                    0
            );

            frame.setLayoutParams(params);

            float radius = dpToPx(CORNER_RADIUS_DP);

            ShapeAppearanceModel shape =
                    ShapeAppearanceModel.builder()
                            .setAllCorners(
                                    CornerFamily.ROUNDED,
                                    radius
                            )
                            .build();

            frame.setShapeAppearanceModel(shape);

            ImageView imageView = new ImageView(activity);

            imageView.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            imageView.setLayoutParams(
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );

            imageView.setOutlineProvider(
                    new ViewOutlineProvider() {

                @Override
                public void getOutline(
                        View view,
                        Outline outline) {

                    outline.setRoundRect(
                            0,
                            0,
                            view.getWidth(),
                            view.getHeight(),
                            radius
                    );
                }
            });

            imageView.setClipToOutline(true);

            frame.addView(imageView);

            return new ViewHolder(frame, imageView);
        }

        @Override
        public void onBindViewHolder(
                ViewHolder holder,
                int position) {

            String imageUrl = imageUrls.get(position);

            holder.imageView.setTag(imageUrl);

            holder.imageView.setImageDrawable(null);

            holder.imageView.setBackgroundColor(
                    0xFFE0E0E0
            );

            holder.maskableFrame.setOnMaskChangedListener(
                    maskRect -> {

                if (holder.maskableFrame.getWidth() > 0) {

                    float progress =
                            maskRect.left /
                            (float) holder.maskableFrame.getWidth();

                    float scale =
                            1f - progress * 0.15f;

                    holder.itemView.setScaleX(scale);
                    holder.itemView.setScaleY(scale);
                }
            });

            imageLoader.load(
                    imageUrl,
                    holder.imageView
            );
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {

            holder.imageView.setTag(null);

            holder.imageView.setImageDrawable(null);

            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        private int dpToPx(int dp) {

            return (int) (
                    dp *
                    activity.getResources()
                            .getDisplayMetrics().density + 0.5f
            );
        }

        void release() {
            imageLoader.shutdown();
        }

        static class ViewHolder
                extends RecyclerView.ViewHolder {

            MaskableFrameLayout maskableFrame;

            ImageView imageView;

            ViewHolder(
                    MaskableFrameLayout frame,
                    ImageView imageView) {

                super(frame);

                this.maskableFrame = frame;

                this.imageView = imageView;
            }
        }
    }

    /**
     * 全新网络图片加载器
     */
    private static class ImageLoader {

        /**
         * 四线程图片下载
         */
        private final ExecutorService executor =
                Executors.newFixedThreadPool(4);

        /**
         * 主线程更新 UI
         */
        private final Handler mainHandler =
                new Handler(Looper.getMainLooper());

        /**
         * 图片内存缓存
         */
        private final LruCache<String, Bitmap> memoryCache;

        ImageLoader() {

            int maxMemory =
                    (int) (Runtime.getRuntime().maxMemory() / 1024);

            int cacheSize = maxMemory / 8;

            memoryCache = new LruCache<String, Bitmap>(
                    cacheSize
            ) {

                @Override
                protected int sizeOf(
                        String key,
                        Bitmap bitmap) {

                    return bitmap.getByteCount() / 1024;
                }
            };
        }

        /**
         * 加载图片
         */
        void load(
                String imageUrl,
                ImageView imageView) {

            if (imageUrl == null ||
                    imageUrl.trim().isEmpty()) {
                return;
            }

            imageView.setTag(imageUrl);

            // 先从内存缓存读取
            Bitmap cached = memoryCache.get(imageUrl);

            if (cached != null &&
                    !cached.isRecycled()) {

                imageView.setImageBitmap(cached);

                imageView.setBackgroundColor(
                        0x00000000
                );

                return;
            }

            // 缓存不存在，开始网络下载
            try {

                executor.execute(() -> {

                    Bitmap bitmap = download(imageUrl);

                    if (bitmap == null) {
                        return;
                    }

                    memoryCache.put(imageUrl, bitmap);

                    mainHandler.post(() -> {

                        Object tag = imageView.getTag();

                        // 防止 RecyclerView 复用导致图片错位
                        if (imageUrl.equals(tag)) {

                            imageView.setImageBitmap(bitmap);

                            imageView.setBackgroundColor(
                                    0x00000000
                            );
                        }
                    });
                });

            } catch (Exception e) {

                Log.e(TAG, "图片任务提交失败：" + imageUrl, e);
            }
        }

        /**
         * 下载并解码图片
         */
        private Bitmap download(String imageUrl) {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(imageUrl);

                connection =
                        (HttpURLConnection) url.openConnection();

                // 允许重定向
                connection.setInstanceFollowRedirects(true);

                // 请求方式
                connection.setRequestMethod("GET");

                // 超时时间
                connection.setConnectTimeout(15000);

                connection.setReadTimeout(20000);

                // 请求头
                connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
                );

                connection.setRequestProperty(
                        "Accept",
                        "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
                );

                connection.setRequestProperty(
                        "Connection",
                        "Keep-Alive"
                );

                connection.setUseCaches(true);

                connection.connect();

                int responseCode =
                        connection.getResponseCode();

                Log.d(
                        TAG,
                        "图片请求：" + imageUrl +
                        "，HTTP状态：" + responseCode
                );

                if (responseCode !=
                        HttpURLConnection.HTTP_OK) {

                    Log.e(
                            TAG,
                            "图片请求失败，HTTP状态：" + responseCode
                    );

                    return null;
                }

                String contentType =
                        connection.getContentType();

                if (contentType != null &&
                        !contentType.toLowerCase().startsWith("image/")) {

                    Log.e(
                            TAG,
                            "返回内容不是图片：" + contentType
                    );

                    return null;
                }

                try (InputStream input =
                             connection.getInputStream();

                     ByteArrayOutputStream output =
                             new ByteArrayOutputStream()) {

                    byte[] buffer = new byte[8192];

                    int length;

                    while ((length = input.read(buffer)) != -1) {

                        output.write(buffer, 0, length);
                    }

                    byte[] imageBytes = output.toByteArray();

                    if (imageBytes.length == 0) {

                        Log.e(TAG, "图片数据为空：" + imageUrl);

                        return null;
                    }

                    BitmapFactory.Options options =
                            new BitmapFactory.Options();

                    options.inJustDecodeBounds = true;

                    BitmapFactory.decodeByteArray(
                            imageBytes,
                            0,
                            imageBytes.length,
                            options
                    );

                    if (options.outWidth <= 0 ||
                            options.outHeight <= 0) {

                        Log.e(TAG, "图片解码失败：" + imageUrl);

                        return null;
                    }

                    // 根据图片尺寸进行采样，减少内存占用
                    options.inJustDecodeBounds = false;

                    options.inSampleSize = calculateSampleSize(
                            options.outWidth,
                            options.outHeight
                    );

                    options.inPreferredConfig =
                            Bitmap.Config.RGB_565;

                    Bitmap bitmap =
                            BitmapFactory.decodeByteArray(
                                    imageBytes,
                                    0,
                                    imageBytes.length,
                                    options
                            );

                    if (bitmap == null) {

                        Log.e(TAG, "Bitmap 创建失败：" + imageUrl);

                        return null;
                    }

                    Log.d(
                            TAG,
                            "图片加载成功：" + imageUrl
                    );

                    return bitmap;
                }

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "网络图片加载异常：" + imageUrl,
                        e
                );

                return null;

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        /**
         * 计算图片采样比例
         */
        private int calculateSampleSize(
                int width,
                int height) {

            int sampleSize = 1;

            int maxSize = 1600;

            while (width / sampleSize > maxSize ||
                    height / sampleSize > maxSize) {

                sampleSize *= 2;
            }

            return sampleSize;
        }

        /**
         * 释放线程池
         */
        void shutdown() {

            executor.shutdownNow();

            mainHandler.removeCallbacksAndMessages(null);

            memoryCache.evictAll();
        }
    }
}
