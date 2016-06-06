package com.nmt.autoresemara;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class ControlService extends Service {
	/* --- 追加 (共有ライブラリをロードする) --- */
	static {
		System.loadLibrary("opencv_java3");
	}

	Process process;
	DataOutputStream outputStream;

	WindowManager wm;
	View view;
	boolean isStart;

	Timer timer;
	Handler hander = new Handler();

	String fileName = "screen_shot.png";
	
	List<Integer> templateList = new ArrayList<Integer>(Arrays.asList(
			R.drawable.yes,
			R.drawable.title_logo_1,
			R.drawable.title_announce_2,
			R.drawable.title_3,
			R.drawable.terms_4
//			R.drawable.screen_shot_camera
	));

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		try {
			process = Runtime.getRuntime().exec("su");
			outputStream = new DataOutputStream(process.getOutputStream());
		} catch (Exception e) {
			e.printStackTrace();
		}

		wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
		LayoutInflater inflater = LayoutInflater.from(this);
		view = inflater.inflate(R.layout.control_service, null);

		WindowManager.LayoutParams params = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT);
		params.gravity = Gravity.TOP | Gravity.RIGHT;

		Button btnStartStop = (Button) view.findViewById(R.id.start_stop);
		btnStartStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isStart) {
					timer.cancel();
					timer = null;

					isStart = false;
					((Button) v).setText("スタート");

				} else {
					ScreenCapTimerTask scTimerTask = new ScreenCapTimerTask();
					timer = new Timer(true);
					timer.schedule(scTimerTask, 0, 3000);

					isStart = true;
					((Button) v).setText("ストップ");
				}
			}
		});

		wm.addView(view, params);
		isStart = false;
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		wm.removeView(view);
		view = null;
		wm = null;
		view = null;
		outputStream = null;
		process = null;

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private void screenCap() {
		// スクリーンキャプチャ
		try {
			String command = "";
			command = String.format("screencap -p %s/%s", getFilesDir().getPath(), fileName);
			outputStream.writeBytes(command);
			outputStream.writeBytes("\n");
			command = String.format("chmod 664 %s/%s", getFilesDir().getPath(), fileName);
			outputStream.writeBytes(command);
			outputStream.writeBytes("\n");
			Thread.sleep(500);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void imageMatch() {
		// Bitmap読み込みオプション
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inScaled = false;
		// 画像をBitmapで取得
		Bitmap bitmap1 = null;
		Bitmap bitmap2 = null;
		// Bitmap取得で失敗したらRetry
		for (int i = 0; i < 10; i++) {
			try {
				FileInputStream fis = openFileInput(fileName);
				bitmap1 = BitmapFactory.decodeStream(fis);
				if (bitmap1 != null) {
					break;
				}
				Log.d("ControlService#imageMatch", "bitmap1 is null. try again.");
				Thread.sleep(500);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		Mat img = new Mat();
		Utils.bitmapToMat(bitmap1, img);

		boolean isMatch = false;
		Mat tmpl = null;
		Core.MinMaxLocResult mmlr = null;
//		bitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.screen_shot_camera, opt);
//		ImageView imgTmpl=(ImageView)view.findViewById(R.id.imgTmpl);
		for (int template : templateList) {
			bitmap2 = BitmapFactory.decodeResource(getResources(), template, opt);
//			imgTmpl.setImageBitmap(bitmap2);
			tmpl = new Mat();
			Utils.bitmapToMat(bitmap2, tmpl);

			// 比較結果を格納するMatを生成
			Mat result = new Mat(img.rows() - tmpl.rows() + 1, img.cols() - tmpl.cols() + 1, CvType.CV_32FC1);

			// テンプレートマッチ実行（TM_CCOEFF_NORMED：相関係数＋正規化）
			Imgproc.matchTemplate(img, tmpl, result, Imgproc.TM_CCOEFF_NORMED);

			if (mmlr!=null) {
				Log.d("ControlService#imageMatch", "matching iamge " + String.valueOf(mmlr.maxVal));
			}
			// 結果から相関係数がしきい値以下を削除（０にする）
			Imgproc.threshold(result, result, 0.8, 1.0, Imgproc.THRESH_TOZERO); // しきい値=0.8

			// マッチした画像の位置
			mmlr = Core.minMaxLoc(result);
			if (mmlr.maxVal != 0) {
				isMatch = true;
				Log.d("ControlService#imageMatch", "matching iamge " + String.valueOf(template));
				break;
			}
			Log.d("ControlService#imageMatch", "no match iamge " + String.valueOf(template));
		}
		// マッチしない場合は終了
		if (!isMatch) {
			return;
		}

		// マッチした部分を四角で囲う
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				(int) mmlr.maxLoc.x,
				(int) mmlr.maxLoc.y,
				WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
				| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
				PixelFormat.TRANSLUCENT);
		params.gravity = Gravity.TOP | Gravity.LEFT;
		final Rectangle rect = new Rectangle(getApplicationContext(), tmpl.cols(), tmpl.rows());
		wm.addView(rect, params);
		// 四角は3秒後に消す
		hander.postDelayed(new Runnable() {
			@Override
			public void run() {
				wm.removeView(rect);
			}
		}, 3000);

		// マッチした画像の位置をタップ
		String x = String.valueOf(mmlr.maxLoc.x + (tmpl.rows() / 2));
		String y = String.valueOf(mmlr.maxLoc.y + (tmpl.cols() / 2));
		String command = String.format("adb shell input tap %s %s", x, y);
		try {
			outputStream.writeBytes(command);
			outputStream.writeBytes("\n");
		} catch (Exception e) {
			e.printStackTrace();
		}
		TextView touchPostion = (TextView) view.findViewById(R.id.touchPostion);
		String postion = x + " : " + y;
		touchPostion.setText(postion);
	}

	class ScreenCapTimerTask extends TimerTask {
		@Override
		public void run() {
			hander.post(new Runnable() {
				@Override
				public void run() {
					screenCap();
					imageMatch();
				}
			});
		}
	}

	class Rectangle extends View {
		int width;
		int height;
		
		public Rectangle(Context context, int w, int h) {
			super(context);
			width = w;
			height = h;
		}

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			Paint paint = new Paint();
			paint.setARGB(255, 255, 30, 30);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(2);
			canvas.drawRect(1, 1, width - 1, height - 1, paint);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			setMeasuredDimension(width, height);
		}

	}
}
