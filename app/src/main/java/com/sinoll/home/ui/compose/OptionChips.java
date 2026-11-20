package com.sinoll.home.ui.compose;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.sinoll.home.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 单选条：类型切换与可见性档位共用。选中实心紫，未选中描边，跟搜索页的筛选 chip 同一套外观。
 */
public final class OptionChips {

    public interface OnSelect {
        void onSelect(int index);
    }

    private final List<TextView> chips = new ArrayList<>();
    private final int[] values;
    private int selected;

    /**
     * @param labels 展示文案
     * @param values 每档对应的服务端取值；两者顺序一致
     */
    public OptionChips(LinearLayout row, String[] labels, int[] values,
                       @Nullable OnSelect onSelect) {
        this.values = values;
        float density = row.getContext().getResources().getDisplayMetrics().density;
        for (int i = 0; i < labels.length; i++) {
            TextView chip = new TextView(row.getContext());
            chip.setText(labels[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            int horizontal = Math.round(16 * density);
            int vertical = Math.round(8 * density);
            chip.setPadding(horizontal, vertical, horizontal, vertical);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = Math.round(8 * density);
            chip.setLayoutParams(params);
            row.addView(chip);
            final int index = i;
            chip.setOnClickListener(view -> {
                select(index);
                if (onSelect != null) {
                    onSelect.onSelect(index);
                }
            });
            chips.add(chip);
        }
        paint();
    }

    public void select(int index) {
        selected = index;
        paint();
    }

    /** 回填服务端取值时找到对应档位，找不到保持现状。 */
    public void selectValue(int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                select(i);
                return;
            }
        }
    }

    public int value() {
        return values[selected];
    }

    private void paint() {
        for (int i = 0; i < chips.size(); i++) {
            TextView chip = chips.get(i);
            boolean on = i == selected;
            chip.setBackgroundResource(on
                    ? R.drawable.bg_chip_filter_on : R.drawable.bg_chip_filter);
            chip.setTextColor(ContextCompat.getColor(chip.getContext(),
                    on ? R.color.brand_on_primary : R.color.ink_muted));
        }
    }
}
