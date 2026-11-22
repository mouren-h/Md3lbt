
package mr.x.md3lb;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.WeakReference;

/**
 * MD3 JSON 轮播图库
 *
 * 支持：
 * 1. JSON 数据加载图片
 * 2. 网络图片异步加载
 * 3. 图片缓存
 * 4. MD3 边缘遮罩
 * 5. 圆角卡片
 * 6. 自动轮播
 */
public class Md3lbt {

    private final Activity activity;

    private String jsonData;

    private RecyclerView recyclerView;

    private CarouselLayoutManager layoutManager;

    private CarouselSnapHelper snapHelper;

    private CarouselAdapter adapter;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private Runnable autoPlayRunnable;

    private int currentPosition = 0;

    private boolean initialized = false;

    private static final int AUTO_PLAY_TIME = 3000;

    private static final int ITEM_WIDTH_DP = 200;

    private static final int CORNER_RADIUS_DP = 16;

    private static final int ITEM_MARGIN_DP = 8;

    /**
     * 构造方法
     */
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
     * 设置 MD3 轮播样式并启动
     */
    public Md3lbt setStyle(
            CarouselLayoutManager layoutManager) {

        this.layoutManager = layoutManager;

        initialize();

        return this;
    }

    /**
     * 初始化轮播图
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

        adapter = new CarouselAdapter(imageUrls);

        recyclerView.setAdapter(adapter);

        currentPosition = 0;
        initialized = true;

        setupScrollListener();

        startAutoPlay();
    }

    /**
     * 解析 JSON 数据
     *
     * 支持：
     * ["url1", "url2"]
     *
     * {"images":["url1","url2"]}
     *
     * {"images":[{"url":"url1"}]}
     *
     * {"data":[{"image":"url1"}]}
     */
    private List<String> parseJson(String json) {

        List<String> result = new ArrayList<>();

        try {

            JSONArray array;

            String trimmed = json.trim();

            if (trimmed.startsWith("[")) {

                array = new JSONArray(trimmed);

            } else {

                JSONObject object = new JSONObject(trimmed);

                if (object.has("images")) {
                    array = object.getJSONArray("images");

                } else if (object.has("data")) {
                    array = object.getJSONArray("data");

                } else if (object.has("list")) {
                    array = object.getJSONArray("list");

                } else {
                    throw new IllegalArgumentException(
                            "JSON 对象中没有 images、data 或 list 数组"
                    );
                }
            }

            for (int i = 0; i < array.length(); i++) {

                Object item = array.get(i);

                String imageUrl = null;

                if (item instanceof String) {

                    imageUrl = (String) item;

                } else if (item instanceof JSONObject) {

                    JSONObject imageObject = (JSONObject) item;

                    imageUrl = imageObject.optString("url", null);

                    if (imageUrl == null ||
                            imageUrl.isEmpty()) {
                        imageUrl = imageObject.optString("image", null);
                    }

                    if (imageUrl == null ||
                            imageUrl.isEmpty()) {
                        imageUrl = imageObject.optString("imageUrl", null);
                    }

                    if (imageUrl == null ||
                            imageUrl.isEmpty()) {
                        imageUrl = imageObject.optString("src", null);
                    }
                }

                if (imageUrl != null &&
                        !imageUrl.trim().isEmpty()) {

                    result.add(imageUrl.trim());
                }
            }

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "JSON 图片数据解析失败：" + e.getMessage(), e
            );
        }

        return result;
    }

    /**
     * 设置滑动监听
     */
    private void setupScrollListener() {

        recyclerView.addOnScrollListener(
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
        });
    }

    /**
     * 获取当前居中的卡片位置
     */
    private void updateCurrentPosition() {

        if (recyclerView == null ||
                snapHelper == null ||
                layoutManager == null) {
            return;
        }

        View snapView = snapHelper.findSnapView(layoutManager);

        if (snapView != null) {

            int position =
                    recyclerView.getChildAdapterPosition(snapView);

            if (position != RecyclerView.NO_POSITION) {
                currentPosition = position;
            }
        }
    }

    /**
     * 启动自动播放
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

                if (recyclerView == null ||
                        adapter == null ||
                        !initialized) {
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
     * 停止轮播并释放引用
     * 建议在 Activity.onDestroy() 中调用
     */
    public void stop() {

        stopAutoPlay();

        initialized = false;
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
        return adapter == null ? 0 : adapter.getItemCount();
    }

    /**
     * RecyclerView 适配器
     */
    private class CarouselAdapter
            extends RecyclerView.Adapter<CarouselAdapter.ViewHolder> {

        private final List<String> imageUrls;

        private final Map<String, Bitmap> imageCache =
                new ConcurrentHashMap<>();

        CarouselAdapter(List<String> imageUrls) {
            this.imageUrls = imageUrls;
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

            holder.maskableFrame.setOnMaskChangedListener(
                    maskRect -> {

                if (holder.maskableFrame.getWidth() > 0) {

                    float progress =
                            maskRect.left /
                            (float) holder.maskableFrame.getWidth();

                    float scale =
                            1f - (progress * 0.15f);

                    holder.itemView.setScaleX(scale);
                    holder.itemView.setScaleY(scale);
                }
            });

            Bitmap cachedBitmap = imageCache.get(imageUrl);

            if (cachedBitmap != null) {

                holder.imageView.setImageBitmap(cachedBitmap);

                holder.imageView.setBackgroundColor(
                        0x00000000
                );

            } else {

                holder.imageView.setImageDrawable(null);

                holder.imageView.setBackgroundColor(
                        0xFFE0E0E0
                );

                new LoadImageTask(
                        holder.imageView,
                        imageUrl,
                        imageCache
                ).executeOnExecutor(
                        AsyncTask.THREAD_POOL_EXECUTOR
                );
            }
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

        class ViewHolder extends RecyclerView.ViewHolder {

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
     * 网络图片异步加载
     */
    private static class LoadImageTask
            extends AsyncTask<String, Void, Bitmap> {

        private final WeakReference<ImageView> imageReference;

        private final String imageUrl;

        private final Map<String, Bitmap> cache;

        LoadImageTask(
                ImageView imageView,
                String imageUrl,
                Map<String, Bitmap> cache) {

            this.imageReference =
                    new WeakReference<>(imageView);

            this.imageUrl = imageUrl;

            this.cache = cache;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(urls[0]);

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");

                connection.setConnectTimeout(10000);

                connection.setReadTimeout(10000);

                connection.setDoInput(true);

                connection.connect();

                if (connection.getResponseCode() !=
                        HttpURLConnection.HTTP_OK) {
                    return null;
                }

                try (InputStream input =
                             connection.getInputStream()) {

                    Bitmap bitmap =
                            BitmapFactory.decodeStream(input);

                    if (bitmap != null) {
                        cache.put(imageUrl, bitmap);
                    }

                    return bitmap;
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

        @Override
        protected void onPostExecute(Bitmap bitmap) {

            ImageView imageView =
                    imageReference.get();

            if (imageView == null ||
                    bitmap == null) {
                return;
            }

            Object tag = imageView.getTag();

            if (imageUrl.equals(tag)) {

                imageView.setImageBitmap(bitmap);

                imageView.setBackgroundColor(
                        0x00000000
                );
            }
        }
    }
}
