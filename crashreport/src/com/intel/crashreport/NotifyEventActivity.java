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

package com.intel.crashreport;

import android.app.Activity;
import android.database.SQLException;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.database.Cursor;
import java.util.*;

import com.intel.crashreport.specific.Event;
import com.intel.crashreport.database.EventDB;


public class NotifyEventActivity extends Activity {

	private ApplicationPreferences appPrefs;
	private TextView crashText;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.critical_events);
		appPrefs = new ApplicationPreferences(getApplicationContext());
		TextView criticalText = (TextView) findViewById(R.id.criticalEventsView);
		crashText = (TextView) findViewById(R.id.crashEventsView);
		if(crashText != null) {
			crashText.setVisibility(View.GONE);
		}
		if(criticalText != null) {
			criticalText.setVisibility(View.GONE);
		}

	}

	public void printInfo(boolean critical){
		EventDB db = new EventDB(getApplicationContext());
		Event event;
		Cursor cursor;
		TextView viewText;
		HashMap<String,Integer> infos = new HashMap<String,Integer>();

		try {
			db.open();
			cursor = db.fetchNotNotifiedEvents(critical);
			if (cursor != null) {
				while (!cursor.isAfterLast()) {
					event = new Event(db.fillEventFromCursor(cursor));
					String eventType = event.getType();
					if (eventType != null && infos.containsKey(eventType)) {
						Integer resultHash = infos.get(eventType);
						if (resultHash != null){
							int value = resultHash.intValue();
							infos.put(eventType, ++value);
						}else{
							infos.put(eventType, 1);
						}
					} else {
						infos.put(event.getType(), 1);
					}
					cursor.moveToNext();
				}
				cursor.close();
			}
		} catch (SQLException e) {
			Log.w("Service: db Exception");
		}
		db.close();
		if(critical)
			viewText = (TextView) findViewById(R.id.criticalEventsView);
		else
			viewText = (TextView) findViewById(R.id.crashEventsView);

		if(viewText != null) {
			if(infos.keySet().size() > 0)
				viewText.setVisibility(View.VISIBLE);

			viewText.setText("");
			for ( String type : infos.keySet()){
				viewText.append(infos.get(type)+" "+type+" occured\n");
			}
		}
	}

	public void notifyEvents(boolean critical){
		EventDB db = new EventDB(getApplicationContext());
		Event event;
		Cursor cursor;
		try {
			db.open();
			cursor = db.fetchNotNotifiedEvents(critical);
			if (cursor != null) {
				while (!cursor.isAfterLast()) {
					event = new Event(db.fillEventFromCursor(cursor));
					db.updateEventToNotified(event.getEventId());
					cursor.moveToNext();
				}
				cursor.close();
			}
		} catch (SQLException e) {
			Log.w("Service: db Exception");
		}
		db.close();
	}

	protected void onResume() {
		super.onResume();
		printInfo(true);
		if(appPrefs.isNotificationForAllCrash())
			printInfo(false);
		else
			crashText.setVisibility(View.GONE);
	}

	public void onBackPressed() {
		super.onBackPressed();
		notifyEvents(true);
		NotificationMgr nMgr = new NotificationMgr(getApplicationContext());
		nMgr.cancelNotifCriticalEvent();
		if(appPrefs.isNotificationForAllCrash()) {
			notifyEvents(false);
			nMgr.cancelNotifNoCriticalEvent();
		}
	}

}
