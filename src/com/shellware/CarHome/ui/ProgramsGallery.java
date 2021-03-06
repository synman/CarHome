package com.shellware.CarHome.ui;

import com.shellware.CarHome.CarHomeActivity;
import com.shellware.CarHome.R;
import com.shellware.CarHome.R.drawable;
import com.shellware.CarHome.R.styleable;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

public class ProgramsGallery extends Gallery {

	private final static String TAG = "ProgramsGallery";
	
	private Context context;
    private Handler hideGalleryHandler = new Handler();
	

	public ProgramsGallery(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
		
        setAdapter(new ImageAdapter(context));

        setSelection(2, false);
        bringToFront();
	}

	public ProgramsGallery(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;

		setAdapter(new ImageAdapter(context));

        setSelection(2, false);
        bringToFront();
	}

	public ProgramsGallery(Context context) {
		super(context);
		this.context = context;

        setAdapter(new ImageAdapter(context));

        setSelection(2, false);
        bringToFront();
}
	
	public void showGallery() {
		
        hideGalleryHandler.removeCallbacks(hideGalleryInTime);

        setSelection(2, false);
		setVisibility(View.VISIBLE);
        bringToFront();

		setOnItemClickListener(clicker);

		clearAnimation();

		AlphaAnimation alpha = new AlphaAnimation(0f,1f);
		alpha.setFillAfter(true);
		alpha.setDuration(1000);

		setAnimation(alpha);

		hideGalleryHandler.postDelayed(hideGalleryInTime, 10000);
	}
	
	public void destroyGallery() {    
        hideGalleryHandler.removeCallbacks(hideGalleryInTime);
	}
	
	public void hideGallery() {

		setOnItemClickListener(null);
		
        clearAnimation();
        setAlpha(1f);

		AlphaAnimation alpha = new AlphaAnimation(1.0f, 0.0f);
		alpha.setDuration(1000);
		alpha.setFillAfter(true);

		alpha.setAnimationListener(new AnimationListener() {
			public void onAnimationEnd(Animation animation) {
				setVisibility(View.INVISIBLE);
			}
			public void onAnimationRepeat(Animation animation) {
			}

			public void onAnimationStart(Animation animation) {
			}
		});
		
		setAnimation(alpha);	
	}
	
	private Runnable hideGalleryInTime = new Runnable() {

		public void run() {
			
			if (getVisibility() == View.VISIBLE) {
				setOnItemClickListener(null);
				
                clearAnimation();
                setAlpha(1f);

				AlphaAnimation alpha = new AlphaAnimation(1.0f, 0.0f);
				alpha.setDuration(1000);
				alpha.setFillAfter(true);

				alpha.setAnimationListener(new AnimationListener() {
					public void onAnimationEnd(Animation animation) {
						setVisibility(View.INVISIBLE);
//						Log.d(TAG, "gone");
					}
					public void onAnimationRepeat(Animation animation) {
					}

					public void onAnimationStart(Animation animation) {
					}
				});
				
				setAnimation(alpha);
			}
		}
	};
	
	private OnItemClickListener clicker = new OnItemClickListener() {
	
		public void onItemClick(final AdapterView<?> parent, View v, int position, long id) {

            clearAnimation();

			AlphaAnimation alpha = new AlphaAnimation(1.0f, 0.0f);
			alpha.setDuration(0);
			alpha.setFillAfter(true);

			alpha.setAnimationListener(new AnimationListener() {
				public void onAnimationEnd(Animation animation) {
					parent.setVisibility(View.INVISIBLE);
					parent.setOnClickListener(null);
				}
				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationStart(Animation animation) {
				}
			});		
			
			String url;
			Intent i;
			
        	switch (position) {
        		case 0:                    
        				CarHomeActivity.toggleCamera();
            		break;
        		case 1:
        		    Intent email = context.getPackageManager().getLaunchIntentForPackage("com.android.email");
        		    context.startActivity(email);
        		    break;
        		case 2:
        			url = "google.navigation:fd=true";
        			i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));            
        			context.startActivity(i);
        			break;
        		case 3:
        			i = new Intent(Intent.ACTION_MAIN);
        			i.addCategory(Intent.CATEGORY_LAUNCHER);
        			i.setClassName("com.android.mms", "com.android.mms.ui.ConversationList");
        			context.startActivity(i);
        			break;
        		case 4:
    				i = new Intent(Intent.ACTION_MAIN, null);
    				i.addCategory(Intent.CATEGORY_LAUNCHER);

    				final ComponentName cn = new ComponentName(
    						"com.shellware.adaptronic.adaptive.tuner",
    						"com.shellware.adaptronic.adaptive.tuner.MainActivity");

    				i.setComponent(cn);

    				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    				context.startActivity(i);
        			break;
        		case 5:
        			context.startActivity(new Intent(Settings.ACTION_SETTINGS));
        			break;
    			default:
        			break;	
        	}
			
		} 	
	};
	
	public class ImageAdapter extends BaseAdapter {
	    int mGalleryItemBackground;
	    private Context mContext;

	    private Integer[] mImageIds = {
	            R.drawable.camera,			// 0
	            R.drawable.email,			// 1
	            R.drawable.navigate,		// 2
	            R.drawable.sms,				// 3
	            R.drawable.adaptive_tuner,	// 4
	            R.drawable.configuration	// 5
	    };

	    public ImageAdapter(Context c) {
	        mContext = c;
	        TypedArray attr = mContext.obtainStyledAttributes(R.styleable.HelloGallery);
	        mGalleryItemBackground = attr.getResourceId(
	                R.styleable.HelloGallery_android_galleryItemBackground, 0);
	        attr.recycle();
	    }

	    public int getCount() {
	        return mImageIds.length;
	    }

	    public Object getItem(int position) {
	        return position;
	    }

	    public long getItemId(int position) {
	        return position;
	    }

	    public View getView(int position, View convertView, ViewGroup parent) {
	        ImageView imageView = new ImageView(mContext);

	        imageView.setImageResource(mImageIds[position]);
	        imageView.setLayoutParams(new Gallery.LayoutParams(150, 100));
	        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
	        imageView.setBackgroundResource(mGalleryItemBackground);

	        return imageView;
	    }
	}
}
