package com.nmt.autoresemara;

import java.io.DataOutputStream;

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
import android.graphics.PixelFormat;
import android.os.Environment;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
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

	private String fileName = "screen_shot.png";
	private String filePath = Environment.getExternalStorageDirectory().getPath() + "/";

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		try {
			process = Runtime.getRuntime().exec("su");
			outputStream = new DataOutputStream(process.getOutputStream());
		} catch (Exception e) {
			e.printStackTrace();
		}

		LayoutInflater inflater = LayoutInflater.from(this);
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT, 0, 80, WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
		wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
		view = inflater.inflate(R.layout.control_service, null);

		Button btnStartStop = (Button) view.findViewById(R.id.start_stop);
		btnStartStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				screenCap();
				imageMatch();
			}
		});

		wm.addView(view, params);
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	private void screenCap() {
		// スクリーンキャプチャ
		String command = String.format("screencap -p %s/%s", filePath, fileName);
		try {
			outputStream.writeBytes(command);
			outputStream.writeBytes("\n");
			Thread.sleep(500);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void imageMatch() {
		// 画像をBitmapで取得
		Bitmap bitmap1 = BitmapFactory.decodeFile(filePath + fileName);
		Bitmap bitmap2 = BitmapFactory.decodeFile(filePath + "ss_test_camera.png");

		// Bitmap -> Mat
		Mat img = new Mat();
		Utils.bitmapToMat(bitmap1, img);
		Mat tmpl = new Mat();
		Utils.bitmapToMat(bitmap2, tmpl);

		// 比較結果を格納するMatを生成
		Mat result = new Mat(img.rows() - tmpl.rows() + 1, img.cols() - tmpl.cols() + 1, CvType.CV_32FC1);

		// テンプレートマッチ実行（TM_CCOEFF_NORMED：相関係数＋正規化）
		Imgproc.matchTemplate(img, tmpl, result, Imgproc.TM_CCOEFF_NORMED);

		// 結果から相関係数がしきい値以下を削除（０にする）
		Imgproc.threshold(result, result, 0.8, 1.0, Imgproc.THRESH_TOZERO); // しきい値=0.8

		// マッチした画像の位置
		Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);
		String x = String.valueOf(mmlr.maxLoc.x + (tmpl.rows() / 2));
		String y = String.valueOf(mmlr.maxLoc.y + (tmpl.cols() / 2));

		TextView touchPostion = (TextView) view.findViewById(R.id.touchPostion);
		String postion = x + " : " + y;
		touchPostion.setText(postion);

		// マッチした画像の位置をタップ
		String command = String.format("adb shell input tap %s %s", x, y);
		try {
			outputStream.writeBytes(command);
			outputStream.writeBytes("\n");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
