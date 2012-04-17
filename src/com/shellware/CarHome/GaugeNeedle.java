package com.shellware.CarHome;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;

public class GaugeNeedle extends ImageView {

	private float lastValue; 
	
	private int minValue = 180;
	private int maxValue = 360;
	private int minDegrees = 0;
	private int maxDegrees = 360;
	private int offsetCenterInDegrees = 0;
	private float pivotPoint = .5f;

	public GaugeNeedle(Context context) {
		super(context);
	}
	public GaugeNeedle(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	public GaugeNeedle(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setValue(final float value) {
		
		final float scale = (float) (maxDegrees - minDegrees) / (maxValue - minValue) ;
		final float newValue = (value - minValue) * scale - ((maxDegrees - minDegrees) / 2) + offsetCenterInDegrees;
		
		// filter out values that will distort our gauge display
		if (newValue + 45 > maxDegrees) return;
		if (newValue - 45 < minDegrees) return;
		
		RotateAnimation rotateAnimation = new RotateAnimation(lastValue, newValue, 
				  Animation.RELATIVE_TO_SELF, 0.5f, 
				  Animation.RELATIVE_TO_SELF, pivotPoint);

		rotateAnimation.setInterpolator(new LinearInterpolator());
		rotateAnimation.setDuration(500);
		rotateAnimation.setFillAfter(true);	
	
		startAnimation(rotateAnimation);
		
		lastValue = newValue;
		Log.d("Tester", String.valueOf(value) + " - " + String.valueOf(newValue) + " - " + String.valueOf(scale));
	}
	


	public int getMinValue() {
		return minValue;
	}
	public void setMinValue(int minValue) {
		this.minValue = minValue;
	}
	public int getMaxValue() {
		return maxValue;
	}
	public void setMaxValue(int maxValue) {
		this.maxValue = maxValue;
	}
	public int getMinDegrees() {
		return minDegrees;
	}
	public void setMinDegrees(int minDegrees) {
		this.minDegrees = minDegrees;
	}
	public int getMaxDegrees() {
		return maxDegrees;
	}
	public void setMaxDegrees(int maxDegrees) {
		this.maxDegrees = maxDegrees;
	}
	public float getPivotPoint() {
		return pivotPoint;
	}
	public void setPivotPoint(float pivotPoint) {
		this.pivotPoint = pivotPoint;
	}
	public int getOffsetCenterInDegrees() {
		return offsetCenterInDegrees;
	}
	public void setOffsetCenterInDegrees(int offsetCenterInDegrees) {
		this.offsetCenterInDegrees = offsetCenterInDegrees;
	}

}
