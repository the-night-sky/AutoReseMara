package com.nmt.autoresemara;

import java.io.DataOutputStream;
import java.io.FileInputStream;
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
import android.widget.TextView;

public class ControlService extends Service {
	/* --- �ǉ� (���L���C�u���������[�h����) --- */
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

	private String fileName = "screen_shot.png";

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
				0,
				0,
				WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT);

		Button btnStartStop = (Button) view.findViewById(R.id.start_stop);
		btnStartStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isStart) {
					timer.cancel();
					timer = null;

					isStart = false;
					((Button) v).setText("�X�^�[�g");

				} else {
					ScreenCapTimerTask scTimerTask = new ScreenCapTimerTask();
					timer = new Timer(true);
					timer.schedule(scTimerTask, 3000);

					isStart = true;
					((Button) v).setText("�X�g�b�v");
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
		// �X�N���[���L���v�`��
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
		// Bitmap�ǂݍ��݃I�v�V����
		BitmapFactory.Options opt = new BitmapFactory.Options();
		opt.inScaled = false;
		// �摜��Bitmap�Ŏ擾
		Bitmap bitmap1 = null;
		Bitmap bitmap2 = null;
		// Bitmap�擾�Ŏ��s������Retry
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
		bitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.screen_shot_camera, opt);

		// Bitmap -> Mat
		Mat img = new Mat();
		Utils.bitmapToMat(bitmap1, img);
		Mat tmpl = new Mat();
		Utils.bitmapToMat(bitmap2, tmpl);

		// ��r���ʂ��i�[����Mat�𐶐�
		Mat result = new Mat(img.rows() - tmpl.rows() + 1, img.cols() - tmpl.cols() + 1, CvType.CV_32FC1);

		// �e���v���[�g�}�b�`���s�iTM_CCOEFF_NORMED�F���֌W���{���K���j
		Imgproc.matchTemplate(img, tmpl, result, Imgproc.TM_CCOEFF_NORMED);

		// ���ʂ��瑊�֌W�����������l�ȉ����폜�i�O�ɂ���j
		Imgproc.threshold(result, result, 0.8, 1.0, Imgproc.THRESH_TOZERO); // �������l=0.8

		// �}�b�`�����摜�̈ʒu
		Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);
		if (mmlr.maxVal == 0) {
			Log.d("ControlService#imageMatch", "no match image.");
			return;
		}

		// �}�b�`�����������l�p�ň͂�
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
		// �l�p��3�b��ɏ���
		hander.postDelayed(new Runnable() {
			@Override
			public void run() {
				wm.removeView(rect);
			}
		}, 3000);

		// �}�b�`�����摜�̈ʒu���^�b�v
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
