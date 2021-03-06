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

import java.util.ArrayList;

import com.intel.crashreport.bugzilla.ui.common.BugStorage;
import com.intel.crashreport.specific.Build;
import com.intel.crashreport.database.EventDB;
import com.intel.crashreport.specific.EventGenerator;
import com.intel.crashreport.StartServiceActivity;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.SQLException;
import android.preference.PreferenceManager;

public class CrashReport extends Application {

	private Boolean serviceStarted = false;
	private Boolean checkEventsServiceStarted = false;
	private Boolean tryingToConnect = false;
	private Boolean activityBounded = false;
	private Boolean wifiOnly = false;
	private Boolean serviceRelaunched = false;
	private Boolean needToUpload = false;
	private Build myBuild;
	private BugStorage bugzillaStorage;
	public static StartServiceActivity boundedActivity = null;
	private ArrayList<CrashReportRequest> requestList;
	private CrashReportService uploadService = null;

	@Override
	public void onCreate() {
		super.onCreate();
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		bugzillaStorage = new BugStorage(this);
		requestList = new ArrayList<CrashReportRequest>();
		String version = this.getString(R.string.app_version);
		EventGenerator.INSTANCE.setContext(getApplicationContext());
		GeneralEventGenerator.INSTANCE.setContext(getApplicationContext());

		if (!privatePrefs.getVersion().contentEquals(version)) {
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
			Editor editor = sharedPrefs.edit();
			editor.clear();
			editor.commit();
			PreferenceManager.setDefaultValues(this, R.xml.menu, true);
			privatePrefs.setVersion(version);

			resetCrashLogsUploadTypes();
		}
	}

	public boolean isCheckEventsServiceStarted(){
		return checkEventsServiceStarted;
	}

	public void setCheckEventsServiceStarted(Boolean s){
		checkEventsServiceStarted = s;
	}

	public boolean isServiceStarted(){
		return serviceStarted;
	}

	public void setServiceStarted(Boolean s){
		serviceStarted = s;
	}

	public void setUploadService(CrashReportService service) {
		uploadService = service;
	}

	public CrashReportService getUploadService() {
		return uploadService;
	}

	public boolean isTryingToConnect(){
		return tryingToConnect;
	}
	public void setTryingToConnect(Boolean s){
		tryingToConnect = s;
	}

	public boolean isActivityBounded() {
		return activityBounded;
	}

	public void setActivityBounded(Boolean s) {
		activityBounded = s;
	}

	public void setActivity(StartServiceActivity activity) {
		boundedActivity = activity;
	}

	public boolean isWifiOnly() {
		return wifiOnly;
	}
	public void setWifiOnly(Boolean s) {
		wifiOnly = s;
	}

	public void setMyBuild(Build myBuild) {
		this.myBuild = myBuild;
	}
	public Build getMyBuild() {
		return myBuild;
	}

	public String getUserLastName(){
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		return privatePrefs.getUserLastName();
	}

	public String getUserFirstName() {
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		return privatePrefs.getUserFirstName();
	}

	public String getUserEmail() {
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		return privatePrefs.getUserEmail();
	}

	public void setUserFirstName(String firstname){
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		privatePrefs.setUserFirstName(firstname);
	}

	public void setUserLastName(String lastname) {
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		privatePrefs.setUserLastName(lastname);
	}

	public void setUserEmail(String email) {
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		privatePrefs.setUserEmail(email);
	}

	public void resetCrashLogsUploadTypes() {
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		privatePrefs.resetCrashLogsUploadTypes();
	}

	public BugStorage getBugzillaStorage() {
		return bugzillaStorage;
	}

	/**
	 * @brief Checks if the build is an user build or not
	 *
	 * Checks if the build is an user build or not
	 *
	 * @return true is the build is an user build, false else
	 */
	public boolean isUserBuild() {
		ApplicationPreferences prefs = new ApplicationPreferences(this);
		return prefs.isUserBuild();
	}


	/**
	 * @brief Add a request in the list of requests
	 *
	 * Add a request in the list of requests
	 *
	 * @param request: the request to add
	 */
	public synchronized void addRequest(CrashReportRequest request) {
		requestList.add(request);
	}

	/**
	 * @brief Get the number of pending requests
	 *
	 * Get the number of pending requests
	 *
	 * @return number of pending requests
	 */
	public synchronized int getRequestListCount() {
		return requestList.size();
	}

	/**
	 * @brief Clear the list of requests
	 *
	 * Clear the list of requests
	 */
	public synchronized void emptyList() {
		requestList.clear();
	}

	public void setServiceRelaunched(Boolean relaunched) {
		serviceRelaunched = relaunched;
	}

	public boolean isServiceRelaunched() {
		return serviceRelaunched;
	}

	public String getTokenGCM(){
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		if(privatePrefs.isGcmEnable())
			return privatePrefs.getGcmToken();
		else return "";
	}

	public boolean isGcmEnabled() {
		ApplicationPreferences privatePrefs = new ApplicationPreferences(this);
		return privatePrefs.isGcmEnable();
	}

	/**
	 * Set the value of needToUpload
	 * @param ntou value of needToUpload
	 */
	public synchronized void setNeedToUpload(Boolean ntou) {
		needToUpload = ntou;
	}

	/**
	 * Get the value of needToUpload
	 * @return
	 */
	public boolean getNeedToUpload() {
		return needToUpload;
	}

}
