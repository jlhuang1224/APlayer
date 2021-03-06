package remix.myplayer.ui.widget;

import android.content.Context;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import remix.myplayer.App;
import remix.myplayer.util.DensityUtil;
import remix.myplayer.util.LogUtil;

public class WidthFitSquareCardView extends CardView {

    public WidthFitSquareCardView(Context context) {
        super(context);
    }

    public WidthFitSquareCardView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public WidthFitSquareCardView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }

    private static final int THRESHOLD = DensityUtil.dip2px(App.getContext(),40);
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        final int sizeMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(widthSize,heightSize), MeasureSpec.EXACTLY);
        super.onMeasure(sizeMeasureSpec, sizeMeasureSpec);
        LogUtil.d("WidthFitSquareCardView","ratio: " + heightSize * 1f / widthSize);
        //根据高宽比调整布局
        if(heightSize * 1f / widthSize > 1.2f){
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) getLayoutParams();
            lp.addRule(RelativeLayout.CENTER_VERTICAL);
            lp.topMargin = 0;
            lp.bottomMargin = 0;
        }
    }
}
