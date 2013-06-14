/* Phone Doctor (CLOTA)
 *
 * Copyright (C) Intel 2012
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Jeremy Rocher <jeremyx.rocher@intel.com>
 */

package com.intel.crashreport;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;

import android.os.SystemProperties;
import com.intel.crashtoolserver.bean.Device;

public class GeneralEvent {

	protected final static SimpleDateFormat EVENT_DF = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss");
	protected final static SimpleDateFormat EVENT_DF_OLD = new SimpleDateFormat("yy-MM-dd-HH-mm-ss");

	protected String eventId = "";
	protected String eventName = "";
	protected String type = "";
	protected String data0 = "";
	protected String data1 = "";
	protected String data2 = "";
	protected String data3 = "";
	protected String data4 = "";
	protected String data5 = "";
	protected Date date = null;
	protected String buildId = "";
	protected String deviceId = "";
	protected String imei = "";
	protected String uptime = "";
	protected String crashDir = "";
	protected boolean dataReady = true;
	protected boolean uploaded = false;
	protected boolean logUploaded = false;
	/*Define event validity : not valid if a mandatory attribute is missing */
	private boolean valid = true;


	protected int iRowID;
	protected String origin = "";
	protected String pdStatus = "";

	public GeneralEvent() {}

	public GeneralEvent(int rowid, String eventId, String eventName, String type, String data0,
			String data1, String data2, String data3,
			String data4, String data5, String date, String buildId,
			String deviceId, String imei, String uptime, String crashDir) {
		this.iRowID = rowid;
		this.eventId = eventId;
		this.eventName = eventName;
		this.type = type;
		this.data0 = data0;
		this.data1 = data1;
		this.data2 = data2;
		this.data3 = data3;
		this.data4 = data4;
		this.data5 = data5;
		this.date = convertDate(date);
		this.buildId = buildId;
		this.deviceId = deviceId;
		this.imei = imei;
		this.uptime = uptime;
		this.crashDir = crashDir;
	}

	public GeneralEvent(int rowid, String eventId, String eventName, String type, String data0,
			String data1, String data2, String data3,
			String data4, String data5, Date date, String buildId,
			String deviceId, String imei, String uptime, String crashDir) {
		this.iRowID = rowid;
		this.eventId = eventId;
		this.eventName = eventName;
		this.type = type;
		this.data0 = data0;
		this.data1 = data1;
		this.data2 = data2;
		this.data3 = data3;
		this.data4 = data4;
		this.data5 = data5;
		this.date = date;
		this.buildId = buildId;
		this.deviceId = deviceId;
		this.imei = imei;
		this.uptime = uptime;
		this.crashDir = crashDir;
	}

	public void readDeviceIdFromSystem() {
		deviceId = getDeviceIdFromSystem();
	}

	@Override
	public String toString() {
		if (eventName.equals("UPTIME"))
			return new String("Event: " + eventId + ":" + eventName + ":" + uptime);
		else
			return new String("Event: " + eventId + ":" + eventName + ":" + type);
	}

	public void readDeviceIdFromFile() {
		deviceId = getDeviceIdFromFile();
	}

	public static String getDeviceIdFromFile() {
		String sResult = "";
		File uuidFile = new File("/logs/" + "uuid.txt");
		try {
			Scanner scan = new Scanner(uuidFile);
			if (scan.hasNext())
				sResult = scan.nextLine();
			scan.close();
		} catch (FileNotFoundException e) {
			Log.w("CrashReportService: deviceId not set");
		}
		return sResult;
	}

	public static String getDeviceIdFromSystem() {
		String sResult = "";
		sResult += android.os.Build.SERIAL;
		return sResult;
	}

	public static String getSSN(){
		return SystemProperties.get("ro.serialno", "");
	}

	public String readImeiFromSystem() {
		String imeiRead = "";
		try {
			imeiRead = SystemProperties.get("persist.radio.device.imei", "");
			if(imeiRead.equals("")) {
				imeiRead = GeneralEventGenerator.INSTANCE.getImei();
			}
		}
		catch (IllegalArgumentException e) {
			Log.w("CrashReportService: IMEI not available");
		}
		return imeiRead;
	}

	public static Date convertDate(String date) {
		Date cDate = null;
		if (date != null) {
			try {
				EVENT_DF.setTimeZone(TimeZone.getTimeZone("GMT"));
				cDate = EVENT_DF.parse(date);
			} catch (ParseException e) {
				try {
					EVENT_DF_OLD.setTimeZone(TimeZone.getTimeZone("GMT"));
					cDate = EVENT_DF_OLD.parse(date);
				} catch (ParseException e1) {
					cDate = new Date();
				}
			}
		} else
			cDate = new Date();
		return cDate;
	}

	public static long convertUptime(String uptime) {
		long cUptime = 0;
		if (uptime != null) {
			String uptimeSplited[] = uptime.split(":");
			if (uptimeSplited.length == 3) {
				long hours = Long.parseLong(uptimeSplited[0]);
				long minutes = Long.parseLong(uptimeSplited[1]);
				long seconds = Long.parseLong(uptimeSplited[2]);
				cUptime = seconds + (60 * minutes) + (3600 * hours);
			}
		}
		return cUptime;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getData0() {
		return data0;
	}

	public void setData0(String data0) {
		this.data0 = data0;
	}

	public String getData1() {
		return data1;
	}

	public void setData1(String data1) {
		this.data1 = data1;
	}

	public String getData2() {
		return data2;
	}

	public void setData2(String data2) {
		this.data2 = data2;
	}

	public String getData3() {
		return data3;
	}

	public void setData3(String data) {
		this.data3 = data;
	}

	public String getData4() {
		return data4;
	}

	public void setData4(String data) {
		this.data4 = data;
	}

	public String getData5() {
		return data5;
	}

	public void setData5(String data) {
		this.data5 = data;
	}

	public String getDateAsString() {
		EVENT_DF.setTimeZone(TimeZone.getTimeZone("GMT"));
		return EVENT_DF.format(date);
	}

	public void setDate(String date) {
		this.date = convertDate(date);
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getBuildId() {
		return buildId;
	}

	public void setBuildId(String buildId) {
		this.buildId = buildId;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public String getUptime() {
		return uptime;
	}

	public void setUptime(String uptime) {
		this.uptime = uptime;
	}

	public String getImei() {
		return imei;
	}

	public void setImei(String imei) {
		this.imei = imei;
	}

	public void setCrashDir(String crashDir) {
		this.crashDir = crashDir;
	}

	public String getCrashDir() {
		return crashDir;
	}

	public boolean isDataReady() {
		return dataReady;
	}

	public void setDataReady(boolean dataReady) {
		this.dataReady = dataReady;
	}

	public boolean isUploaded() {
		return uploaded;
	}

	public void setUploaded(boolean uploaded) {
		this.uploaded = uploaded;
	}

	public boolean isLogUploaded() {
		return logUploaded;
	}

	public void setLogUploaded(boolean logUploaded) {
		this.logUploaded = logUploaded;
	}

	public void setOrigin(String mOrigin) {
		origin = mOrigin;
	}

	public String getOrigin() {
		return origin;
	}

	public int getiRowID() {
		return iRowID;
	}

	public void setiRowID(int iRowID) {
		this.iRowID = iRowID;
	}
	public String getPdStatus() {
		return pdStatus;
	}

	public void setPdStatus(String pdStatus) {
		this.pdStatus = pdStatus;
	}

	public void setValid(boolean validity) {
		this.valid = validity;
	}

	public boolean isValid() {
		return this.valid;
	}
}