package mr.x.md3lb;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
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
 * MD3 JSON 轮播图控件
 *
 * 继承 RecyclerView
 *
 * 保留原版 MD3 Carousel 视觉效果：
 * 1. 中心卡片较大
 * 2. 两侧卡片较小
 * 3. 边缘遮罩动画
 * 4. 圆角卡片
 * 5. 自动轮播
 *
 * 优化：
 * 1. JSON 数据解析
 * 2. 线程池图片加载
 * 3. LruCache 图片缓存
 * 4. 防止图片错位
 * 5. 网络异常处理
 */
public class Md3lbt extends RecyclerView {

    private static final long DEFAULT_INTERVAL = 3000;

    private static final int ITEM_WIDTH_DP = 100;

    private static final int CORNER_RADIUS_DP = 16;

    private static final int ITEM_MARGIN_DP = 8;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService imageExecutor =
            Executors.newFixedThreadPool(3);

    private final LruCache<String, Bitmap> imageCache;

    private CarouselSnapHelper snapHelper;

    private CarouselAdapter adapter;

    private Runnable autoPlayRunnable;

    private long autoPlayInterval = DEFAULT_INTERVAL;

    private boolean autoPlayEnabled = true;

    private boolean released = false;

    private int currentPosition = 0;

    // ==============================
    // 构造方法
    // ==============================

    public Md3lbt(Context context) {
        super(context);
        imageCache = createImageCache();
        init();
    }

    public Md3lbt(Context context, AttributeSet attrs) {
        super(context, attrs);
        imageCache = createImageCache();
        init();
    }

    public Md3lbt(
            Context context,
            AttributeSet attrs,
            int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        imageCache = createImageCache();
        init();
    }

    // ==============================
    // 初始化
    // ==============================

    private void init() {

        setClipChildren(false);

        setClipToPadding(false);

        snapHelper = new CarouselSnapHelper();

        snapHelper.attachToRecyclerView(this);

        addOnScrollListener(new OnScrollListener() {

            @Override
            public void onScrollStateChanged(
                    RecyclerView recyclerView,
                    int newState) {

                if (newState == SCROLL_STATE_DRAGGING) {

                    stopAutoPlay();

                } else if (newState == SCROLL_STATE_IDLE) {

                    updateCurrentPosition();

                    startAutoPlay();
                }
            }
        });
    }

    // ==============================
    // 图片缓存
    // ==============================

    private LruCache<String, Bitmap> createImageCache() {

        int maxMemory = (int) (
                Runtime.getRuntime().maxMemory() / 1024
        );

        return new LruCache<String, Bitmap>(maxMemory / 8) {

            @Override
            protected int sizeOf(String key, Bitmap bitmap) {

                return bitmap.getByteCount() / 1024;
            }
        };
    }

    // ==============================
    // JSON 加载
    // ==============================

    /**
     * 加载 JSON 字符串
     *
     * 支持：
     * ["url1","url2"]
     *
     * [{"url":"url1"},{"url":"url2"}]
     *
     * {"images":[{"url":"url1"}]}
     */
    public void qd(String json) {

        qd(new CarouselLayoutManager(), json);
    }

    /**
     * 自定义 CarouselLayoutManager
     */
    public void qd(
            CarouselLayoutManager layoutManager,
            String json) {

        if (released) {
            throw new IllegalStateException("控件已经释放");
        }

        if (layoutManager == null) {
            throw new IllegalArgumentException("布局管理器不能为空");
        }

        List<ImageItem> items = parseJson(json);

        // 保留原生 Carousel 布局管理器
        setLayoutManager(layoutManager);

        // 创建适配器
        adapter = new CarouselAdapter(
                getContext(),
                items,
                imageCache,
                imageExecutor
        );

        setAdapter(adapter);

        currentPosition = 0;

        startAutoPlay();
    }

    /**
     * 从 res/raw 加载 JSON
     */
    public void qdFromRaw(int rawResId) {

        qdFromRaw(
                new CarouselLayoutManager(),
                rawResId
        );
    }

    /**
     * 自定义布局管理器并从 raw 加载
     */
    public void qdFromRaw(
            CarouselLayoutManager layoutManager,
            int rawResId) {

        qd(
                layoutManager,
                readRawJson(rawResId)
        );
    }

    // ==============================
    // JSON 解析
    // ==============================

    private List<ImageItem> parseJson(String json) {

        if (json == null || json.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "JSON 数据不能为空"
            );
        }

        List<ImageItem> result = new ArrayList<>();

