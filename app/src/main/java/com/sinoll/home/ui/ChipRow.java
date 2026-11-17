package com.sinoll.home.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * 单行标签条。按可用宽度依次摆放，放不下的直接隐藏，因此既不会换行也不会出现半个字。
 */
public class ChipRow extends LinearLayout {

    public ChipRow(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
    }

    public void setItems(@Nullable List<String> values, @DrawableRes int background,
                         @ColorRes int textColor) {
        removeAllViews();
        if (values == null || values.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        for (String value : values) {
            addView(create(value, background, textColor));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && available > 0) {
            int used = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View chip = getChildAt(i);
                chip.measure(
                        MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                LayoutParams params = (LayoutParams) chip.getLayoutParams();
                int width = chip.getMeasuredWidth() + params.leftMargin + params.rightMargin;
                boolean fits = used + width <= available;
                chip.setVisibility(fits ? VISIBLE : GONE);
                if (fits) {
                    used += width;
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private TextView create(String value, int background, int textColor) {
        TextView chip = new TextView(getContext());
        chip.setText(value);
        chip.setTextSize(12);
        chip.setTextColor(ContextCompat.getColor(getContext(), textColor));
        chip.setBackgroundResource(background);
        chip.setMaxEms(7);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        int horizontal = dp(8);
        int vertical = dp(3);
        chip.setPadding(horizontal, vertical, horizontal, vertical);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        chip.setLayoutParams(params);
        return chip;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
