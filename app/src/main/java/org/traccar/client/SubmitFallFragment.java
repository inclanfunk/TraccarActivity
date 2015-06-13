package org.traccar.client;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class SubmitFallFragment extends DialogFragment {

	private TextView mSecondsBeforeReport;
	private boolean reportFall = true;
	private Timer mTimer = new Timer("Fall Detection Timer");
	private TraccarActivity mTraccerActivity ;
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mTraccerActivity = (TraccarActivity) activity;
	}
	public static boolean fired = false;
	private TimerTask mFallDetectionReport = new DetectionVerificationTask();

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
				getActivity());
		LayoutInflater inflator = (LayoutInflater) getActivity()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflator.inflate(R.layout.fragment_submit_fall, null);
		mSecondsBeforeReport = (TextView) view
				.findViewById(R.id.seconds_remaining);
		alertDialogBuilder.setView(view);
		alertDialogBuilder.setTitle("Fall Detected!");
		alertDialogBuilder.setPositiveButton("Submit Fall",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {

						mTimer.cancel();
						synchronized (SubmitFallFragment.this) {
							reportFall = false;
							mTraccerActivity.FALL_REPORTING = true;
						}
					}
				});
		alertDialogBuilder.setNegativeButton("False Positive",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						if(mFallDetectionReport != null){
							mFallDetectionReport.cancel();
						}
						synchronized (SubmitFallFragment.this) {
							reportFall = false;
						}
					}
				});
		return alertDialogBuilder.create();
	}

	protected void fireFallDetection() {

	}

	@Override
	public void onStart() {
		super.onStart();
		mSecondsBeforeReport.setText("10");
		mFallDetectionReport = new DetectionVerificationTask();
		mTimer = new Timer();
		mTimer.schedule(mFallDetectionReport, 0);

	}
	@Override
	public void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		if(mFallDetectionReport != null){
			mFallDetectionReport.cancel();
		}
		mFallDetectionReport = null;
		mTimer = null;
	} 
	@Override
	public void onDetach() {
		super.onDetach();
		mTraccerActivity.warningDialogVisible = false;
	}
	 AtomicInteger secondsRemaing;
	class DetectionVerificationTask extends TimerTask {
		private boolean cancelReporting = false;

		@Override
		public void run() {

			try {
				secondsRemaing = new AtomicInteger(
					Integer.parseInt(mSecondsBeforeReport.getText().toString()));
				do {
					if (secondsRemaing.get() > -1) {
						Thread.currentThread().sleep(1000);
						secondsRemaing.decrementAndGet();
						if (getActivity() != null) {
							getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									mSecondsBeforeReport
											.setText((secondsRemaing.get()) + "");
								}
							});
							
						}
						

					}
				} while (secondsRemaing.get() > 0);
				synchronized (SubmitFallFragment.this) {
					if (reportFall) {
						mTraccerActivity.FALL_REPORTING = true;
					}	
				}
				dismiss();
				fireFallDetection();
				fired = false;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