        try {

            String content = json.trim();

            JSONArray array;

            if (content.startsWith("{")) {

                JSONObject root = new JSONObject(content);

                array = root.optJSONArray("images");

                if (array == null) {
                    array = root.optJSONArray("data");
                }

                if (array == null) {
                    throw new IllegalArgumentException(
                            "JSON 缺少 images 或 data 数组"
                    );
                }

            } else {

                array = new JSONArray(content);
            }

            for (int i = 0; i < array.length(); i++) {

                Object object = array.opt(i);

                String url = "";

                String title = "";

                if (object instanceof String) {

                    url = (String) object;

                } else if (object instanceof JSONObject) {

                    JSONObject item = (JSONObject) object;

                    url = item.optString("url", "");

                    if (url.isEmpty()) {
                        url = item.optString("imageUrl", "");
                    }

                    if (url.isEmpty()) {
                        url = item.optString("image", "");
                    }

                    title = item.optString("title", "");
                }

                if (!url.trim().isEmpty()) {

                    result.add(
                            new ImageItem(url.trim(), title)
                    );
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
     * 读取 raw JSON
     */
    private String readRawJson(int rawResId) {

        try (
                InputStream input =
                        getResources().openRawResource(rawResId);

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            byte[] buffer = new byte[4096];

            int length;

            while ((length = input.read(buffer)) != -1) {

                output.write(buffer, 0, length);
            }

            return output.toString("UTF-8");

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "读取 JSON 失败：" + e.getMessage(),
                    e
            );
        }
    }

    // ==============================
    // 图片数据
    // ==============================

    private static class ImageItem {

        final String url;

        final String title;

        ImageItem(String url, String title) {

            this.url = url;

            this.title = title;
        }
    }

    // ==============================
    // 自动轮播
    // ==============================

    public void startAutoPlay() {

        stopAutoPlay();

        if (released || !autoPlayEnabled) {
            return;
        }

        if (adapter == null || adapter.getItemCount() <= 1) {
            return;
        }

        autoPlayRunnable = new Runnable() {

            @Override
            public void run() {

                if (released || !autoPlayEnabled || adapter == null) {
                    return;
                }

                int count = adapter.getItemCount();

                if (count <= 1) {
                    return;
                }

                updateCurrentPosition();

                int nextPosition =
                        (currentPosition + 1) % count;

                smoothScrollToPosition(nextPosition);

                currentPosition = nextPosition;

                handler.postDelayed(
                        this,
                        autoPlayInterval
                );
            }
        };

        handler.postDelayed(
                autoPlayRunnable,
                autoPlayInterval
        );
    }

    public void stopAutoPlay() {

        if (autoPlayRunnable != null) {

            handler.removeCallbacks(autoPlayRunnable);

            autoPlayRunnable = null;
        }
    }

    public void setAutoPlayEnabled(boolean enabled) {

        autoPlayEnabled = enabled;

        if (enabled) {
            startAutoPlay();
        } else {
            stopAutoPlay();
        }
    }

    public void setAutoPlayInterval(long interval) {

        if (interval <= 0) {
            throw new IllegalArgumentException(
                    "轮播时间必须大于0"
            );
        }

        autoPlayInterval = interval;

        startAutoPlay();
    }

    // ==============================
    // 当前页面
    // ==============================

    private void updateCurrentPosition() {

        if (snapHelper == null || getLayoutManager() == null) {
            return;
        }

        View view = snapHelper.findSnapView(getLayoutManager());

        if (view != null) {

            int position = getChildAdapterPosition(view);

            if (position != NO_POSITION) {
                currentPosition = position;
            }
        }
    }

    public int getCurrentPosition() {

        updateCurrentPosition();

        return currentPosition;
    }

    public void setCurrentPosition(int position) {

        if (adapter == null) {
            return;
        }

        if (position < 0 || position >= adapter.getItemCount()) {
            return;
        }

        currentPosition = position;

        smoothScrollToPosition(position);
    }

    // ==============================
    // 缓存管理
    // ==============================

    public void clearImageCache() {

        imageCache.evictAll();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // ==============================
    // 生命周期
    // ==============================

    @Override
    protected void onDetachedFromWindow() {

        stopAutoPlay();

        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {

        super.onAttachedToWindow();

        if (adapter != null && autoPlayEnabled) {
            startAutoPlay();
        }
    }

    public void release() {

        if (released) {
            return;
        }

        released = true;

        stopAutoPlay();

        handler.removeCallbacksAndMessages(null);

        if (snapHelper != null) {

            snapHelper.attachToRecyclerView(null);

            snapHelper = null;
        }

        setAdapter(null);

        adapter = null;

        imageExecutor.shutdownNow();

        imageCache.evictAll();
    }

    // ==============================
    // MD3 Carousel Adapter
    // ==============================

    private static class CarouselAdapter
            extends RecyclerView.Adapter<CarouselAdapter.ViewHolder> {

        private final Context context;

        private final List<ImageItem> items;

        private final LruCache<String, Bitmap> cache;

        private final ExecutorService executor;

        private final int itemWidthPx;

        CarouselAdapter(
                Context context,
                List<ImageItem> items,
                LruCache<String, Bitmap> cache,
                ExecutorService executor) {

            this.context = context;

            this.items = items;

            this.cache = cache;

            this.executor = executor;

            float density = context.getResources()
                    .getDisplayMetrics().density;

            itemWidthPx = (int) (
                    ITEM_WIDTH_DP * density + 0.5f
            );
        }

        @Override
        public ViewHolder onCreateViewHolder(
                ViewGroup parent,
                int viewType) {

            // 保留原版 MD3 遮罩容器
            MaskableFrameLayout maskableFrame =
                    new MaskableFrameLayout(context);

            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(
                            itemWidthPx,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );

            int marginPx = dpToPx(context, ITEM_MARGIN_DP);

            params.setMargins(
                    marginPx,
                    0,
                    marginPx,
                    0
            );

            maskableFrame.setLayoutParams(params);

            // 保留原版圆角
            float cornerRadius =
                    dpToPx(context, CORNER_RADIUS_DP);

            ShapeAppearanceModel shape =
                    ShapeAppearanceModel.builder()
                            .setAllCorners(
                                    CornerFamily.ROUNDED,
                                    cornerRadius
                            )
                            .build();

            maskableFrame.setShapeAppearanceModel(shape);

            // 图片控件
            ImageView imageView = new ImageView(context);

            imageView.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            imageView.setLayoutParams(
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );

            // 圆角裁剪
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
                                    cornerRadius
                            );
                        }
                    }
            );

            imageView.setClipToOutline(true);

            maskableFrame.addView(imageView);

            return new ViewHolder(
                    maskableFrame,
                    imageView
            );
        }

        @Override
        public void onBindViewHolder(
                ViewHolder holder,
                int position) {

            ImageItem item = items.get(position);

            String imageUrl = item.url;

            holder.imageView.setTag(imageUrl);

            // ==============================
            // 保留原版遮罩缩放机制
            // ==============================

            holder.maskableFrame.setOnMaskChangedListener(
                    maskRect -> {

                        if (holder.maskableFrame.getWidth() > 0) {

                            float maskProgress =
                                    maskRect.left /
                                    (float) holder.maskableFrame.getWidth();

                            float scale =
                                    1f - (maskProgress * 0.15f);

                            holder.itemView.setScaleX(scale);

                            holder.itemView.setScaleY(scale);
                        }
                    }
            );

            // ==============================
            // 图片缓存
            // ==============================

            Bitmap bitmap = cache.get(imageUrl);

            if (bitmap != null) {

                holder.imageView.setImageBitmap(bitmap);

                holder.imageView.setBackgroundColor(0x00000000);

                return;
            }

            holder.imageView.setImageDrawable(null);

            holder.imageView.setBackgroundColor(0xFFE0E0E0);

            // ==============================
            // 异步加载图片
            // ==============================

            try {

                executor.execute(() -> {

                    Bitmap result = loadImage(imageUrl);

                    if (result == null) {
                        return;
                    }

                    cache.put(imageUrl, result);

                    holder.imageView.post(() -> {

                        Object tag = holder.imageView.getTag();

                        if (imageUrl.equals(tag)) {

                            holder.imageView.setImageBitmap(result);

                            holder.imageView.setBackgroundColor(
                                    0x00000000
                            );
                        }
                    });
                });

            } catch (Exception ignored) {
                // 线程池已关闭
            }
        }

        /**
         * 网络图片加载
         */
        private static Bitmap loadImage(String imageUrl) {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(imageUrl);

                connection = (HttpURLConnection)
                        url.openConnection();

                connection.setConnectTimeout(8000);

                connection.setReadTimeout(10000);

                connection.setDoInput(true);

                connection.connect();

                if (connection.getResponseCode()
                        != HttpURLConnection.HTTP_OK) {
                    return null;
                }

                try (InputStream input =
                             connection.getInputStream()) {

                    return BitmapFactory.decodeStream(input);
                }

            } catch (Exception e) {

                e.printStackTrace();

                return null;

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static int dpToPx(
                Context context,
                int dp) {

            return (int) (
                    dp * context.getResources()
                            .getDisplayMetrics().density
                            + 0.5f
            );
        }

        @Override
        public int getItemCount() {

            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            final MaskableFrameLayout maskableFrame;

            final ImageView imageView;

            ViewHolder(
                    MaskableFrameLayout frame,
                    ImageView imageView) {

                super(frame);

                this.maskableFrame = frame;

                this.imageView = imageView;
            }
        }
    }
}
