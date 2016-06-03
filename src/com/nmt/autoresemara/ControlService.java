package com.nmt.autoresemara;

import java.io.DataOutputStream;
import java.io.FileInputStream;

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
import android.os.IBinder;
import android.util.Log;
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

	private String fileName = "screen_shot.png";;

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
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				0,
				80,
				WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT
		);
		wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
		view = inflater.inflate(R.layout.control_service, null);

		Button btnStartStop = (Button) view.findViewById(R.id.start_stop);
		btnStartStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isStart) {
					stopSelf();
					return;
				}
				screenCap();
				imageMatch();
				
				isStart = true;
				((Button) v).setText("�X�g�b�v");
			}
		});

		wm.addView(view, params);
		isStart = false;
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		wm.removeView(view);
		wm = null;
		view = null;
		process = null;

		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
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
		FileInputStream fis=null;
		try {
			fis = openFileInput(fileName);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		Bitmap bitmap1 = BitmapFactory.decodeStream(fis);
		Bitmap bitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.screen_shot_camera, opt);
		
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
		String x = String.valueOf(mmlr.maxLoc.x + (tmpl.rows() / 2));
		String y = String.valueOf(mmlr.maxLoc.y + (tmpl.cols() / 2));

		TextView touchPostion = (TextView) view.findViewById(R.id.touchPostion);
		String postion = x + " : " + y;
		touchPostion.setText(postion);

		// �}�b�`�����摜�̈ʒu���^�b�v
		String command = String.format("adb shell input tap %s %s", x, y);
		try {
			outputStream.writeBytes(command);
			outputStream.writeBytes("\n");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
