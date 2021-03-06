/* Copyright (C) 2019 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.crashreport.specific;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.intel.crashreport.CustomizableEventData;
import com.intel.crashreport.GeneralEventGenerator;
import com.intel.crashreport.R;
import com.intel.phonedoctor.Constants;

public class UploadAplogActivity extends Activity{
	final Context context = this;
	final Activity curActivity = this;

	public static int ALL_LOGS_VALUE = 21;
	public static final String COLLECT_ACTION = "com.intel.phonemonitor.COLLECT_METRICS_ACTION";
	public static final String UPLOAD_ACTION = "com.intel.phonemonitor.UPLOAD_METRICS_ACTION";
	public static final String COLLECT_LIST_EXTRA = "com.intel.phonemonitor.COLLECT_LIST_EXTRA";


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.upload_aplog_main);


		Button buttonUpload = (Button) findViewById(R.id.buttonUpload);
		if(buttonUpload != null) {
			buttonUpload.setOnClickListener(new AplogListener());
		}

		RadioButton rdDef = (RadioButton) findViewById(R.id.radioButtonDefault);
		RadioButton rdAll = (RadioButton) findViewById(R.id.radioButtonAll);

		LogTimeProcessing process = new LogTimeProcessing(Constants.LOGS_DIR);

		long lDefHour = process.getDefaultLogHour();
		long lAllHour = process.getLogHourByNumber(ALL_LOGS_VALUE);

		if (rdDef != null && lDefHour > 1 ){
			rdDef.setText(rdDef.getText() + " ("+ lDefHour + " Hours of log)");
		}

		if (rdAll != null && lAllHour > 1 ){
			rdAll.setText(rdAll.getText() + " ("+ lAllHour + " Hours of log)");
		}

		setTitle("");
	}

	public void manageThermalData(){
		//step 1 : request for thermal metrics collection
		CharSequence[] monitorArray = { "Thermal"};
		Intent iCollect = new Intent(COLLECT_ACTION);
		iCollect.putExtra(COLLECT_LIST_EXTRA, monitorArray);
		sendBroadcastAsUser(iCollect, UserHandle.CURRENT);
		//step2 : create INFO ExtraData
		CustomizableEventData mEvent = EventGenerator.INSTANCE.getEmptyInfoEvent();
		mEvent.setType("EXTRA_REPORT");
		mEvent.setData0("THERMAL");
		GeneralEventGenerator.INSTANCE.generateEvent(mEvent);
		//step 3 : request for upload to phone monitor
		Intent iUpload = new Intent(UPLOAD_ACTION);
		sendBroadcastAsUser(iUpload, UserHandle.CURRENT);
	}

	private class AplogListener implements  View.OnClickListener {
		@Override
		public void onClick(View v) {
			RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radiogroup_upload);


			int checkedRadioButton = R.id.radioButtonDefault;
			if(radioGroup != null) {
				checkedRadioButton = radioGroup.getCheckedRadioButtonId();
			}

			String aplogSelected = "";
			int iNbLog =-1;

			switch (checkedRadioButton) {
			case R.id.radioButtonDefault : aplogSelected = "(with default number of aplog)";
			iNbLog = -1;
			break;
			case R.id.radioButtonAll : aplogSelected = "(with all aplog)";
			iNbLog = ALL_LOGS_VALUE;
			break;
			}

			EditText summary = (EditText)findViewById(R.id.aplog_summary_text);
			String strSummary = "";
			if (summary != null)
				strSummary = summary.getText().toString();

			CheckBox thermalCheck = (CheckBox) findViewById(R.id.checkBoxThermal);
			new UploadAplogTask(iNbLog, context, strSummary).execute();
			AlertDialog alert = new AlertDialog.Builder(context).create();
			String sMessage = "A background request of log upload has been created. \n " + aplogSelected;
			if (null != thermalCheck && thermalCheck.isChecked()){
				manageThermalData();
				sMessage +=  "\n Thermal data report requested." ;
			}
			alert.setMessage(sMessage);
			alert.setButton(DialogInterface.BUTTON_NEUTRAL,"OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					curActivity.finish();
				}
			});
			alert.show();
		}
	}
}
